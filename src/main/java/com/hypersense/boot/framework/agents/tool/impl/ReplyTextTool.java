package com.hypersense.boot.framework.agents.tool.impl;

import com.hypersense.boot.framework.agents.tool.ToolProvider;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 纯文本回复工具
 * <p>
 * 所有非工具操作的场景（问候、知识问答、解释、总结、澄清）必须使用此工具向用户输出文本。
 * 禁止在此工具中编造任何操作结果、文件路径、工具调用记录——它只是文本回显通道。
 * </p>
 * <p>
 * 本工具是「废除 ExecuteNode.direct 策略」的承接方：原 direct 由 LLM 直接生成回复，
 * 现统一改为通过 {@code reply_text} 工具执行，使所有输出都经 ToolNode 留痕、可审计。
 * </p>
 *
 * <h3>参数：</h3>
 * <ul>
 *   <li>{@code content}（必填）：完整回复正文，可包含 Markdown</li>
 *   <li>{@code replyType}（必填，枚举）：回复类型，取值 GREETING / QA / EXPLANATION / SUMMARY / CLARIFY</li>
 * </ul>
 *
 * <h3>返回：</h3>
 * <pre>{@code
 *   {success: true, replied: true, content: <透传>, replyType: <透传>}
 * }</pre>
 * <p>失败：content 缺失 → {@code {success: false, error: "content 参数缺失"}}</p>
 *
 * @author Claude
 * @since 2026/6/25
 */
@Slf4j
@Component
public class ReplyTextTool implements ToolProvider {

    /** replyType 合法取值集合；非法值兜底为 EXPLANATION */
    private static final Set<String> VALID_REPLY_TYPES = Set.of(
            "GREETING", "QA", "EXPLANATION", "SUMMARY", "CLARIFY"
    );

    /** replyType 兜底值 */
    private static final String DEFAULT_REPLY_TYPE = "EXPLANATION";

    @Override
    public String name() {
        return "reply_text";
    }

    @Override
    public String description() {
        return "向用户输出纯文本回复。所有非工具操作的场景（问候、知识问答、解释、总结、澄清）必须使用此工具。"
                + "禁止在此工具中编造任何操作结果、文件路径、工具调用记录。"
                + "必填参数：content（完整回复正文，可含 Markdown）、replyType（GREETING/QA/EXPLANATION/SUMMARY/CLARIFY）。";
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name("reply_text")
                .description("向用户输出纯文本回复。问候、知识问答、解释、总结、澄清类需求必须使用此工具，"
                        + "禁止在回复中编造文件路径、工具调用结果等操作痕迹。")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("content",
                                "完整回复正文（必填）。可包含 Markdown 格式。"
                                        + "禁止省略号、占位符；必须是面向用户的最终回复内容。")
                        .addStringProperty("replyType",
                                "回复类型枚举（必填）：GREETING（问候/闲聊）/ QA（知识问答）/ "
                                        + "EXPLANATION（解释说明）/ SUMMARY（汇总整理）/ CLARIFY（澄清确认）")
                        .required(List.of("content", "replyType"))
                        .build())
                .build();
    }

    @Override
    public Object execute(Map<String, Object> params) {
        // 兼容多种字段命名（content / text / body / message）
        String content = pickString(params, "content", "text", "body", "message");
        String replyType = pickString(params, "replyType", "reply_type", "type");

        // content 校验：缺失/空串 → 失败，避免回复空内容
        if (content == null || content.isBlank()) {
            log.warn("ReplyTextTool: content 参数缺失或为空，拒绝回复");
            return Map.of(
                    "success", false,
                    "error", "content 参数缺失",
                    "hint", "请通过 content 字段传入完整的回复正文"
            );
        }

        // replyType 校验：非法或缺失 → 兜底为 EXPLANATION
        String normalizedType = normalizeReplyType(replyType);

        log.info("ReplyTextTool: 输出回复 replyType={}, length={}", normalizedType, content.length());

        // 透传返回，保持 content 完整不裁剪，便于下游节点 / 前端展示
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("replied", true);
        result.put("content", content);
        result.put("replyType", normalizedType);
        return result;
    }

    /**
     * 规范化 replyType：空值或非法值 → 默认 EXPLANATION；合法值统一大写。
     */
    private String normalizeReplyType(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_REPLY_TYPE;
        }
        String upper = raw.trim().toUpperCase(Locale.ROOT);
        return VALID_REPLY_TYPES.contains(upper) ? upper : DEFAULT_REPLY_TYPE;
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
}
