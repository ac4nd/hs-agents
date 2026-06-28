package com.hypersense.boot.framework.agents.profile.impl;

import com.hypersense.boot.framework.agents.profile.*;
import com.hypersense.boot.framework.agents.profile.lint.SymbolRegistry;
import com.hypersense.boot.framework.agents.tool.SandboxExecutor;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CodeProfileTest {

    private CodeProfile create() {
        SandboxExecutor sandbox = Mockito.mock(SandboxExecutor.class);
        SymbolRegistry reg = SymbolRegistry.withCommonBuiltins();
        return (CodeProfile) CodeProfile.withRuntimeContext(
                sandbox, reg, "s1", "python", "src/foo.py", "test/test_foo.py",
                CodeSchema.SYSTEM_PROMPT_TEMPLATE,
                List.of("file_read","file_write","sandbox_exec","package_lookup","reply_text"),
                CodeProfile.defaultOutputFormat(),
                new HitlPolicy(true, List.of("test_hitl", "lint_failed"), 3, 3));
    }

    @Test
    void idAndStrategyShouldMatchCode() {
        CodeProfile p = create();
        assertEquals("code", p.id());
        assertEquals(PlanStrategy.TDD, p.planStrategy());
    }

    @Test
    void systemPromptShouldRenderUserInput() {
        CodeProfile p = create();
        ProfileContext ctx = ProfileContext.minimal("s1", "实现快排");
        String prompt = p.systemPrompt(ctx);
        assertTrue(prompt.contains("实现快排"));
        assertTrue(prompt.contains("SOLID"));
        assertTrue(prompt.contains("TDD"));
        assertTrue(prompt.contains("禁止修改测试"));
    }

    @Test
    void lintRulesShouldContainFourRules() {
        CodeProfile p = create();
        List<String> ids = p.lintRules().stream().map(LintRule::id).toList();
        assertTrue(ids.contains("comment_language_match"));
        assertTrue(ids.contains("no_phantom_api"));
        assertTrue(ids.contains("compile_pass"));
        assertTrue(ids.contains("test_pass"));
        assertEquals(4, ids.size());
    }

    @Test
    void outputFormatShouldContainCodeSchema() {
        assertNotNull(CodeProfile.defaultOutputFormat().path("language"));
    }
}
