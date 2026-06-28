package com.hypersense.boot.framework.agents.tool.impl;

import com.hypersense.boot.framework.agents.profile.lint.SymbolRegistry;
import com.hypersense.boot.framework.agents.tool.ToolProvider;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * package_lookup 工具：在 LLM 写代码前确认第三方 package 真实存在并取最新版本号。
 *
 * <h3>核心动机</h3>
 * Capability Profile 的 code-profile 通过 {@link SymbolRegistry} + NoPhantomApiRule 拦截
 * 幻觉 API：LLM 调本工具成功后，对应的 symbol 自动注册到 registry，后续 file_write 写源码
 * 才能通过 lint。
 *
 * <h3>支持的语言</h3>
 * <ul>
 *   <li>python → PyPI JSON API</li>
 *   <li>javascript / typescript → npm registry</li>
 *   <li>java → maven central（仅返回存在性，版本号暂不解析）</li>
 * </ul>
 *
 * <h3>ToolProvider 适配</h3>
 * 本项目使用自定义 {@link ToolProvider} 接口（非 LangChain4j {@code @Tool} 注解），
 * 与 {@code DesignAssetFetchTool} / {@code FileRenderTool} 保持一致：
 * {@code name()} / {@code description()} / {@code specification()} / {@code execute(Map)}。
 * 原 spec 草稿的 {@code lookup(...)} 方法保留为 public，供单测直接调用，并由
 * {@link #execute(Map)} 从 args Map 解包后委托调用。
 *
 * <h3>baseUrl 注入（关键适配）</h3>
 * spec 草稿 {@code buildUrl()} 直接拼 {@code https://pypi.org/...} 绝对 URL，会绕过测试
 * RestClient 的 baseUrl 导致真实网络调用。改造方式照搬 DesignAssetFetchTool：
 * 三组 base 字段（{@link #pypiApi}/{@link #npmApi}/{@link #mavenApi}），
 * 生产构造用默认常量，测试构造全传空串，使 {@code buildUrl} 拼出相对路径走 MockWebServer。
 *
 * @author Claude
 * @since 2026/6/29
 */
@Slf4j
@Component
public class PackageLookupTool implements ToolProvider {

    private static final String DEFAULT_PYPI_API = "https://pypi.org/pypi";
    private static final String DEFAULT_NPM_API = "https://registry.npmjs.org";
    private static final String DEFAULT_MAVEN_API = "https://search.maven.org/solrsearch/select";

    private static final Pattern PYPI_VERSION = Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern PYPI_SUMMARY = Pattern.compile("\"summary\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern NPM_LATEST = Pattern.compile("\"latest\"\\s*:\\s*\"([^\"]+)\"");

    private final SymbolRegistry registry;
    private final RestClient httpClient;
    /** 可注入的 API base，生产环境为公网地址，单测通过 MockWebServer 替换以隔离网络 */
    private final String pypiApi;
    private final String npmApi;
    private final String mavenApi;

    /**
     * 生产构造：SymbolRegistry 由 Spring 注入，RestClient 用默认实例，三个 base 走公网默认。
     */
    public PackageLookupTool(SymbolRegistry registry) {
        this(registry, RestClient.create(),
                DEFAULT_PYPI_API, DEFAULT_NPM_API, DEFAULT_MAVEN_API);
    }

    /**
     * 单测专用：注入 MockWebServer 的 baseUrl 作为上游 API 的 base。
     * 三个 base 传空串，使 buildUrl 拼出相对路径走 MockWebServer。
     */
    public PackageLookupTool(SymbolRegistry registry, RestClient httpClient) {
        this(registry, httpClient, "", "", "");
    }

    private PackageLookupTool(SymbolRegistry registry, RestClient httpClient,
                              String pypiApi, String npmApi, String mavenApi) {
        this.registry = registry;
        this.httpClient = httpClient;
        this.pypiApi = pypiApi;
        this.npmApi = npmApi;
        this.mavenApi = mavenApi;
    }

    @Override
    public String name() {
        return "package_lookup";
    }

    @Override
    public String description() {
        return "在写代码前确认第三方 package 真实存在并取最新版本号。"
                + "支持 python(PyPI) / javascript/typescript(npm) / java(Maven Central)。"
                + "成功后会自动把传入的 symbol 注册到 SymbolRegistry，"
                + "使后续 file_write 源码能通过 NoPhantomApiRule lint。"
                + "返回 exists/version/docs/symbolFound/raw。";
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name("package_lookup")
                .description("确认第三方 package 存在并取最新版本，成功后注册 symbol 到 registry。"
                        + "防止 LLM 编造不存在的 API。")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("language",
                                "目标语言：python / javascript / typescript / java")
                        .addStringProperty("pkg", "包名，如 numpy / react / org.apache.commons:commons-lang3")
                        .addStringProperty("symbol",
                                "将要使用的符号（可选）。如 np.array、useState；成功后会注册到 registry")
                        .addStringProperty("sessionId", "会话 id（ToolNode 自动注入）")
                        .required("language", "pkg")
                        .build())
                .build();
    }

    /**
     * ToolProvider 入口：从 LLM function-call 的 arguments Map 中解包 language/pkg/symbol/sessionId，
     * 委托给 {@link #lookup(String, String, String, String)}。
     */
    @Override
    public Object execute(Map<String, Object> params) {
        String language = str(params, "language");
        String pkg = str(params, "pkg");
        String symbol = str(params, "symbol");
        String sessionId = str(params, "sessionId");
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = "default";
        }
        return lookup(language, pkg, symbol, sessionId);
    }

    private static String str(Map<String, Object> params, String key) {
        if (params == null) return null;
        Object v = params.get(key);
        return v == null ? null : v.toString();
    }

    /**
     * 查询 package 是否存在并取版本。
     *
     * <p>保留为 public 便于单测（MockWebServer）直接调用，绕过 arguments Map 解包。</p>
     *
     * @param language python / javascript / typescript / java
     * @param pkg      包名
     * @param symbol   将使用的符号，可为 null
     * @param sessionId 会话 id
     * @return {@link LookupResult}
     */
    public LookupResult lookup(String language, String pkg, String symbol, String sessionId) {
        if (pkg == null || pkg.isBlank()) {
            return LookupResult.miss("pkg is blank");
        }
        String url;
        try {
            url = buildUrl(language, pkg);
        } catch (IllegalArgumentException e) {
            return LookupResult.miss(e.getMessage());
        }

        String body;
        int status;
        try {
            RestClient.ResponseSpec spec = httpClient.get().uri(url).retrieve();
            body = spec.body(String.class);
            status = 200;
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound nf) {
            log.debug("package_lookup 404 for {} {}: {}", language, pkg, nf.getStatusCode());
            return LookupResult.miss("404 not found");
        } catch (Exception e) {
            log.debug("package_lookup error for {} {}: {}", language, pkg, e.getMessage());
            return LookupResult.miss("error: " + e.getMessage());
        }

        if (body == null || body.isBlank() || body.contains("Not Found")) {
            return LookupResult.miss("empty body or Not Found");
        }

        String version = extractVersion(language, body);
        String docs = extractSummary(language, body);
        boolean symbolFound = false;

        if (symbol != null && !symbol.isBlank()) {
            String sid = (sessionId == null || sessionId.isBlank()) ? "default" : sessionId;
            registry.register(sid, symbol.trim());
            // 额外注册去掉模块前缀的简短形式：np.array → array
            int dot = symbol.lastIndexOf('.');
            if (dot >= 0 && dot < symbol.length() - 1) {
                String shortForm = symbol.substring(dot + 1).trim();
                if (!shortForm.isEmpty()) {
                    registry.register(sid, shortForm);
                }
            }
            symbolFound = true;
        }

        log.info("package_lookup hit: language={} pkg={} version={} symbolRegistered={}",
                language, pkg, version, symbolFound);

        return new LookupResult(true, version, docs, symbolFound, body);
    }

    private String buildUrl(String language, String pkg) {
        String encoded = URLEncoder.encode(pkg, StandardCharsets.UTF_8);
        String lang = language == null ? "" : language.toLowerCase();
        switch (lang) {
            case "python":
                return pypiApi + "/" + encoded + "/json";
            case "javascript":
            case "typescript":
                return npmApi + "/" + encoded;
            case "java":
                // maven central 接受 g:artifact 形式（用户传 org.apache.commons:commons-lang3）
                return mavenApi + "?q=g:" + encoded + "&rows=1&wt=json";
            default:
                throw new IllegalArgumentException("unsupported language: " + language);
        }
    }

    private String extractVersion(String language, String body) {
        String lang = language == null ? "" : language.toLowerCase();
        switch (lang) {
            case "python":
                return firstGroup(PYPI_VERSION, body);
            case "javascript":
            case "typescript":
                return firstGroup(NPM_LATEST, body);
            default:
                // java 暂不要求解析版本
                return null;
        }
    }

    private String extractSummary(String language, String body) {
        if (language == null || !language.equalsIgnoreCase("python")) {
            return null;
        }
        return firstGroup(PYPI_SUMMARY, body);
    }

    private static String firstGroup(Pattern p, String input) {
        Matcher m = p.matcher(input);
        return m.find() ? m.group(1) : null;
    }

    /**
     * package_lookup 查询结果。
     *
     * @param exists      package 是否存在
     * @param version     最新版本号（python/npm；java 可能为 null）
     * @param docs        package 摘要文档（仅 python 有）
     * @param symbolFound 是否成功注册了 symbol（symbol 入参为空时为 false）
     * @param raw         原始响应体
     */
    public record LookupResult(boolean exists, String version, String docs,
                               boolean symbolFound, String raw) {

        static LookupResult miss(String reason) {
            return new LookupResult(false, null, reason, false, null);
        }
    }
}
