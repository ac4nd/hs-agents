package com.hypersense.boot.framework.agents.render;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.springframework.stereotype.Component;

import java.io.StringWriter;
import java.util.*;

/**
 * Velocity 模板渲染引擎封装。
 * 模板位置：src/main/resources/templates/slides/
 *
 * 暴露 render(templateName, specJson) → 完整 HTML 字符串。
 */
@Component
public class SlideTemplateEngine {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final VelocityEngine engine;

    public SlideTemplateEngine() {
        Properties props = new Properties();
        props.setProperty(RuntimeConstants.RESOURCE_LOADERS, "classpath");
        props.setProperty("resource.loader.classpath.class", ClasspathResourceLoader.class.getName());
        props.setProperty("input.encoding", "UTF-8");
        props.setProperty("output.encoding", "UTF-8");
        this.engine = new VelocityEngine(props);
        engine.init();
    }

    public String render(String templateName, JsonNode specJson) {
        VelocityContext ctx = buildContext(specJson);
        StringWriter writer = new StringWriter(8192);
        engine.getTemplate("templates/slides/" + templateName + ".vm", "UTF-8")
                .merge(ctx, writer);
        return writer.toString();
    }

    /** 渲染 deck 聚合页：传入所有 slides 文件名列表 + meta */
    public String renderDeckIndex(JsonNode specJson, List<String> slideFileNames) {
        VelocityContext ctx = buildContext(specJson);
        ctx.put("slideFiles", slideFileNames);
        StringWriter writer = new StringWriter(8192);
        engine.getTemplate("templates/slides/deck_index.vm", "UTF-8")
                .merge(ctx, writer);
        return writer.toString();
    }

    private VelocityContext buildContext(JsonNode specJson) {
        VelocityContext ctx = new VelocityContext();
        JsonNode meta = specJson.path("meta");
        ctx.put("title", meta.path("title").asText("Untitled Deck"));
        ctx.put("audience", meta.path("audience").asText(""));
        ctx.put("temperature", meta.path("temperature").asText(""));
        ctx.put("designSystem", jsonNodeToMap(meta.path("designSystem")));
        ctx.put("assets", jsonNodeToMap(specJson.path("assets")));
        ctx.put("slides", jsonNodeToList(specJson.path("slides")));
        ctx.put("meta", jsonNodeToMap(meta));
        return ctx;
    }

    private Map<String, Object> jsonNodeToMap(JsonNode node) {
        if (node == null || node.isMissingNode()) return Map.of();
        return MAPPER.convertValue(node, Map.class);
    }

    private List<Map<String, Object>> jsonNodeToList(JsonNode node) {
        if (node == null || node.isMissingNode() || !node.isArray()) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (JsonNode item : node) {
            result.add(MAPPER.convertValue(item, Map.class));
        }
        return result;
    }
}
