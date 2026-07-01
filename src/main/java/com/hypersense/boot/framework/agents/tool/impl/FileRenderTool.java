package com.hypersense.boot.framework.agents.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypersense.boot.framework.agents.profile.SlidesSchema;
import com.hypersense.boot.framework.agents.render.SlideTemplateEngine;
import com.hypersense.boot.framework.agents.tool.ToolProvider;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * file_render 工具 — 把 design-profile 产出的 slides JSON 渲染成完整 HTML deck（spec §3.8）。
 *
 * <h3>职责</h3>
 * <ol>
 *   <li>读取 {@code specJson} 中的 {@code meta.templateType}，选择对应 Velocity 模板</li>
 *   <li>逐页渲染（每页一个 {@code slide_<n>.html}），便于前端按页预览</li>
 *   <li>渲染 deck 聚合页 {@code index.html}（链接所有 slide）</li>
 *   <li>模板缺失时按 KEYNOTE → REPORT → WEEKLY 回退链降级</li>
 * </ol>
 *
 * <h3>落盘位置</h3>
 * <ul>
 *   <li>单测 / spec：直连 {@code new FileRenderTool(engine, path)}，写入 {@code path/<sessionId>/}</li>
 *   <li>生产：构造时未传 path，使用系统属性 {@code app.session.root}（默认 {@code work/sessions}）</li>
 * </ul>
 *
 * <p>spec 测试要求直连路径（{@code @TempDir}），不走沙箱；与 FileWriteChunkTool 不同，
 * 本工具的产物是只读 HTML 资产，无需 SandboxManager 注入。</p>
 *
 * @author Claude
 * @since 2026/6/29
 */
