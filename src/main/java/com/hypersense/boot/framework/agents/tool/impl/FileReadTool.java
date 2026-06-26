package com.hypersense.boot.framework.agents.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypersense.boot.agents.service.AgentService;
import com.hypersense.boot.framework.agents.llm.ChatModelRegistry;
import com.hypersense.boot.framework.agents.sandbox.Sandbox;
import com.hypersense.boot.framework.agents.sandbox.SandboxManager;
import com.hypersense.boot.framework.agents.tool.ToolProvider;
import com.hypersense.boot.system.model.entity.LlmModelConfig;
import com.hypersense.boot.system.service.LlmModelConfigService;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 文件读取工具 — 读取沙箱工作目录下的任意文件。
 * <p>
 * 按文件类型分流：
 * <ul>
 *   <li>文本/代码：直接返回内容，&gt;8000 字符触发同模型摘要</li>
 *   <li>图片：当前模型支持视觉 → 多模态下发（base64 + ImageContent）；否则返回元信息</li>
 *   <li>其他二进制：仅返回元信息（大小/MIME/sha256）</li>
 * </ul>
 * 返回 JSON 字符串，ToolNode 通过 {@code multimodal=true} 字段识别多模态下发场景。
 * </p>
 *
 * @author Claude
 * @since 2026/5/15
 */
@Slf4j
@Component
public class FileReadTool implements ToolProvider {

    /** 文本切片上限；超过则触发摘要并截断，避免下一轮 prompt 过长 */
    private static final int TEXT_LIMIT = 8000;
    /** 摘要最大字符数 */
    private static final int SUMMARY_MAX_CHARS = 500;

    private final SandboxManager sandboxManager;
    private final ChatModelRegistry chatModelRegistry;
    private final AgentService agentService;
    private final LlmModelConfigService modelConfigService;
    private final ObjectMapper objectMapper;

    @Autowired
    public FileReadTool(@Nullable SandboxManager sandboxManager,
                        @Nullable ChatModelRegistry chatModelRegistry,
                        @Nullable @Lazy AgentService agentService,
                        @Nullable LlmModelConfigService modelConfigService) {
        this.sandboxManager = sandboxManager;
        this.chatModelRegistry = chatModelRegistry;
        this.agentService = agentService;
        this.modelConfigService = modelConfigService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String name() {
        return "read_file";
    }

    @Override
    public String description() {
        return "读取沙箱工作目录下指定文件的内容。当用户要求了解/查看/分析/读取某个文件、附件、文档时必须调用本工具。"
                + "文本文件直接返回内容；图片文件按当前模型能力返回 base64 或元信息；"
                + "其他二进制返回元信息（大小/MIME/sha256）。大文件（>8000 字符）自动截断并生成同模型摘要。";
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name("read_file")
                .description(description())
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("path", "沙箱内相对路径，如 uploads/readme.md")
                        .addIntegerProperty("maxLines", "最多读取的行数，默认 500")
                        .addIntegerProperty("offset", "从第几行开始读（0-based），默认 0")
                        .required(List.of("path"))
                        .build())
                .build();
    }

    @Override
    public Object execute(Map<String, Object> params) {
        if (params == null) {
            return json(errorNode(null, "参数为空"));
        }
        String path = pickString(params, "path", "filename", "file");
        int maxLines = toInt(params.get("maxLines"), 500);
        int offset = toInt(params.get("offset"), 0);
        String sessionId = pickString(params, "sessionId");

        // 安全校验：禁止绝对路径与父级穿越
        if (path == null || path.isBlank()) {
            return json(errorNode(null, "缺少 path 参数"));
        }
        if (path.contains("..") || path.startsWith("/") || path.startsWith("\\")) {
            return json(errorNode(path, "非法路径：禁止绝对路径与父级穿越"));
        }

        if (sandboxManager == null) {
            return json(errorNode(path, "沙箱未就绪：SandboxManager 未注入"));
        }
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = "default";
            log.debug("FileReadTool: sessionId 缺失，回退到 default 会话");
        }

