package com.hypersense.boot.framework.agents.profile.lint;

import com.hypersense.boot.framework.agents.tool.SandboxExecutor;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestPassRuleTest {

    @Test
    void shouldPassWhenTestExitZero() {
        SandboxExecutor sandbox = Mockito.mock(SandboxExecutor.class);
        when(sandbox.exec(anyString(), anyString()))
                .thenReturn(new SandboxExecutor.Result(0, "1 passed", ""));
        TestPassRule rule = new TestPassRule(sandbox, "python", "test_foo.py");

        assertNull(rule.check(""));
    }

    @Test
    void shouldFailWhenTestExitNonZero() {
        SandboxExecutor sandbox = Mockito.mock(SandboxExecutor.class);
        when(sandbox.exec(anyString(), anyString()))
                .thenReturn(new SandboxExecutor.Result(1, "", "1 failed"));
        TestPassRule rule = new TestPassRule(sandbox, "python", "test_foo.py");

        String err = rule.check("");
        assertNotNull(err);
        assertTrue(err.contains("failed"));
    }
}
