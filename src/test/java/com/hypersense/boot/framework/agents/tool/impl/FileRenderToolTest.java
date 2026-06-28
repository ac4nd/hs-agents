package com.hypersense.boot.framework.agents.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypersense.boot.framework.agents.render.SlideTemplateEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FileRenderTool 单测（spec §3.8 / Plan B Task 15）。
 * <p>
 * 使用 @TempDir + 直连构造函数，绕开沙箱：验证模板渲染、多页拆分、
 * deck 聚合 index.html 生成、模板回退链（templateType → KEYNOTE → REPORT → WEEKLY）。
 * </p>
 */
class FileRenderToolTest {

    private final SlideTemplateEngine engine = new SlideTemplateEngine();
    private final ObjectMapper mapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    /** 构造最小合法 spec JSON（3 页，templateType=KEYNOTE）。 */
    private JsonNode buildSpec(String templateType) throws Exception {
        String json = """
                {
                  "schemaVersion": "1.0",
                  "profile": "design",
                  "meta": {
                    "title": "Q2 Review",
                    "audience": "leadership",
                    "temperature": "confident",
                    "templateType": "%s",
                    "format": "16:9",
                    "designSystem": {"primary": "#0F172A", "accent": "#38BDF8", "font": "Inter"}
                  },
                  "assets": [],
                  "slides": [
                    {"id": "s1", "role": "cover", "layout": "title", "content": {"headline": "Hello"}},
                    {"id": "s2", "role": "body", "layout": "bullets", "content": {"points": ["a", "b"]}},
                    {"id": "s3", "role": "close", "layout": "thanks", "content": {}}
                  ]
                }
                """.formatted(templateType);
        return mapper.readTree(json);
    }

    @Test
    void rendersEachSlideAsFileAndIndex() throws Exception {
        FileRenderTool tool = new FileRenderTool(engine, tempDir.toString());

        Map<String, Object> result = tool.render(buildSpec("ppt_keynote"), "session-1");

        @SuppressWarnings("unchecked")
        Map<String, Object> res = (Map<String, Object>) result;
        assertEquals(Boolean.TRUE, res.get("success"), () -> "msg=" + res.get("message"));
        assertEquals("session-1", res.get("sessionId"));

        // 3 slides → 3 个 slide_*.html
        assertTrue(Files.exists(tempDir.resolve("session-1/slide_1.html")));
        assertTrue(Files.exists(tempDir.resolve("session-1/slide_2.html")));
        assertTrue(Files.exists(tempDir.resolve("session-1/slide_3.html")));

        // deck 聚合页
        Path index = tempDir.resolve("session-1/index.html");
        assertTrue(Files.exists(index), "index.html 必须生成");
        String indexHtml = Files.readString(index);
        assertTrue(indexHtml.contains("slide_1.html"), "index 应链接到 slide_1");
        assertTrue(indexHtml.contains("slide_2.html"), "index 应链接到 slide_2");
        assertTrue(indexHtml.contains("slide_3.html"), "index 应链接到 slide_3");

        @SuppressWarnings("unchecked")
        List<String> files = (List<String>) res.get("files");
        assertNotNull(files);
        assertTrue(files.size() >= 4, "至少应包含 3 slide + index");
    }

    @Test
    void fallsBackToKeynoteTemplateWhenTemplateTypeMissing() throws Exception {
        FileRenderTool tool = new FileRenderTool(engine, tempDir.toString());

        // templateType 留空 → 回退链应命中 KEYNOTE
        JsonNode spec = buildSpec("");
        Map<String, Object> result = tool.render(spec, "session-2");

        @SuppressWarnings("unchecked")
        Map<String, Object> res = (Map<String, Object>) result;
        assertEquals(Boolean.TRUE, res.get("success"), () -> "msg=" + res.get("message"));
        assertTrue(Files.exists(tempDir.resolve("session-2/index.html")));
        assertTrue(Files.exists(tempDir.resolve("session-2/slide_1.html")));
    }
}
