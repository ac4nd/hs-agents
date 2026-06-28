package com.hypersense.boot.framework.agents.engine;

import com.hypersense.boot.framework.agents.profile.CapabilityProfile;
import com.hypersense.boot.framework.agents.profile.CapabilityProfileRegistry;
import com.hypersense.boot.framework.agents.profile.PlanStrategy;
import com.hypersense.boot.framework.agents.profile.ProfileContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 集成测试：从 DB 加载 → ProfileRegistry 缓存 → Profile 接口 → systemPrompt 渲染。
 * 不调用真实 LLM，仅验证框架装配。
 *
 * <p>需要 dev profile 数据库连接（PostgreSQL + sys_agent_profile 表已建表并播种）。
 * 若 CI 无 DB，可通过 {@code -DexcludedGroups=integration} 排除。</p>
 */
@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
class ProfileGraphIntegrationTest {

    @Autowired
    private CapabilityProfileRegistry registry;

    @Test
    void shouldLoadDesignProfileFromDb() {
        CapabilityProfile profile = registry.get("design");
        assertEquals("design", profile.id());
        assertEquals(PlanStrategy.OUTLINE_DEMO, profile.planStrategy());
        assertTrue(profile.allowedTools().contains("file_render"));
    }

    @Test
    void shouldLoadCodeProfileFromDb() {
        CapabilityProfile profile = registry.get("code");
        assertEquals("code", profile.id());
        assertEquals(PlanStrategy.TDD, profile.planStrategy());
        assertTrue(profile.allowedTools().contains("sandbox_exec"));
    }

    @Test
    void shouldLoadThinkProfileMergedFromResearchAndPlanning() {
        CapabilityProfile profile = registry.get("think");
        assertEquals("think", profile.id());
        assertEquals(PlanStrategy.DIVERGE_THEN_STRUCTURE, profile.planStrategy());
    }

    @Test
    void shouldRenderSystemPromptWithContext() {
        CapabilityProfile profile = registry.get("design");
        ProfileContext ctx = ProfileContext.minimal("sess-1", "做一份世界杯 PPT");
        String prompt = profile.systemPrompt(ctx);
        assertNotNull(prompt);
        assertFalse(prompt.isBlank());
        assertTrue(prompt.contains("做一份世界杯 PPT"));
    }

    @Test
    void shouldCacheProfileBetweenCalls() {
        CapabilityProfile p1 = registry.get("design");
        CapabilityProfile p2 = registry.get("design");
        assertSame(p1, p2);
    }
}