        try {
            Sandbox sandbox = sandboxManager.getOrCreate(sessionId);
            byte[] bytes = sandbox.readAllBytes(path);
            if (bytes == null) {
                return json(errorNode(path, "文件不存在或读取为空"));
            }
            String mimeType = probeMimeType(path, bytes);
            long size = bytes.length;

            // 文本
            if (isText(mimeType, path)) {
                return handleText(path, bytes, mimeType, size, offset, maxLines, sessionId);
            }
            // 图片
            if (mimeType.startsWith("image/")) {
                return handleImage(path, bytes, mimeType, size, sessionId);
            }
            // 其他二进制
            return handleBinary(path, mimeType, size, bytes);
        } catch (UnsupportedOperationException e) {
            // 当前沙箱实现未覆盖 readAllBytes
            return json(errorNode(path, "当前沙箱不支持读取字节: " + e.getMessage()));
        } catch (Exception e) {
            log.warn("FileReadTool: 读取失败 path={}, err={}", path, e.getMessage());
            return json(errorNode(path, "读取失败: " + e.getMessage()));
        }
    }

    // ========== 分流处理 ==========

    private String handleText(String path, byte[] bytes, String mimeType, long size,
                              int offset, int maxLines, String sessionId) {
        String full = new String(bytes, StandardCharsets.UTF_8);
        // 按 \n 切割保留行结构；用 split("\n", -1) 保留末尾空行
        String[] lines = full.split("\n", -1);
        int totalLines = lines.length;
        int from = Math.max(0, Math.min(offset, totalLines));
        int to = Math.min(from + Math.max(1, maxLines), totalLines);
        String sliced = String.join("\n", Arrays.copyOfRange(lines, from, to));
        int returnedLines = to - from;

        boolean truncated = sliced.length() > TEXT_LIMIT;
        String summary = null;
        String content = sliced;
        if (truncated) {
            summary = summarize(sliced, path, sessionId);
            content = sliced.substring(0, TEXT_LIMIT);
        }

        Map<String, Object> node = new LinkedHashMap<>();
        node.put("kind", "text");
        node.put("path", path);
        node.put("mimeType", mimeType);
        node.put("size", size);
        node.put("truncated", truncated);
        node.put("totalLines", totalLines);
        node.put("returnedLines", returnedLines);
        node.put("offset", from);
        if (summary != null) {
            node.put("summary", summary);
        }
        node.put("content", content);
        return json(node);
    }

    private String handleImage(String path, byte[] bytes, String mimeType, long size, String sessionId) {
        String sha = sha256(bytes);
        boolean supportsVision = supportsVision(sessionId);
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("kind", "image");
        node.put("path", path);
        node.put("mimeType", mimeType);
        node.put("size", size);
        node.put("sha256", sha);
        if (supportsVision) {
            // 多模态下发：返回 base64 + multimodal=true，由 ToolNode 解析为 ImageContent
            String base64 = Base64.getEncoder().encodeToString(bytes);
            node.put("deliveredAs", "multimodal_base64");
            node.put("multimodal", true);
            node.put("base64", base64);
            node.put("note", "图片已多模态下发，直接分析图片内容");
        } else {
            node.put("deliveredAs", "metadata_only");
            node.put("multimodal", false);
            node.put("note", "当前模型不支持图片分析，建议切换支持视觉的模型（supports_vision=1）");
        }
        return json(node);
    }

    private String handleBinary(String path, String mimeType, long size, byte[] bytes) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("kind", "binary");
        node.put("path", path);
        node.put("mimeType", mimeType);
        node.put("size", size);
        node.put("sha256", sha256(bytes));
        node.put("note", "二进制文件无法直接读取，建议：1）使用专用工具转换为文本；"
                + "2）描述文件用途让模型给出分析方法");
        return json(node);
    }

    // ========== 模型能力判定 ==========

    /**
     * 当前 session 绑定的模型是否支持视觉输入。
     * 通过 AgentService.getSessionModelConfigId 拿到 modelConfigId，
     * 再查 sys_llm_model_config.supports_vision（1=支持）。
     */
    private boolean supportsVision(String sessionId) {
        if (agentService == null || modelConfigService == null) {
            log.warn("[FileReadTool] supportsVision: 依赖未注入 agentService={} modelConfigService={}",
                    agentService, modelConfigService);
            return false;
        }
        try {
            Long modelConfigId = agentService.getSessionModelConfigId(sessionId);
            log.info("[FileReadTool] supportsVision: sessionId={} → modelConfigId={}", sessionId, modelConfigId);
            if (modelConfigId == null) {
                log.warn("[FileReadTool] supportsVision: modelConfigId 为 null，无法判断视觉能力");
                return false;
            }
            LlmModelConfig mc = modelConfigService.getById(modelConfigId);
            if (mc == null) {
                log.warn("[FileReadTool] supportsVision: modelConfigId={} 未找到 LlmModelConfig 记录", modelConfigId);
                return false;
            }
            Integer v = mc.getSupportsVision();
            boolean result = Integer.valueOf(1).equals(v);
            log.info("[FileReadTool] supportsVision: modelConfigId={} modelName={} supportsVision={} → result={}",
                    modelConfigId, mc.getModelName(), v, result);
            return result;
        } catch (Exception e) {
            log.warn("[FileReadTool] supportsVision: 查询视觉能力异常 sessionId={}", sessionId, e);
            return false;
        }
    }

    /**
     * 调用当前 session 同款模型生成不超过 500 字的摘要。
     * 失败返回 null（外层走截断路径）。
     */
    private String summarize(String content, String path, String sessionId) {
        if (chatModelRegistry == null || agentService == null) {
            return null;
        }
        try {
            Long modelConfigId = agentService.getSessionModelConfigId(sessionId);
            ChatModel model = chatModelRegistry.getOrDefault(modelConfigId);
            String prompt = String.format(
                    "请用不超过 %d 个字摘要以下文件内容的关键信息（文件：%s）：\n\n%s",
                    SUMMARY_MAX_CHARS, path, content);
            List<ChatMessage> messages = List.of(
                    SystemMessage.from("你是文件摘要助手，只输出摘要正文，不要前后缀解释。"),
                    UserMessage.from(prompt)
            );
            ChatResponse resp = model.chat(ChatRequest.builder()
                    .messages(messages)
                    .build());
            String text = resp.aiMessage() != null ? resp.aiMessage().text() : null;
            if (text != null && text.length() > SUMMARY_MAX_CHARS * 2) {
                text = text.substring(0, SUMMARY_MAX_CHARS * 2);
            }
            return text;
        } catch (Exception e) {
            log.warn("FileReadTool: 同模型摘要失败，降级截断 path={}, err={}", path, e.getMessage());
            return null;
        }
    }

    // ========== 辅助方法 ==========

    /** 文本类型判定：MIME 以 text/ 开头，或扩展名在白名单 */
    private boolean isText(String mimeType, String path) {
        if (mimeType != null && mimeType.startsWith("text/")) {
            return true;
        }
        String ext = extractExtension(path);
        return TEXT_EXTENSIONS.contains(ext);
    }

    private static String extractExtension(String path) {
        if (path == null) return "";
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "";
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** MIME 探测：扩展名表优先（避免 Windows 上 .md/.csv 探测异常），失败再走 Files.probeContentType */
    private String probeMimeType(String path, byte[] bytes) {
        String ext = extractExtension(path);
        String mapped = EXTENSION_MIME.get(ext);
        if (mapped != null) {
            return mapped;
        }
        try {
            java.nio.file.Path tmp = java.nio.file.Paths.get(path);
            String probed = java.nio.file.Files.probeContentType(tmp);
            if (probed != null && !probed.isBlank()) {
                return probed;
            }
        } catch (Exception ignore) {
            // 路径在沙箱内可能是相对路径，probe 失败很常见，忽略
        }
        // 字节级兜底：UTF-8 可解码且无控制字符 → text/plain
        if (looksLikeText(bytes)) {
            return "text/plain";
        }
        return "application/octet-stream";
    }

    private static boolean looksLikeText(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return false;
        int sample = Math.min(bytes.length, 4096);
        int control = 0;
        for (int i = 0; i < sample; i++) {
            byte b = bytes[i];
            if (b == 0) return false; // NUL → 二进制
            if (b < 0x09 || (b > 0x0D && b < 0x20)) {
                control++;
            }
        }
        return control < sample / 10;
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(bytes);
            StringBuilder sb = new StringBuilder(dig.length * 2);
            for (byte b : dig) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static int toInt(Object v, int def) {
        if (v == null) return def;
        try {
            return Integer.parseInt(v.toString());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static String pickString(Map<String, Object> params, String... keys) {
        if (params == null) return null;
        for (String k : keys) {
            Object v = params.get(k);
            if (v != null && !v.toString().isBlank()) {
                return v.toString();
            }
        }
        return null;
    }

    private Map<String, Object> errorNode(String path, String message) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("kind", "error");
        node.put("path", path);
        node.put("message", message);
        return node;
    }

    private String json(Object node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            // 兜底：最小可用 JSON
            return "{\"kind\":\"error\",\"message\":\"JSON 序列化失败: " +
                    e.getMessage().replace("\"", "'") + "\"}";
        }
    }

    // 常见扩展名 → MIME 映射（覆盖文本/代码/图片）
    private static final Map<String, String> EXTENSION_MIME = Map.ofEntries(
            Map.entry("md", "text/markdown"),
            Map.entry("markdown", "text/markdown"),
            Map.entry("txt", "text/plain"),
            Map.entry("csv", "text/csv"),
            Map.entry("json", "application/json"),
            Map.entry("xml", "application/xml"),
            Map.entry("yaml", "text/yaml"),
            Map.entry("yml", "text/yaml"),
            Map.entry("log", "text/plain"),
            Map.entry("html", "text/html"),
            Map.entry("htm", "text/html"),
            Map.entry("css", "text/css"),
            Map.entry("js", "application/javascript"),
            Map.entry("mjs", "application/javascript"),
            Map.entry("ts", "application/typescript"),
            Map.entry("jsx", "text/jsx"),
            Map.entry("tsx", "text/tsx"),
            Map.entry("vue", "text/vue"),
            Map.entry("py", "text/x-python"),
            Map.entry("java", "text/x-java-source"),
            Map.entry("c", "text/x-c"),
            Map.entry("cpp", "text/x-c++"),
            Map.entry("cc", "text/x-c++"),
            Map.entry("h", "text/x-c"),
            Map.entry("hpp", "text/x-c++"),
            Map.entry("go", "text/x-go"),
            Map.entry("rs", "text/x-rust"),
            Map.entry("rb", "text/x-ruby"),
            Map.entry("php", "text/x-php"),
            Map.entry("kt", "text/x-kotlin"),
            Map.entry("swift", "text/x-swift"),
            Map.entry("sh", "application/x-sh"),
            Map.entry("bash", "application/x-sh"),
            Map.entry("sql", "application/sql"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"),
            Map.entry("bmp", "image/bmp"),
            Map.entry("pdf", "application/pdf"),
            Map.entry("doc", "application/msword"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("zip", "application/zip"),
            Map.entry("tar", "application/x-tar"),
            Map.entry("gz", "application/gzip")
    );

    /** 文本类扩展名白名单（即使 MIME 探测失败也按文本处理） */
    private static final java.util.Set<String> TEXT_EXTENSIONS = java.util.Set.of(
            "md", "markdown", "txt", "csv", "json", "xml", "yaml", "yml", "log",
            "py", "java", "js", "mjs", "ts", "jsx", "tsx", "vue", "html", "htm",
            "css", "sql", "sh", "bash", "go", "rs", "c", "cpp", "cc", "h", "hpp",
            "rb", "php", "kt", "swift"
    );
}
