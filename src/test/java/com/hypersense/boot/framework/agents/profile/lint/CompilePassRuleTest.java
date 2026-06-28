package com.hypersense.boot.framework.agents.profile.lint;

import com.hypersense.boot.framework.agents.tool.SandboxExecutor;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CompilePassRuleTest {

    @Test
    void shouldPassWhenCompileExitZero() {
        SandboxExecutor sandbox = Mockito.mock(SandboxExecutor.class);
        when(sandbox.exec(anyString(), anyString()))
                .thenReturn(new SandboxExecutor.Result(0, "", ""));
        CompilePassRule rule = new CompilePassRule(sandbox, "python", "src/foo.py");

        assertNull(rule.check("源码内容不重要，sandbox mock 了"));
    }

    @Test
    void shouldFailWhenCompileExitNonZero() {
        SandboxExecutor sandbox = Mockito.mock(SandboxExecutor.class);
        when(sandbox.exec(anyString(), anyString()))
                .thenReturn(new SandboxExecutor.Result(1, "", "SyntaxError: invalid syntax"));
        CompilePassRule rule = new CompilePassRule(sandbox, "python", "src/foo.py");

        String err = rule.check("code");
        assertNotNull(err);
        assertTrue(err.contains("SyntaxError"));
    }

    @Test
    void shouldSkipForJsLanguage() {
        // JS 不做编译检查（解释型语言）
        SandboxExecutor sandbox = Mockito.mock(SandboxExecutor.class);
        CompilePassRule rule = new CompilePassRule(sandbox, "javascript", "src/foo.js");

        assertNull(rule.check("code"));
        verify(sandbox, never()).exec(anyString(), anyString());
    }
}
