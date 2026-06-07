package com.hypersense.boot.framework.agents.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypersense.boot.framework.agents.config.AgentProperties;
import com.hypersense.boot.framework.agents.tool.ToolProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 网络搜索工具
 * <p>
 * 通过调用外部搜索 API 获取搜索结果，供 Agent 节点在执行任务时使用。
 * 支持 SearXNG（自托管）和 SerpAPI 等兼容接口。
 * </p>
 *
 * <h3>配置项：</h3>
 * <pre>
 * agent:
 *   tools:
 *     search:
 *       enabled: true
 *       endpoint: http://localhost:8080/search    # SearXNG 实例地址
 *       api-key:                                   # 可选，SerpAPI 等需要
 *       max-results: 5                             # 最大返回条数
 * </pre>
 *
 * @author Claude
 * @since 2026/5/16
 */
@Slf4j
@Component
public class InternetSearchTool implements ToolProvider {

    private final AgentProperties.SearchConfig searchConfig;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public InternetSearchTool(AgentProperties agentProperties) {
        this.searchConfig = agentProperties.getTools().getSearch();
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public String name() {
        return "internet_search";
    }

    @Override
    public String description() {
        return "通过互联网搜索获取信息。参数：query（搜索关键词），返回搜索结果列表（标题、摘要、链接）";
    }

    @Override
    public Object execute(Map<String, Object> params) {
        if (!Boolean.TRUE.equals(searchConfig.getEnabled())) {
            return errorResult("网络搜索工具未启用，请在配置中设置 agent.tools.search.enabled=true");
        }
        if (searchConfig.getEndpoint() == null || searchConfig.getEndpoint().isBlank()) {
            return errorResult("搜索 API 地址未配置，请设置 agent.tools.search.endpoint");
        }

        String query = extractQuery(params);
        if (query == null || query.isBlank()) {
            return errorResult("缺少 query 参数");
        }

        log.info("InternetSearchTool: 搜索关键词=[{}]", query);

        try {
            String url = buildSearchUrl(query);
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .GET();

            // API Key 通过 HTTP Header 传递，避免暴露在 URL 中
            String apiKey = searchConfig.getApiKey();
            if (apiKey != null && !apiKey.isBlank()) {
                if (searchConfig.getEndpoint().contains("serpapi")) {
                    requestBuilder.header("X-API-Key", apiKey);
                } else {
                    requestBuilder.header("Authorization", "Bearer " + apiKey);
                }
            }

            HttpRequest request = requestBuilder.build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("InternetSearchTool: 搜索 API 返回状态码 {}", response.statusCode());
                return errorResult("搜索服务返回错误: HTTP " + response.statusCode());
            }

            List<Map<String, String>> results = parseResults(response.body());
            log.info("InternetSearchTool: 搜索完成，返回 {} 条结果", results.size());

            return Map.of(
                    "success", true,
                    "query", query,
                    "resultCount", results.size(),
                    "results", results
            );
        } catch (Exception e) {
            log.error("InternetSearchTool: 搜索异常", e);
            return errorResult("搜索失败: " + e.getMessage());
        }
    }

    // ========== 私有方法 ==========

    /**
     * 从参数中提取搜索关键词
     */
    private String extractQuery(Map<String, Object> params) {
        // 优先使用显式 query 参数
        Object query = params.get("query");
        if (query != null && !query.toString().isBlank()) {
            return query.toString();
        }
        // 兼容：从 todo_description 取关键词
        Object todoDesc = params.get("todo_description");
        return todoDesc != null ? todoDesc.toString() : null;
    }

    /**
     * 构建搜索请求 URL
     * <p>
     * 兼容 SearXNG 格式：{endpoint}?q={query}&format=json
     * API Key 通过 HTTP Header 传递，不再拼接到 URL 中
     * </p>
     */
    private String buildSearchUrl(String query) {
        String endpoint = searchConfig.getEndpoint();
        StringBuilder url = new StringBuilder(endpoint);
        String separator = endpoint.contains("?") ? "&" : "?";

        url.append(separator).append("q=").append(URLEncoder.encode(query, StandardCharsets.UTF_8));

        // SearXNG 使用 format=json
        if (!endpoint.contains("serpapi")) {
            url.append("&format=json");
        }

        return url.toString();
    }

    /**
     * 解析搜索结果 JSON
     * <p>
     * 优先按 SearXNG 格式解析，失败则尝试通用格式。
     * SearXNG 返回格式：{ "results": [{ "title": "...", "url": "...", "content": "..." }] }
     * </p>
     */
    private List<Map<String, String>> parseResults(String body) {
        int maxResults = searchConfig.getMaxResults() != null ? searchConfig.getMaxResults() : 5;
        List<Map<String, String>> results = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode items = root.has("results") ? root.get("results") : root;

            if (items.isArray()) {
                int count = 0;
                for (JsonNode item : items) {
                    if (count >= maxResults) break;

                    String title = getTextOrDefault(item, "title", "");
                    String url = getTextOrDefault(item, "url", "link", "");
                    String snippet = getTextOrDefault(item, "content", "snippet", "");

                    if (title.isBlank() && snippet.isBlank()) continue;

                    Map<String, String> entry = new HashMap<>();
                    entry.put("title", title);
                    entry.put("url", url);
                    entry.put("snippet", snippet);
                    results.add(entry);
                    count++;
                }
            }
        } catch (Exception e) {
            log.warn("InternetSearchTool: 解析搜索结果异常", e);
        }
        return results;
    }

    /**
     * 从 JSON 节点中尝试多个字段名获取文本值
     */
    private String getTextOrDefault(JsonNode node, String... fieldNames) {
        for (String field : fieldNames) {
            if (node.has(field) && !node.get(field).isNull()) {
                String text = node.get(field).asText("").trim();
                if (!text.isBlank()) return text;
            }
        }
        return "";
    }

    /**
     * 构建错误结果
     */
    private Map<String, Object> errorResult(String message) {
        return Map.of("success", false, "error", message);
    }
}
