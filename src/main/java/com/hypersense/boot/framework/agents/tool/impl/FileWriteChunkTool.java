package com.hypersense.boot.framework.agents.tool.impl;

import com.hypersense.boot.framework.agents.sandbox.Sandbox;
import com.hypersense.boot.framework.agents.sandbox.SandboxManager;
import com.hypersense.boot.framework.agents.sandbox.SandboxResult;
import com.hypersense.boot.framework.agents.tool.ToolProvider;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * file_write_chunk 工具 — 分块写入大文件（spec §3.7）。
 * <p>
 * 解决单个 file_write 调用难以承载 8K+ tokens 大 HTML 的问题：LLM 按
 * {@code mode=start|append|end} 三阶段把内容切片投递，由 {@link SessionChunkBuffer}
 * 在内存中累积，{@code end} 时一次性落盘。
 * </p>
 *
 * <h3>参数</h3>
 * <ul>
 *   <li>{@code sessionId}：会话 id（ToolNode 自动注入）</li>
 *   <li>{@code filename}：目标文件名，相对路径（如 {@code landing.html}）</li>
 *   <li>{@code chunk}：本次 chunk 内容；{@code mode=start/end} 时可传空字符串</li>
 *   <li>{@code mode}：{@code start} | {@code append} | {@code end}</li>
 * </ul>
 *
 * <h3>落盘位置</h3>
 * 与 {@link FileWriteTool} 一致：写入沙箱工作目录的 {@code uploads/} 子目录，并返回
 * {@code relativePath/workspacePath/content} 等字段，保持与 {@code ToolNode} 既有
 * 文件副作用约定（FILE_CREATED 事件、前端附件刷新）兼容。
 *
 * <p>本工具主要用于自由 HTML 兜底场景；design-profile 优先走 {@code file_render} 模板渲染。</p>
 *
 * @author Claude
 * @since 2026/6/29
 */
@Slf4j
@Component
public class FileWriteChunkTool implements ToolProvider {

    /** 附件默认存放子目录，与 FileWriteTool 的约定一致 */
    private static final String UPLOADS_DIR = "uploads";

    private final SessionChunkBuffer buffer;
    private final SandboxManager sandboxManager;

    @Autowired
    public FileWriteChunkTool(SessionChunkBuffer buffer,
                              @Nullable SandboxManager sandboxManager) {
        this.buffer = buffer;
        this.sandboxManager = sandboxManager;
    }

    @Override
    public String name() {
        return "file_write_chunk";
    }

