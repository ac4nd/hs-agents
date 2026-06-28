package com.hypersense.boot.framework.agents.tool.impl;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * design_asset_fetch 工具单测：基于 MockWebServer 验证三级兜底。
 * <p>
 * 覆盖 spec §3.7 + §5.4：
 * <ul>
 *   <li>svgl.dev 命中 → SVG data URL</li>
 *   <li>svgl 空数组 → simpleicons fallback 命中</li>
 *   <li>两个源全 404 → 空串 + warning（诚实占位）</li>
 * </ul>
 *
 * @author Claude
 * @since 2026/6/29
 */
class DesignAssetFetchToolTest {

    private MockWebServer server;
    private DesignAssetFetchTool tool;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        String baseUrl = server.url("/").toString();
        tool = new DesignAssetFetchTool(RestClient.builder().baseUrl(baseUrl).build());
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void shouldFetchLogoFromSvgl() {
        // mock svgl.dev 返回 SVG
        server.enqueue(new MockResponse()
                .setBody("[{\"name\":\"Apple\",\"svg\":\"<svg id=\\\"apple\\\"></svg>\"}]")
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        Map<String, Object> result = tool.fetchAssets(
                List.of("Apple"), List.of(),
                "test-session");

        assertNotNull(result.get("logos"));
        @SuppressWarnings("unchecked")
        Map<String, String> logos = (Map<String, String>) result.get("logos");
        assertTrue(logos.containsKey("Apple"));
        assertTrue(logos.get("Apple").startsWith("data:image/svg"));
    }

    @Test
    void shouldFallbackToSimpleiconsWhenSvglEmpty() {
        server.enqueue(new MockResponse().setBody("[]"));
        // simpleicons 也返回 mock
        server.enqueue(new MockResponse()
                .setBody("<svg xmlns=\"http://www.w3.org/2000/svg\"><path d=\"M0,0\"/></svg>")
                .setHeader(HttpHeaders.CONTENT_TYPE, "image/svg+xml"));

        Map<String, Object> result = tool.fetchAssets(List.of("Apple"), List.of(), "s1");
        assertNotNull(result.get("logos"));
    }

    @Test
    void shouldReturnHonestPlaceholderWhenAllSourcesFail() {
        server.enqueue(new MockResponse().setResponseCode(404));
        server.enqueue(new MockResponse().setResponseCode(404));

        Map<String, Object> result = tool.fetchAssets(List.of("UnknownBrand"), List.of(), "s1");
        @SuppressWarnings("unchecked")
        Map<String, String> logos = (Map<String, String>) result.get("logos");
        assertEquals("", logos.get("UnknownBrand"));
        @SuppressWarnings("unchecked")
        List<String> warnings = (List<String>) result.get("warnings");
        assertFalse(warnings.isEmpty(), "所有源失败时应有 warning");
    }
}
