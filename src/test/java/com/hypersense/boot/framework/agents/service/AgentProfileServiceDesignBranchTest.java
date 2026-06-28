package com.hypersense.boot.framework.agents.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypersense.boot.framework.agents.mapper.AgentProfileMapper;
import com.hypersense.boot.framework.agents.model.AgentProfileEntity;
import com.hypersense.boot.framework.agents.profile.CapabilityProfile;
import com.hypersense.boot.framework.agents.profile.PlanStrategy;
import com.hypersense.boot.framework.agents.profile.impl.DesignProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentProfileServiceDesignBranchTest {

    private AgentProfileMapper mapper;
    private AgentProfileService service;

    @BeforeEach
    void setUp() {
        mapper = Mockito.mock(AgentProfileMapper.class);
        service = new AgentProfileService(mapper);
    }

    @Test
    void shouldReturnDesignProfileInstanceForDesignId() {
        AgentProfileEntity entity = new AgentProfileEntity();
        entity.setProfileId("design");
        entity.setName("设计模式");
        entity.setSystemPrompt("你是设计专家。{{userInput}}");
        entity.setAllowedTools(toJsonArray(List.of("design_asset_fetch","file_render","file_write","reply_text")));
        entity.setPlanStrategy("OUTLINE_DEMO");
        when(mapper.findEnabledByProfileId("design")).thenReturn(entity);

        CapabilityProfile profile = service.loadProfile("design");

        assertInstanceOf(DesignProfile.class, profile);
        assertEquals(PlanStrategy.OUTLINE_DEMO, profile.planStrategy());
        assertFalse(profile.lintRules().isEmpty(), "DesignProfile 必须含反 slop lint");
    }

    private com.fasterxml.jackson.databind.JsonNode toJsonArray(List<String> list) {
        return new ObjectMapper().valueToTree(list);
    }
}