    @Override
    public String description() {
        return "写入文件，支持两种模式："
                + "(A) 单次写入 mode=write（推荐，适合 <8K tokens 的常规 HTML）：一次性传入完整内容直接落盘；"
                + "(B) 分块写入（适合 8K+ tokens 大文件）：先 mode=start 初始化缓冲，"
                + "再多次 mode=append 追加 chunk，最后 mode=end 一次性落盘。"
                + "默认 mode=write；分块模式下 mode=start/end 时 chunk 可传空字符串。";
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name("file_write_chunk")
                .description("写入文件。mode=write 单次落盘（默认，适合常规 HTML）；"
                        + "或 mode=start/append/end 三阶段分块（适合 8K+ tokens 大文件）。")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("filename", "目标文件名（如 landing.html）；basename，禁止绝对路径/..")
                        .addStringProperty("chunk", "文件内容（mode=write 必填完整内容）；"
                                + "mode=start/end 可传空字符串")
                        .addStringProperty("mode", "模式：write（默认，单次）| start | append | end")
                        .required(List.of("filename"))
                        .build())
                .build();
    }

    @Override
    public Object execute(Map<String, Object> params) {
        if (params == null) {
            return errorResult(null, null, "参数为空");
        }
        String sessionId = pickString(params, "sessionId");
        String filename = pickString(params, "filename", "fileName", "file_name", "path");
        String chunk = pickString(params, "chunk", "content", "contents", "text");
        String mode = pickString(params, "mode");

        if (filename == null || filename.isBlank()) {
            // 防御性兜底：LLM 漏传 filename 时从 chunk 的 <title> 推导，再不行用 output.html
            // 历史观察：LLM 经常把完整 HTML 塞进 chunk 但忘了带 filename，硬拒绝会导致整轮产物丢失
            String derived = deriveFilenameFromContent(chunk);
            log.warn("file_write_chunk: filename 缺失，从内容推导为 '{}' sessionId={}", derived, sessionId);
            filename = derived;
        }
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = "default";
            log.debug("FileWriteChunkTool: sessionId 缺失，回退到 default 会话");
        }
        String safeName = sanitizeFilename(filename);
        String normalizedMode = (mode == null || mode.isBlank()) ? "write" : mode.toLowerCase();

        try {
            switch (normalizedMode) {
                case "write" -> {
                    // 单次写入模式：LLM 一次性传完整内容，直接落盘，无需 start/append/end
                    if (chunk == null || chunk.isBlank()) {
                        return errorResult(sessionId, safeName,
                                "write 模式要求 chunk 非空（如需分块请用 start/append/end）");
                    }
                    Map<String, Object> writeResult = persist(sessionId, safeName, chunk);
                    log.info("file_write_chunk write(single-shot): sessionId={}, file={}, bytes={}, success={}",
                            sessionId, safeName, chunk.length(), writeResult.get("success"));
                    return writeResult;
                }
                case "start" -> {
                    // 防御性兜底：LLM 把完整 HTML 塞进 start 又没继续 append/end 时，直接当 write 落盘
                    if (chunk != null && !chunk.isBlank()) {
                        log.warn("file_write_chunk: start 携带 {} 字符内容，自动按 write 落盘 sessionId={}, file={}",
                                chunk.length(), sessionId, safeName);
                        Map<String, Object> writeResult = persist(sessionId, safeName, chunk);
                        return writeResult;
                    }
                    buffer.start(sessionId, safeName);
                    log.info("file_write_chunk start: sessionId={}, file={}", sessionId, safeName);
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("success", true);
                    result.put("status", "started");
                    result.put("filename", safeName);
                    result.put("sessionId", sessionId);
                    return result;
                }
                case "append" -> {
                    buffer.append(sessionId, safeName, chunk == null ? "" : chunk);
                    int size = buffer.bufferSize(sessionId, safeName);
                    log.debug("file_write_chunk append: sessionId={}, file={}, accumulated={}",
                            sessionId, safeName, size);
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("success", true);
                    result.put("status", "appended");
                    result.put("filename", safeName);
                    result.put("sessionId", sessionId);
                    result.put("accumulatedSize", size);
                    return result;
                }
                case "end" -> {
                    String content = buffer.end(sessionId, safeName);
                    if (content == null || content.isBlank()) {
                        return errorResult(sessionId, safeName,
                                "end 时缓冲为空：未 append 任何 chunk 或 start 后立即结束");
                    }
                    Map<String, Object> writeResult = persist(sessionId, safeName, content);
                    log.info("file_write_chunk end: sessionId={}, file={}, bytes={}, success={}",
                            sessionId, safeName, content.length(), writeResult.get("success"));
                    return writeResult;
                }
                default -> {
                    return errorResult(sessionId, safeName, "unknown mode: " + mode);
                }
            }
        } catch (IllegalStateException e) {
            // start/end 顺序错乱：把状态机错误信息透传给 LLM，便于其自纠
            log.warn("file_write_chunk 状态错误 sessionId={}, file={}, mode={}, err={}",
                    sessionId, safeName, normalizedMode, e.getMessage());
            return errorResult(sessionId, safeName, e.getMessage());
        } catch (Exception e) {
            log.error("file_write_chunk 失败 sessionId={}, file={}, mode={}",
                    sessionId, safeName, normalizedMode, e);
            return errorResult(sessionId, safeName, e.getMessage());
        }
    }

    /**
     * 把缓冲区完整内容写入沙箱（uploads/<filename>），返回与 FileWriteTool 同结构的成功/失败 Map。
     * <p>返回字段：success / filename / path / relativePath / workspacePath / content / bytesWritten
     * / message / (error) — 与 ToolNode.handleFileWriteSideEffect 期望的契约保持一致。</p>
     */
    private Map<String, Object> persist(String sessionId, String filename, String content) {
        String sandboxPath = UPLOADS_DIR + "/" + filename;
        String relativePath = sessionId + "/uploads/" + filename;
        String workspacePath = "workspace/" + relativePath;

        Map<String, Object> base = new LinkedHashMap<>();
        base.put("filename", filename);
        base.put("path", sandboxPath);
        base.put("relativePath", relativePath);
        base.put("workspacePath", workspacePath);
        base.put("content", content);
        base.put("bytesWritten", content.length());

        if (sandboxManager == null) {
            // 单测或沙箱未启用：返回 success=false 而非硬失败，便于上层降级
            base.put("success", false);
            base.put("message", "沙箱未就绪：SandboxManager 未注入，内容未落盘");
            base.put("error", "SandboxManager not injected");
            log.warn("file_write_chunk: SandboxManager 未注入，跳过真实写盘 filename={}", filename);
            return base;
        }

        try {
            Sandbox sandbox = sandboxManager.getOrCreate(sessionId);
            long startMs = System.currentTimeMillis();
            log.info("file_write_chunk: 写入开始 sessionId={}, file={}, bytes={}, startTs={}",
                    sessionId, filename, content.length(), startMs);
            SandboxResult result = sandbox.writeFile(sandboxPath, content);
            long elapsedMs = System.currentTimeMillis() - startMs;
            if (result.isSuccess()) {
                base.put("success", true);
                base.put("status", "written");
                base.put("elapsedMs", elapsedMs);
                base.put("message", "内容已写入工作空间: " + workspacePath);
                log.info("file_write_chunk: 写入完成 sessionId={}, file={}, bytes={}, elapsedMs={}, throughput={}KB/s",
                        sessionId, filename, content.length(), elapsedMs,
                        elapsedMs > 0 ? (content.length() / 1024.0 / (elapsedMs / 1000.0)) : -1);
            } else {
                base.put("success", false);
                base.put("elapsedMs", elapsedMs);
                base.put("message", "沙箱写入失败: " + workspacePath);
                base.put("error", result.getError() != null ? result.getError() : "未知错误");
                log.warn("file_write_chunk: 写入失败 sessionId={}, file={}, elapsedMs={}, err={}",
                        sessionId, filename, elapsedMs, base.get("error"));
            }
        } catch (Exception e) {
            base.put("success", false);
            base.put("message", "沙箱写入异常: " + e.getMessage());
            base.put("error", e.getMessage());
            log.error("file_write_chunk: 沙箱调用异常 sessionId={}, path={}", sessionId, sandboxPath, e);
        }
        return base;
    }

    /**
     * filename 缺失时的防御性推导：优先从 HTML {@code <title>} 抽取并 slugify，
     * 失败回退 {@code output.html}。绝不硬拒绝，避免整轮产物丢失。
     */
    private String deriveFilenameFromContent(String chunk) {
        if (chunk != null) {
            int titleIdx = chunk.indexOf("<title>");
            if (titleIdx >= 0) {
                int end = chunk.indexOf("</title>", titleIdx);
                if (end > titleIdx) {
                    String title = chunk.substring(titleIdx + 7, end).trim();
                    String slug = title.toLowerCase()
                            .replaceAll("[^a-z0-9\\u4e00-\\u9fa5]+", "-")
                            .replaceAll("^-+|-+$", "");
                    if (!slug.isBlank()) {
                        return slug + ".html";
                    }
                }
            }
        }
        return "output.html";
    }

    /**
     * filename 安全清洗：与 {@link FileWriteTool#resolveSandboxPath} 同口径，
     * 仅保留 basename（拒绝对路径 / 绝对路径 / 父级穿越），避免沙箱外目录被创建。
     */
    private String sanitizeFilename(String filename) {
        String normalized = filename.replace('\\', '/').trim();
        // 拒绝绝对路径 / 父级穿越：取最后一段 basename
        if (normalized.startsWith("/") || normalized.matches("^[A-Za-z]:.*")
                || normalized.startsWith("~") || normalized.contains("..")) {
            int slashIdx = normalized.lastIndexOf('/');
            normalized = slashIdx >= 0 ? normalized.substring(slashIdx + 1) : normalized;
        }
        int lastSlash = normalized.lastIndexOf('/');
        if (lastSlash >= 0) {
            normalized = normalized.substring(lastSlash + 1);
        }
        return normalized.isBlank() ? "output.html" : normalized;
    }

    private Map<String, Object> errorResult(String sessionId, String filename, String error) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("filename", filename);
        result.put("sessionId", sessionId);
        result.put("error", error);
        return result;
    }

    private static String pickString(Map<String, Object> params, String... keys) {
        if (params == null) {
            return null;
        }
        for (String key : keys) {
            Object v = params.get(key);
            if (v != null && !v.toString().isBlank()) {
                return v.toString();
            }
        }
        return null;
    }
}
