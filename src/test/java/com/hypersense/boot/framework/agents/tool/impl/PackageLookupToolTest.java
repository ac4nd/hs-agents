package com.hypersense.boot.framework.agents.tool.impl;

import com.hypersense.boot.framework.agents.profile.lint.SymbolRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;

class PackageLookupToolTest {

    private MockWebServer server;
    private SymbolRegistry registry;
    private PackageLookupTool tool;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        registry = SymbolRegistry.withCommonBuiltins();
        tool = new PackageLookupTool(
                registry,
                RestClient.builder().baseUrl(server.url("/").toString()).build());
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void shouldReturnTrueForExistingPypiPackage() {
        server.enqueue(new MockResponse()
                .setBody("{\"info\":{\"version\":\"1.26.0\",\"summary\":\"NumPy is the fundamental package\"}}")
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        PackageLookupTool.LookupResult result =
                tool.lookup("python", "numpy", null, "s1");

        assertTrue(result.exists());
        assertEquals("1.26.0", result.version());
    }

    @Test
    void shouldReturnFalseForPhantomPackage() {
        server.enqueue(new MockResponse().setResponseCode(404));

        PackageLookupTool.LookupResult result =
                tool.lookup("python", "this_package_does_not_exist_xyz", null, "s1");

        assertFalse(result.exists());
    }

    @Test
    void shouldRegisterSymbolOnSuccess() {
        server.enqueue(new MockResponse()
                .setBody("{\"info\":{\"version\":\"1.26.0\"}}")
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        tool.lookup("python", "numpy", "np.array", "s1");

        assertTrue(registry.contains("s1", "np.array"),
                "package_lookup 成功后应自动注册 symbol 到 registry");
    }

    @Test
    void shouldHandleNpmRegistry() {
        server.enqueue(new MockResponse()
                .setBody("{\"dist-tags\":{\"latest\":\"18.2.0\"}}")
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        PackageLookupTool.LookupResult result =
                tool.lookup("javascript", "react", "useState", "s1");

        assertTrue(result.exists());
        assertEquals("18.2.0", result.version());
        assertTrue(registry.contains("s1", "useState"));
    }
}
