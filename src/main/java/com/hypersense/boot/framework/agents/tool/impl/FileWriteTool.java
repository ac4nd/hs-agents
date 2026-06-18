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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件写入工具 — 将内容写入沙箱工作目录（uploads/ 子目录）。
 * <p>
 * 必填参数：
 * <ul>
 *   <li>filename：文件名（如 report.md）；<b>未提供时基于 TODO 描述自动生成</b></li>
 *   <li>content：要写入的完整文件内容；未提供时返回明确的失败结果</li>
 * </ul>
 * 兼容字段名（参数别名）：filename / fileName / file_name / path；content / contents。
 * </p>
 * <p>
 * 写入位置：沙箱工作目录下的 uploads/ 子目录（与 {@code FileReadTool}、{@code SandboxTool} 的附件约定一致）。
 * 若 filename 已包含目录前缀（如 {@code output/foo.md}），则按原路径写入；否则自动拼接 {@code uploads/}。
 * </p>
 *
 * @author Claude
 * @since 2026/5/15
 */
@Slf4j
@Component
public class FileWriteTool implements ToolProvider {

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /** 附件默认存放子目录，与 AttachmentVO 的路径约定一致 */
    private static final String UPLOADS_DIR = "uploads";

    private final SandboxManager sandboxManager;

    @Autowired
    public FileWriteTool(@Nullable SandboxManager sandboxManager) {
        this.sandboxManager = sandboxManager;
    }

    @Override
    public String name() {
        return "file_write";
    }

    @Override
    public String description() {
        return "将文本内容写入沙箱工作目录的指定文件（默认 uploads/ 子目录）。" +
                "必填参数：filename（文件名，如 report.md；用户没指定时请基于内容自动命名，" +
                "系统也会基于 TODO 描述兜底自动生成），content（要写入的完整文件内容，必填）。";
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name("file_write")
                .description("将文本内容写入沙箱工作目录的指定文件。filename 缺省时自动命名（如 report.md），content 必填。")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("filename", "目标文件名（如 mbappe.md）。用户未指定时请根据内容主题自动命名")
                        .addStringProperty("content", "完整文件内容（必填，多行文本，可含 Markdown 格式）")
                        .required(List.of("content"))
                        .build())
                .build();
    }

    @Override
    public Object execute(Map<String, Object> params) {
        // 兼容多种字段命名（filename / fileName / file_name / path）
        String filename = pickString(params, "filename", "fileName", "file_name", "path");
        String content = pickString(params, "content", "contents", "text", "body");
        // TODO 描述（ToolNode 自动注入），用于兜底自动命名
        String todoDesc = pickString(params, "todo_description", "instructions");

        // filename 兜底：基于 TODO 描述前 20 字 + 时间戳，避免 LLM 没传文件名时整步失败
        if (filename == null || filename.isBlank()) {
            filename = autoGenerateFilename(todoDesc);
        } else {
            // 若只给了目录或纯名字，做最小清洗
            filename = filename.trim();
        }

        // content 必须有真实值，不允许用空串/TODO 描述糊弄（避免写出空产物）
        if (content == null || content.isBlank()) {
            return Map.of(
                    "success", false,
                    "filename", filename,
                    "error", "缺少 content 参数：file_write 必须提供要写入文件的完整文本内容"
            );
        }

        // 计算沙箱内完整路径：若 filename 已带目录则按原样；否则拼 uploads/ 前缀（与附件约定一致）
        String fullPath = resolveSandboxPath(filename);

        Map<String, Object> base = new LinkedHashMap<>();
        base.put("filename", filename);
        base.put("path", fullPath);
        base.put("content", content);

        // 沙箱管理器未注入（如单元测试场景）时，回退为仅返回准备好的内容，避免硬失败
        if (sandboxManager == null) {
            log.warn("FileWriteTool: SandboxManager 未注入，跳过真实写盘 filename={}", filename);
            base.put("success", false);
            base.put("message", "沙箱未就绪：SandboxManager 未注入，内容未落盘");
            base.put("error", "SandboxManager not injected");
            return base;
        }

        // 从 params 取 sessionId（ToolNode 已透传），缺失时回退到 default 会话
        String sessionId = pickString(params, "sessionId");
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = "default";
            log.debug("FileWriteTool: sessionId 缺失，回退到 default 会话");
        }

        try {
            Sandbox sandbox = sandboxManager.getOrCreate(sessionId);
            SandboxResult result = sandbox.writeFile(fullPath, content);
            if (result.isSuccess()) {
                base.put("success", true);
                base.put("message", "内容已写入沙箱: " + fullPath);
                log.info("FileWriteTool: 写入成功 sessionId={}, path={}", sessionId, fullPath);
            } else {
                base.put("success", false);
                base.put("message", "沙箱写入失败: " + fullPath);
                base.put("error", result.getError() != null ? result.getError() : "未知错误");
                log.warn("FileWriteTool: 写入失败 sessionId={}, path={}, err={}",
                        sessionId, fullPath, result.getError());
            }
        } catch (Exception e) {
            base.put("success", false);
            base.put("message", "沙箱写入异常: " + e.getMessage());
            base.put("error", e.getMessage());
            log.error("FileWriteTool: 沙箱调用异常 sessionId={}, path={}", sessionId, fullPath, e);
        }
        return base;
    }

    /**
     * 计算沙箱内完整路径：
     * <ul>
     *   <li>filename 含路径分隔符（如 {@code output/foo.md} 或绝对路径）→ 原样返回</li>
     *   <li>纯文件名（如 {@code mbappe.md}）→ 拼接 {@code uploads/} 前缀</li>
     * </ul>
     */
    private String resolveSandboxPath(String filename) {
        if (filename == null || filename.isBlank()) {
            return UPLOADS_DIR + "/output.md";
        }
        if (filename.contains("/") || filename.contains("\\") || filename.startsWith(".")) {
            return filename.replace('\\', '/');
        }
        return UPLOADS_DIR + "/" + filename;
    }

    /**
     * 从多个候选键中取首个非空字符串值，兼容不同 LLM 输出风格。
     */
    private String pickString(Map<String, Object> params, String... keys) {
        if (params == null || keys == null) return null;
        for (String key : keys) {
            Object v = params.get(key);
            if (v != null && !v.toString().isBlank()) {
                return v.toString();
            }
        }
        return null;
    }

    /**
     * 基于 TODO 描述生成安全文件名：取前若干个非空白字符（仅保留中文/字母/数字/连字符），
     * 末尾追加时间戳保证唯一性，默认扩展名 .md。
     */
    private String autoGenerateFilename(String todoDesc) {
        String base;
        if (todoDesc != null && !todoDesc.isBlank()) {
            // 去掉首尾空白，按任意空白/标点切分取前若干段
            String cleaned = todoDesc.trim()
                    .replaceAll("[\\s\\p{Punct}，。、；：！？（）【】《》\"'`]+", "_")
                    .replaceAll("[^\\u4e00-\\u9fa5A-Za-z0-9_-]", "");
            if (cleaned.length() > 20) {
                cleaned = cleaned.substring(0, 20);
            }
            base = cleaned.isEmpty() ? "output" : cleaned;
        } else {
            base = "output";
        }
        return base + "-" + LocalDateTime.now().format(TS_FMT) + ".md";
    }
}
