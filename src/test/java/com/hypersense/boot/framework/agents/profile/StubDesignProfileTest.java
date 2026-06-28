package com.hypersense.boot.framework.agents.profile;

import com.hypersense.boot.framework.agents.profile.impl.StubDesignProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StubDesignProfileTest {

    private StubDesignProfile create() {
        return new StubDesignProfile(
                "design", "设计",
                "你是设计专家。用户输入：{{userInput}}",
                List.of("design_asset_fetch", "file_render", "file_write", "reply_text"),
                PlanStrategy.OUTLINE_DEMO,
                null,
                HitlPolicy.defaultPolicy());
    }

    @Test
    void idAndNameShouldMatch() {
        StubDesignProfile p = create();
        assertEquals("design", p.id());
        assertEquals("设计", p.name());
    }

    @Test
    void systemPromptShouldRenderUserInput() {
        StubDesignProfile p = create();
        ProfileContext ctx = ProfileContext.minimal("sess-1", "做一份世界杯 PPT");
        String prompt = p.systemPrompt(ctx);
        assertTrue(prompt.contains("做一份世界杯 PPT"));
    }

    @Test
    void allowedToolsShouldContainFileRender() {
        StubDesignProfile p = create();
        assertTrue(p.allowedTools().contains("file_render"));
    }

    @Test
    void planStrategyShouldBeOutlineDemo() {
        assertEquals(PlanStrategy.OUTLINE_DEMO, create().planStrategy());
    }

    @Test
    void lintRulesShouldBeEmptyInStub() {
        assertTrue(create().lintRules().isEmpty());
    }
}
