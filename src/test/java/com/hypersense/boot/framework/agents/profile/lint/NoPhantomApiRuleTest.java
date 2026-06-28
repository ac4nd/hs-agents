package com.hypersense.boot.framework.agents.profile.lint;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NoPhantomApiRuleTest {

    private SymbolRegistry registry;
    private NoPhantomApiRule rule;

    @BeforeEach
    void setUp() {
        registry = SymbolRegistry.withCommonBuiltins();
        registry.register("s1", "np.array");
        registry.register("s1", "np.zeros");
        rule = new NoPhantomApiRule(registry, "s1");
    }

    @Test
    void shouldPassWhenAllCallsitesInRegistry() {
        String code = """
                import numpy as np
                def make():
                    a = np.array([1, 2, 3])
                    b = np.zeros(3)
                    print(a, b)
                    return a
                """;
        assertNull(rule.check(code));
    }

    @Test
    void shouldCatchPhantomApiCall() {
        // np.imaginary_function 没在 registry 中
        String code = """
                import numpy as np
                def f():
                    return np.imaginary_function([1,2,3])
                """;
        assertNotNull(rule.check(code));
    }

    @Test
    void shouldPassWhenUsingBuiltinPrintLen() {
        String code = """
                def f(items):
                    for i, v in enumerate(items):
                        print(i, len(v))
                    """;
        assertNull(rule.check(code));
    }

    @Test
    void shouldIgnoreMethodCallsOnLocalVariables() {
        // items.append 中的 items 是变量，append 是 list 方法（注册在 list 类型）
        // 简化策略：仅检查 known 模块前缀（如 np. / tf. / torch. / React. 等）
        String code = """
                def f():
                    items = []
                    items.append(1)
                    return items
                """;
        assertNull(rule.check(code));
    }

    @Test
    void shouldCatchPhantomReactHook() {
        // useState 没注册到 s1
        String code = """
                import React, { useState } from 'react';
                function C() {
                    const [x, setX] = useState(0);
                    return <div>{x}</div>;
                }
                """;
        // 先注册 React.useState
        registry.register("s1", "React.useState");
        // 此时 useState 仍不在 registry（regex 抓的是裸 useState，不是 React.useState）
        // 本规则抓 imported 函数调用
        String err = rule.check(code);
        // useState 已通过 import 导入，但没注册为独立符号 → 应被拦截
        assertNotNull(err);
    }
}
