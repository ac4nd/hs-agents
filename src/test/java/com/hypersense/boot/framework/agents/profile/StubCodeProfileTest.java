package com.hypersense.boot.framework.agents.profile;

import com.hypersense.boot.framework.agents.profile.impl.StubCodeProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StubCodeProfileTest {

    private StubCodeProfile create() {
        return new StubCodeProfile(
                "code", "代码",
                "你是资深工程师。任务：{{userInput}}",
                List.of("file_read", "file_write", "sandbox_exec", "package_lookup", "reply_text"),
                PlanStrategy.TDD,
                null,
                HitlPolicy.defaultPolicy());
    }

    @Test
    void planStrategyShouldBeTdd() {
        assertEquals(PlanStrategy.TDD, create().planStrategy());
    }

    @Test
    void allowedToolsShouldContainSandboxExec() {
        assertTrue(create().allowedTools().contains("sandbox_exec"));
    }

    @Test
    void systemPromptShouldRenderTask() {
        StubCodeProfile p = create();
        ProfileContext ctx = ProfileContext.minimal("sess-1", "实现快排");
        assertTrue(p.systemPrompt(ctx).contains("实现快排"));
    }
}
