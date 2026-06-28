package com.hypersense.boot.framework.agents.tool.impl;

import com.hypersense.boot.framework.agents.tool.ToolProvider;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * design_asset_fetch 工具：抓取品牌 logo + 内容真图。
 *
 * <h3>三级兜底（spec §3.7 + §5.4）</h3>
 * <ol>
 *   <li>svgl.dev API（最高质量，官方 logo）</li>
 *   <li>simpleicons.org CDN（次选）</li>
 *   <li>失败 → 返回空字符串 + warning（由 audit_warning 透传给用户，避免幻觉编造 logo URL）</li>
 * </ol>
 *
 * <p>内容真图（images）走 Wikimedia Commons API。全部失败 → 诚实 placeholder + warning「图待补」，
 * 严禁 LLM 自行编造图片 URL。</p>
 *
 * <h3>ToolProvider 适配</h3>
 * 本项目使用自定义 {@link ToolProvider} 接口（非 LangChain4j {@code @Tool} 注解），
 * 与 {@code FileWriteTool} / {@code FileWriteChunkTool} 保持一致：
 * {@code name()} / {@code description()} / {@code specification()} / {@code execute(Map)}。
 * 原 spec 的 {@code fetchAssets(...)} 方法保留为 public，供单测直接调用，并由
 * {@link #execute(Map)} 从 args Map 解包后委托调用。
 *
 * @author Claude
 * @since 2026/6/29
 */
@Slf4j
@Component
public class DesignAssetFetchTool implements ToolProvider {

    private static final String DEFAULT_SVGL_API = "https://svgl.dev/api";
    private static final String DEFAULT_SIMPLEICONS_CDN = "https://cdn.simpleicons.org/";
    private static final String DEFAULT_WIKIMEDIA_API = "https://commons.wikimedia.org/w/api.php";

    private final RestClient httpClient;
    /** 可注入的 API base，生产环境为公网地址，单测通过 MockWebServer 替换以隔离网络 */
    private final String svglApi;
    private final String simpleiconsCdn;
    private final String wikimediaApi;

    public DesignAssetFetchTool() {
        this(RestClient.create(),
                DEFAULT_SVGL_API, DEFAULT_SIMPLEICONS_CDN, DEFAULT_WIKIMEDIA_API);
    }

    /**
     * 单测专用：注入 MockWebServer 的 baseUrl 作为所有上游 API 的 base。
     * <p>生产环境走无参构造函数。</p>
     */
    public DesignAssetFetchTool(RestClient httpClient) {
        this(httpClient, "", "", "");
    }

    private DesignAssetFetchTool(RestClient httpClient,
                                 String svglApi, String simpleiconsCdn, String wikimediaApi) {
        this.httpClient = httpClient;
        this.svglApi = svglApi;
        this.simpleiconsCdn = simpleiconsCdn;
        this.wikimediaApi = wikimediaApi;
    }

    @Override
    public String name() {
        return "design_asset_fetch";
    }

    @Override
    public String description() {
        return "抓取品牌官方 logo 与内容真图。输入 logos 数组（品牌名）+ images 数组（含 query/source 字段）。"
                + "三级兜底：svgl → simpleicons → 占位（空串+warning）。"
                + "返回 data URL 形式可直接嵌入 HTML，失败时返回空串并附 warning，"
                + "严禁自行编造图片 URL。";
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name("design_asset_fetch")
                .description("抓取品牌 logo 与内容真图，三级兜底（svgl → simpleicons → 占位）。"
                        + "失败时返回空串 + warning，禁止编造 URL。")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("logos",
                                dev.langchain4j.model.chat.request.json.JsonArraySchema.builder()
                                        .description("品牌名列表，如 [\"Apple\",\"FIFA\"]")
                                        .items(dev.langchain4j.model.chat.request.json.JsonStringSchema.builder()
                                                .build())
                                        .build())
                        .addProperty("images",
                                dev.langchain4j.model.chat.request.json.JsonArraySchema.builder()
                                        .description("图片需求列表，每项含 query/source 字段")
                                        .items(dev.langchain4j.model.chat.request.json.JsonObjectSchema.builder()
                                                .addStringProperty("query", "图片查询词，如 'Eiffel Tower'")
                                                .addStringProperty("source", "图片来源：wikimedia（默认）")
                                                .build())
                                        .build())
                        .addStringProperty("sessionId", "会话 id（ToolNode 自动注入，仅用于日志）")
                        .build())
                .build();
    }

    /**
     * ToolProvider 入口：从 LLM function-call 的 arguments Map 中解包 logos / images / sessionId，
     * 委托给 {@link #fetchAssets(List, List, String)}。
     *
     * <p>容错：logos 缺失视为空列表；images 同理；sessionId 缺失回退 "default"。</p>
     */
    @Override
    @SuppressWarnings("unchecked")
    public Object execute(Map<String, Object> params) {
        List<String> logos = null;
        List<Map<String, String>> images = null;
        String sessionId = "default";

        if (params != null) {
            Object logosRaw = params.get("logos");
            if (logosRaw instanceof List<?> list) {
                logos = new ArrayList<>();
                for (Object o : list) {
                    if (o != null) {
                        logos.add(o.toString());
                    }
                }
            }
            Object imagesRaw = params.get("images");
            if (imagesRaw instanceof List<?> list) {
                images = new ArrayList<>();
                for (Object o : list) {
                    if (o instanceof Map<?, ?> m) {
                        Map<String, String> entry = new LinkedHashMap<>();
                        for (Map.Entry<?, ?> e : m.entrySet()) {
                            entry.put(String.valueOf(e.getKey()),
                                    e.getValue() == null ? "" : String.valueOf(e.getValue()));
                        }
                        images.add(entry);
                    }
                }
            }
            Object sid = params.get("sessionId");
            if (sid != null && !sid.toString().isBlank()) {
                sessionId = sid.toString();
            }
        }
        return fetchAssets(logos, images, sessionId);
    }

    /**
     * 抓取品牌 logo + 内容真图，三级兜底。
     * <p>保留为 public 便于单测（MockWebServer）直接调用，绕过 arguments Map 解包。</p>
     *
     * @param logos     品牌名列表，可为 null
     * @param images    图片需求列表，每项含 query/source；可为 null
     * @param sessionId 会话 id，仅用于日志
     * @return {@code logos} Map（品牌→data URL，失败为空串）+ {@code images} 列表 + {@code warnings} 列表
     */
    public Map<String, Object> fetchAssets(
            List<String> logos,
            List<Map<String, String>> images,
            String sessionId) {

        Map<String, String> logoResults = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();

        if (logos != null) {
            for (String brand : logos) {
                String dataUrl = fetchLogo(brand);
                logoResults.put(brand, dataUrl);
                if (dataUrl.isEmpty()) {
                    warnings.add("品牌 logo 取不到：" + brand + "（请用户手动提供）");
                }
            }
        }

        List<Map<String, String>> imageResults = new ArrayList<>();
        if (images != null) {
            for (Map<String, String> req : images) {
                String q = req.getOrDefault("query", "");
                String source = req.getOrDefault("source", "wikimedia");
                String url = fetchImage(q, source);
                Map<String, String> entry = new LinkedHashMap<>();
                entry.put("query", q);
                entry.put("source", source);
                entry.put("url", url);
                imageResults.add(entry);
                if (url.isEmpty()) {
                    warnings.add("图片取不到：" + q + "（请用户手动提供）");
                }
            }
        }

        log.info("design_asset_fetch: sessionId={}, logos={} (hit={} miss={}), images={} warnings={}",
                sessionId,
                logos == null ? 0 : logos.size(),
                logoResults.size() - (int) logoResults.values().stream().filter(String::isEmpty).count(),
                logoResults.values().stream().filter(String::isEmpty).count(),
                imageResults.size(),
                warnings.size());

        return Map.of(
                "logos", logoResults,
                "images", imageResults,
                "warnings", warnings
        );
    }

    private String fetchLogo(String brand) {
        // 1) svgl — 官方 logo 数据源，质量最高
        try {
            String svglJson = httpClient.get()
                    .uri(svglApi + "?query=" + URLEncoder.encode(brand, StandardCharsets.UTF_8))
                    .retrieve()
                    .body(String.class);
            if (svglJson != null && svglJson.contains("\"svg\"")) {
                int idx = svglJson.indexOf("\"svg\":\"");
                if (idx > 0) {
                    int start = idx + 7;
                    int end = svglJson.indexOf("\"}", start);
                    if (end > start) {
                        String svg = svglJson.substring(start, end)
                                .replace("\\u003c", "<")
                                .replace("\\u003e", ">")
                                .replace("\\\"", "\"")
                                .replace("\\/", "/");
                        return toDataUrl(svg, "image/svg+xml");
                    }
                }
            }
        } catch (Exception e) {
            log.debug("svgl fetch failed for {}: {}", brand, e.getMessage());
        }
        // 2) simpleicons — 次选
        try {
            byte[] pngBytes = httpClient.get()
                    .uri(simpleiconsCdn + brand.toLowerCase().replaceAll("[^a-z0-9]", ""))
                    .retrieve()
                    .body(byte[].class);
            if (pngBytes != null && pngBytes.length > 0) {
                return "data:image/png;base64," + Base64.getEncoder().encodeToString(pngBytes);
            }
        } catch (Exception e) {
            log.debug("simpleicons fetch failed for {}: {}", brand, e.getMessage());
        }
        // 3) 失败：返回空串，由上层生成 warning，避免 LLM 编造 logo URL
        return "";
    }

    private String fetchImage(String query, String source) {
        if ("wikimedia".equalsIgnoreCase(source)) {
            try {
                String body = httpClient.get()
                        .uri(wikimediaApi + "?action=query&prop=imageinfo&iiprop=url&format=json&titles=File:"
                                + URLEncoder.encode(query, StandardCharsets.UTF_8))
                        .retrieve()
                        .body(String.class);
                if (body != null && body.contains("\"url\":\"")) {
                    int idx = body.indexOf("\"url\":\"") + 7;
                    int end = body.indexOf("\"", idx);
                    if (end > idx) {
                        return body.substring(idx, end).replace("\\/", "/");
                    }
                }
            } catch (Exception e) {
                log.debug("wikimedia fetch failed: {}", e.getMessage());
            }
        }
        return "";
    }

    private String toDataUrl(String svg, String mime) {
        return "data:" + mime + ";utf8," + URLEncoder.encode(svg, StandardCharsets.UTF_8);
    }
}
