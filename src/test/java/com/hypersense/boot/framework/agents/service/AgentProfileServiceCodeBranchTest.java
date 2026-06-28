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
}
