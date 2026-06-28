package com.hypersense.boot.framework.agents.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypersense.boot.framework.agents.profile.*;
import com.hypersense.boot.framework.agents.profile.impl.DesignProfile;
import com.hypersense.boot.framework.agents.render.SlideTemplateEngine;
import com.hypersense.boot.framework.agents.tool.impl.FileRenderTool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.*;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 端到端集成测试：模拟 design-profile 完整链路（不调真实 LLM）。
 * 标 @Tag("integration") — 需要 PostgreSQL + design profile DB seed。
 */
@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
class DesignProfileEndToEndTest {

    @Autowired private CapabilityProfileRegistry registry;
    @Autowired private SlideTemplateEngine engine;

    @TempDir Path tempDir;

    @Test
    void shouldLoadDesignProfileWithFullConfig() {
        CapabilityProfile profile = registry.get("design");
        assertEquals("design", profile.id());
        assertEquals(PlanStrategy.OUTLINE_DEMO, profile.planStrategy());
        assertTrue(profile.allowedTools().contains("file_render"));
        assertTrue(profile.allowedTools().contains("design_asset_fetch"));
        assertTrue(profile.allowedTools().contains("design_direction_explore"));
        assertTrue(profile.allowedTools().contains("file_write_chunk"));
        assertTrue(profile.systemPrompt(ProfileContext.minimal("s1", "test"))
                .contains("反 slop"));
    }

    @Test
    void shouldRenderWorldCupPptAndPassLint() throws Exception {
        String specJson = """
                {
                  "schemaVersion":"1.0","profile":"design",
                  "meta":{
                    "title":"2026 世界杯周报",
                    "audience":"足球爱好者",
                    "templateType":"ppt_weekly_update",
                    "format":"1920x1080",
                    "designSystem":{"primary":"#07c160","accent":"#F97316","font":"Source Serif"}
                  },
                  "assets":[],
                  "slides":[
                    {"id":"cover","role":"hero","layout":"center_stage",
                     "content":{"headline":"2026 世界杯周报","subhead":"第 3 比赛日 · 焦点速览"}},
                    {"id":"fixtures","role":"data","layout":"fixture_grid",
                     "content":{"title":"赛程","matches":[
                       {"home":"BRA","away":"ARG","time":"06-29 20:00"},
                       {"home":"FRA","away":"GER","time":"06-30 21:00"}
                     ]}},
                    {"id":"summary","role":"text","layout":"body",
                     "content":{"title":"小结","body":"本轮共 4 场焦点赛事，巴西阿根廷大战最受关注。"}}
                  ]
                }
                """;
        JsonNode spec = new ObjectMapper().readTree(specJson);

        FileRenderTool renderTool = new FileRenderTool(engine, tempDir.toString());
        Map<String, Object> result = renderTool.render(spec, "wc-test");

        List<String> files = (List<String>) result.get("files");
        assertTrue(files.contains("index.html"));
        assertTrue(files.contains("deck.html"));

        Path deck = tempDir.resolve("wc-test").resolve("deck.html");
        String html = Files.readString(deck);
        assertTrue(html.contains("2026 世界杯周报"));
        assertTrue(html.contains("BRA"));

        CapabilityProfile profile = registry.get("design");
        for (LintRule rule : profile.lintRules()) {
            String err = rule.check(html);
            assertNull(err, "Lint 规则 " + rule.id() + " 不应失败：" + err);
        }
    }
}
