package com.hypersense.boot.framework.agents.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypersense.boot.framework.agents.mapper.AgentProfileMapper;
import com.hypersense.boot.framework.agents.model.AgentProfileEntity;
import com.hypersense.boot.framework.agents.profile.CapabilityProfile;
import com.hypersense.boot.framework.agents.profile.PlanStrategy;
import com.hypersense.boot.framework.agents.profile.impl.CodeProfile;
import com.hypersense.boot.framework.agents.profile.lint.SymbolRegistry;
import com.hypersense.boot.framework.agents.tool.SandboxExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentProfileServiceCodeBranchTest {

    private AgentProfileMapper mapper;
    private SymbolRegistry symbolRegistry;
    private SandboxExecutor sandbox;
    private AgentProfileService service;

    @BeforeEach
    void setUp() {
        mapper = Mockito.mock(AgentProfileMapper.class);
        symbolRegistry = new SymbolRegistry();
        sandbox = Mockito.mock(SandboxExecutor.class);
        service = new AgentProfileService(mapper, symbolRegistry, sandbox);
    }

    @Test
    void shouldReturnCodeProfileForCodeId() {
        AgentProfileEntity entity = new AgentProfileEntity();
        entity.setProfileId("code");
        entity.setName("代码模式");
        entity.setSystemPrompt("你是工程师。{{userInput}}");
        entity.setAllowedTools(new ObjectMapper().valueToTree(
                List.of("file_read","file_write","sandbox_exec","package_lookup","reply_text")));
        entity.setPlanStrategy("TDD");
        when(mapper.findEnabledByProfileId("code")).thenReturn(entity);

        CapabilityProfile profile = service.loadProfile("code", "s1");

        assertInstanceOf(CodeProfile.class, profile);
        assertEquals(PlanStrategy.TDD, profile.planStrategy());
        assertFalse(profile.lintRules().isEmpty());
        List<String> ids = profile.lintRules().stream()
                .map(r -> r.id()).toList();
        assertTrue(ids.contains("no_phantom_api"));
        assertTrue(ids.contains("comment_language_match"));
    }

    /**
     * P0#3：hints 透传测试。验证 hints 中的 language/sourceFile/testFile 能正常到达
     * CodeProfile 构造路径而不抛异常。Hints=null（缺失）应回退 Python 默认值。
     */
    @Test
    void shouldAcceptHintsForCodeProfile() {
        AgentProfileEntity entity = new AgentProfileEntity();
        entity.setProfileId("code");
        entity.setName("代码模式");
        entity.setSystemPrompt("你是工程师。{{userInput}}");
        entity.setAllowedTools(new ObjectMapper().valueToTree(
                List.of("file_read","file_write","sandbox_exec","package_lookup","reply_text")));
        entity.setPlanStrategy("TDD");
        when(mapper.findEnabledByProfileId("code")).thenReturn(entity);

        // hints 提供非 Python 语言配置，CodeProfile 应正常构造（不抛异常）
        java.util.Map<String, Object> hints = java.util.Map.of(
                "language", "javascript",
                "sourceFile", "src/index.js",
                "testFile", "test/index.test.js");
        CapabilityProfile profile = service.loadProfile("code", "s1", hints);

        assertInstanceOf(CodeProfile.class, profile);
        // 4 条 lint 规则应正常装配（compile_pass / test_pass 会用 JS 命令模板）
        assertEquals(4, profile.lintRules().size());
    }
}
