package com.hypersense.boot.framework.agents.profile.impl;

import com.hypersense.boot.framework.agents.profile.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DesignProfileTest {

    private DesignProfile create() {
        return (DesignProfile) DesignProfile.withBrandColor(
                "#07c160",
                SlidesSchema.SYSTEM_PROMPT_TEMPLATE,
                List.of("design_asset_fetch", "design_direction_explore",
                        "file_render", "file_write", "reply_text"),
                DesignProfile.defaultOutputFormat(),
                new HitlPolicy(true, List.of("demo", "batch"), 3, 3));
    }

    @Test
    void idAndPlanStrategyShouldMatchDesign() {
        DesignProfile p = create();
        assertEquals("design", p.id());
        assertEquals(PlanStrategy.OUTLINE_DEMO, p.planStrategy());
    }

    @Test
    void systemPromptShouldRenderUserInput() {
        DesignProfile p = create();
        ProfileContext ctx = ProfileContext.minimal("sess-1", "做一份世界杯 PPT");
        String prompt = p.systemPrompt(ctx);
        assertTrue(prompt.contains("做一份世界杯 PPT"));
        assertTrue(prompt.contains("反 slop"));
        assertTrue(prompt.contains("Junior Designer"));
    }

    @Test
    void lintRulesShouldContainFiveRulesWhenBrandColorGiven() {
        DesignProfile p = create();
        assertEquals(5, p.lintRules().size());
        List<String> ids = p.lintRules().stream().map(LintRule::id).toList();
        assertTrue(ids.contains("no_purple_gradient"));
        assertTrue(ids.contains("no_emoji_icon"));
        assertTrue(ids.contains("no_svg_human"));
        assertTrue(ids.contains("no_placeholder"));
        assertTrue(ids.contains("brand_color_drift"));
    }

    @Test
    void lintRulesShouldContainFourRulesWhenNoBrandColor() {
        DesignProfile p = (DesignProfile) DesignProfile.withBrandColor(
                null, "tpl",
                List.of("file_render"), null, HitlPolicy.defaultPolicy());
        assertEquals(4, p.lintRules().size());
    }

    @Test
    void outputFormatShouldContainSlidesSchema() {
        assertNotNull(DesignProfile.defaultOutputFormat().path("properties").path("slides"));
    }
}
