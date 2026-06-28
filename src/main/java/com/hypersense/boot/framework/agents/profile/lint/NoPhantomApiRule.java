package com.hypersense.boot.framework.agents.profile.lint;

import com.hypersense.boot.framework.agents.profile.LintRule;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lint：拦截 LLM 幻觉 API（spec §5.5）。
 *
 * 抓取源码中所有「模块前缀.方法」与「imported 函数调用」形式的符号，
 * 校验是否在 SymbolRegistry 中。未注册 → 视为幻觉。
 *
 * 偏差说明（与原始 spec 文本）：
 *   spec 注释中曾写「已知模块前缀（变量本身就是模块对象，不视为幻觉）」会无条件放行
 *   np.X 调用。但 TDD 用例 {@code shouldCatchPhantomApiCall} 要求 np.imaginary_function
 *   被拦截（np 已注册 array/zeros，未注册 imaginary_function）。以测试为准（TDD），
 *   调整逻辑为：
 *     - 大写前缀（如 React / 类名）：强制要求注册
 *     - 小写且在 KNOWN_MODULE_PREFIXES 中（如 np / tf / torch）：仍要求 prefix.method 注册
 *     - 小写且不在 KNOWN_MODULE_PREFIXES 中（如 items / obj）：视为本地变量，跳过
 */
public class NoPhantomApiRule implements LintRule {

    private static final Pattern MODULE_CALL = Pattern.compile(
            "\\b([a-zA-Z_][a-zA-Z0-9_]*)\\.([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\("
    );

    /** 已知模块前缀（小写时用于区分模块对象 vs 本地变量） */
    private static final Set<String> KNOWN_MODULE_PREFIXES = Set.of(
            "np", "pd", "tf", "torch", "plt", "sns", "React", "ReactDOM",
            "Vue", "Angular", "$", "_", "fs", "path", "os", "sys", "math",
            "datetime", "json", "re", "requests"
    );

    /** JS/TS import { a, b as c } from '...' （支持 `import React, { useState } from '...'`） */
    private static final Pattern JS_IMPORT = Pattern.compile(
            "(?s)import\\s+[^;]*?\\{([^}]+)\\}\\s*from\\s+['\"][^'\"]+['\"]"
    );

    private final SymbolRegistry registry;
    private final String sessionId;

    public NoPhantomApiRule(SymbolRegistry registry, String sessionId) {
        this.registry = registry;
        this.sessionId = sessionId;
    }

    @Override
    public String id() {
        return "no_phantom_api";
    }

    @Override
    public String description() {
        return "禁止使用未在 package_lookup 中确认的第三方 API（防 LLM 幻觉）";
    }

    @Override
    public String check(String input) {
        if (input == null || input.isEmpty()) return null;

        Set<String> phantoms = new LinkedHashSet<>();

        // 1. 模块前缀调用
        Matcher m = MODULE_CALL.matcher(input);
        while (m.find()) {
            String prefix = m.group(1);
            String method = m.group(2);
            String full = prefix + "." + method;

            if (registry.contains(sessionId, full)) continue;

            boolean isUpperCasePrefix = Character.isUpperCase(prefix.charAt(0));
            boolean isKnownModule = KNOWN_MODULE_PREFIXES.contains(prefix);

            if (isUpperCasePrefix || isKnownModule) {
                // 模块对象或类名 → 必须注册 prefix.method
                phantoms.add(full);
            }
            // 否则：小写且非已知模块 → 视为本地变量方法调用，跳过
        }

        // 2. 从 import { useState } 抓 imported symbol
        Matcher jm = JS_IMPORT.matcher(input);
        while (jm.find()) {
            String[] syms = jm.group(1).split(",");
            for (String s : syms) {
                String sym = s.trim().split("\\s+as\\s+")[0].trim();
                if (sym.isEmpty()) continue;
                if (registry.contains(sessionId, sym)) continue;
                // 检查源码中是否调用了该 imported symbol（裸调用 sym(）
                if (Pattern.compile("\\b" + Pattern.quote(sym) + "\\s*\\(").matcher(input).find()) {
                    phantoms.add(sym);
                }
            }
        }

        if (!phantoms.isEmpty()) {
            return "检测到幻觉 API 调用：" + phantoms +
                   "。请先调用 package_lookup 确认 API 存在，或改用已确认的等价 API。";
        }
        return null;
    }
}