@Slf4j
@Component
public class FileRenderTool implements ToolProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 系统属性 key：会话根目录（与 file_write 系列工具约定一致）。 */
    private static final String SESSION_ROOT_PROP = "app.session.root";
    private static final String DEFAULT_SESSION_ROOT = "work/sessions";

    /** 模板回退链：templateType 缺失或找不到时按序尝试。 */
    private static final List<String> TEMPLATE_FALLBACK = List.of(
            SlidesSchema.TEMPLATE_KEYNOTE,
            SlidesSchema.TEMPLATE_REPORT,
            SlidesSchema.TEMPLATE_WEEKLY
    );

    private final SlideTemplateEngine engine;
    private final String sessionRoot;

    /**
     * 生产构造函数：使用 {@code app.session.root} 系统属性。
     */
    @Autowired
    public FileRenderTool(SlideTemplateEngine engine) {
        this(engine, System.getProperty(SESSION_ROOT_PROP, DEFAULT_SESSION_ROOT));
    }

    /**
     * 全参构造函数：spec 测试用，注入临时目录绕开沙箱。
     */
    public FileRenderTool(SlideTemplateEngine engine, @Nullable String sessionRoot) {
        this.engine = engine;
        this.sessionRoot = (sessionRoot == null || sessionRoot.isBlank())
                ? DEFAULT_SESSION_ROOT : sessionRoot;
    }

    @Override
    public String name() {
        return "file_render";
    }

    @Override
    public String description() {
        return "把 design-profile 输出的 slides JSON spec 渲染成 HTML deck。"
                + "逐页生成 slide_<n>.html 并聚合为 index.html。";
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name("file_render")
                .description("渲染 slides spec 为 HTML deck（每页一个文件 + index 聚合页）。")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("specJson",
                                "design-profile 输出的完整 JSON spec（含 meta / assets / slides）")
                        .addStringProperty("sessionId", "会话 id（决定输出子目录）")
                        .required(List.of("specJson", "sessionId"))
                        .build())
                .build();
    }

    @Override
    public Object execute(Map<String, Object> args) {
        if (args == null) {
            return errorResult(null, "参数为空");
        }
        String sessionId = pickString(args, "sessionId");
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = "default";
            log.debug("file_render: sessionId 缺失，回退到 default");
        }
        Object specRaw = args.get("specJson");
        if (specRaw == null) {
            return errorResult(sessionId, "specJson 缺失");
        }
        JsonNode specJson;
        try {
            if (specRaw instanceof JsonNode node) {
                specJson = node;
            } else if (specRaw instanceof String s && !s.isBlank()) {
                specJson = MAPPER.readTree(s);
            } else {
                specJson = MAPPER.valueToTree(specRaw);
            }
        } catch (Exception e) {
            return errorResult(sessionId, "specJson 解析失败: " + e.getMessage());
        }
        return render(specJson, sessionId);
    }

    /**
     * 主渲染入口：直接接收解析好的 spec，供 ToolProvider.execute 与单测共用。
     *
     * @param specJson  design-profile 输出（含 meta.templateType / slides）
     * @param sessionId 会话 id，决定输出子目录名
     * @return Map：success / sessionId / files / templateUsed / message / error
     */
    public Map<String, Object> render(JsonNode specJson, String sessionId) {
        if (specJson == null) {
            return errorResult(sessionId, "specJson 为空");
        }
        String effectiveSession = (sessionId == null || sessionId.isBlank()) ? "default" : sessionId;

        JsonNode slides = specJson.path("slides");
        if (!slides.isArray() || slides.isEmpty()) {
            return errorResult(effectiveSession, "spec.slides 缺失或为空");
        }

        String templateName = resolveTemplate(specJson);
        Path outDir = Paths.get(sessionRoot, effectiveSession).toAbsolutePath();
        try {
            Files.createDirectories(outDir);
        } catch (IOException e) {
            return errorResult(effectiveSession, "创建输出目录失败: " + e.getMessage());
        }

        List<String> produced = new ArrayList<>();
        List<String> slideFileNames = new ArrayList<>();
        int idx = 1;
        long renderStartMs = System.currentTimeMillis();
        log.info("file_render: 渲染开始 sessionId={}, template={}, slides={}, startTs={}",
                effectiveSession, templateName, slides.size(), renderStartMs);
        try {
            for (JsonNode slide : slides) {
                String fileName = "slide_" + idx + ".html";
                long slideStartMs = System.currentTimeMillis();
                // 单页 spec：复用整体 spec 但替换 slides 为单元素，避免模板遍历整副 deck
                JsonNode singleSpec = wrapSingleSlide(specJson, slide);
                String html = engine.render(templateName, singleSpec);
                Files.writeString(outDir.resolve(fileName), html, StandardCharsets.UTF_8);
                long slideElapsedMs = System.currentTimeMillis() - slideStartMs;
                // 单页日志改 DEBUG：N 页 PPT 会产 N 条 INFO 噪音（10 页 = 10 条），汇总在末尾 INFO 即可
                log.debug("file_render: 单页写入完成 sessionId={}, file={}, bytes={}, elapsedMs={}",
                        effectiveSession, fileName, html.length(), slideElapsedMs);
                slideFileNames.add(fileName);
                produced.add(fileName);
                idx++;
            }

            // deck 聚合页
            long idxStartMs = System.currentTimeMillis();
            String indexHtml = engine.renderDeckIndex(specJson, slideFileNames);
            Files.writeString(outDir.resolve("index.html"), indexHtml, StandardCharsets.UTF_8);
            log.info("file_render: 聚合页写入完成 sessionId={}, file=index.html, bytes={}, elapsedMs={}",
                    effectiveSession, indexHtml.length(), System.currentTimeMillis() - idxStartMs);
            produced.add("index.html");
        } catch (Exception e) {
            log.error("file_render 渲染失败 sessionId={}, template={}", effectiveSession, templateName, e);
            Map<String, Object> partial = errorResult(effectiveSession, "渲染失败: " + e.getMessage());
            partial.put("files", produced);
            partial.put("templateUsed", templateName);
            return partial;
        }

        long totalElapsedMs = System.currentTimeMillis() - renderStartMs;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("sessionId", effectiveSession);
        result.put("templateUsed", templateName);
        result.put("files", produced);
        result.put("outputDir", outDir.toString());
        result.put("elapsedMs", totalElapsedMs);
        result.put("message", "渲染完成：" + slideFileNames.size() + " 页 + index.html");
        log.info("file_render done: sessionId={}, template={}, slides={}, totalElapsedMs={}, avgPerSlideMs={}",
                effectiveSession, templateName, slideFileNames.size(), totalElapsedMs,
                slideFileNames.isEmpty() ? 0 : (totalElapsedMs / slideFileNames.size()));
        return result;
    }

    /**
     * 解析模板名：优先用 meta.templateType，缺失或不可用时按 KEYNOTE → REPORT → WEEKLY 回退。
     * 注：此处不做模板文件存在性探测（Velocity 在 init 时只声明 classpath loader，
     * 文件缺失会在 merge 时抛 ResourceNotFoundException，由调用方兜底）；
     * 仅做命名规范清洗 + 空值回退，保持工具轻量。
     */
    private String resolveTemplate(JsonNode specJson) {
        String declared = specJson.path("meta").path("templateType").asText("").trim();
        if (!declared.isBlank()) {
            return declared;
        }
        return TEMPLATE_FALLBACK.get(0);
    }

    /**
     * 把整副 spec 中 slides 字段替换为单元素数组，便于模板按单页渲染。
     */
    private JsonNode wrapSingleSlide(JsonNode specJson, JsonNode slide) {
        com.fasterxml.jackson.databind.node.ObjectNode clone = MAPPER.createObjectNode();
        clone.setAll((com.fasterxml.jackson.databind.node.ObjectNode) specJson);
        clone.set("slides", MAPPER.createArrayNode().add(slide));
        return clone;
    }

    private Map<String, Object> errorResult(String sessionId, String error) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("sessionId", sessionId);
        result.put("error", error);
        return result;
    }

    private static String pickString(Map<String, Object> params, String... keys) {
        if (params == null) return null;
        for (String key : keys) {
            Object v = params.get(key);
            if (v != null && !v.toString().isBlank()) {
                return v.toString();
            }
        }
        return null;
    }
}
