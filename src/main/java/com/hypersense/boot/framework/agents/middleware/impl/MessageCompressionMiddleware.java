package com.hypersense.boot.framework.agents.middleware.impl;

import com.hypersense.boot.framework.agents.middleware.AgentMiddleware;
import com.hypersense.boot.framework.agents.model.DeepAgentState;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 消息压缩中间件
 * <p>
 * 当 MESSAGES 累积超过阈值时，通过 LLM 将旧消息压缩为摘要，
 * 写入 COMPRESSED_CONTEXT 通道。后续节点可读取压缩摘要作为上下文。
 * </p>
 *
 * <h3>压缩策略：</h3>
 * <pre>
 * 原始 MESSAGES: [m1, m2, ..., m20, m21(新增)]
 *                         ↑            ↑
 *                    压缩区间      保留区间
 *                summary = LLM([m1...m16])
 *
 * COMPRESSED_CONTEXT = "之前对话的摘要: ..."
 * 节点可在构建 LLM prompt 时优先读取 COMPRESSED_CONTEXT
 * </pre>
 *
 * <h3>触发条件（满足任一）：</h3>
 * <ul>
 *   <li>消息总数超过 maxMessages（默认 20）</li>
 *   <li>总字符数超过 maxTotalChars（默认 50000，约 25000 tokens）</li>
 * </ul>
 *
 * @author Claude
 * @since 2026/5/22
 */
@Slf4j
public class MessageCompressionMiddleware implements AgentMiddleware {

    private final ChatModel chatModel;
    private final int maxMessages;
    private final int maxTotalChars;
    private final int keepRecent;

    private static final String COMPRESSION_PROMPT = """
            你是对话历史压缩器。将以下对话历史压缩为简洁的摘要，保留：
            1. 已完成的任务及其结果
            2. 关键决策和发现
            3. 当前正在进行的任务
            4. 遇到的错误和解决方式

            摘要应简洁但信息完整，不超过 500 字。
            """;

    /**
     * @param chatModel     用于生成摘要的 LLM（可为 null，在 Builder.build() 中延迟注入）
     * @param maxMessages   最大消息条数（超过触发压缩），默认 20
     * @param maxTotalChars 最大总字符数（超过触发压缩），默认 50000
     * @param keepRecent    保留最近 N 条消息不压缩，默认 4
     */
    public MessageCompressionMiddleware(ChatModel chatModel, int maxMessages, int maxTotalChars, int keepRecent) {
        this.chatModel = chatModel;
        this.maxMessages = maxMessages;
        this.maxTotalChars = maxTotalChars;
        this.keepRecent = keepRecent;
    }

    public MessageCompressionMiddleware(ChatModel chatModel) {
        this(chatModel, 20, 50000, 4);
    }

    @Override
    public String name() {
        return "message-compression";
    }

    @Override
    public Map<String, Object> after(String nodeName, DeepAgentState state, Map<String, Object> output) {
        if (chatModel == null) {
            return output;
        }

        List<ChatMessage> messages = state.chatMessages();

        // 检查是否需要压缩
        if (!needsCompression(messages)) {
            return output;
        }

        log.info("MessageCompressionMiddleware: 触发压缩, node={}, 当前消息数={}, 总字符数={}",
                nodeName, messages.size(), totalChars(messages));

        // 分割消息：待压缩 + 保留
        int splitIndex = Math.max(0, messages.size() - keepRecent);
        List<ChatMessage> toCompress = messages.subList(0, splitIndex);

        if (toCompress.isEmpty()) {
            return output;
        }

        // 调用 LLM 压缩旧消息
        String summary = compressMessages(toCompress);
        if (summary == null || summary.isBlank()) {
            log.warn("MessageCompressionMiddleware: LLM 压缩返回空，跳过");
            return output;
        }

        // 将摘要写入 COMPRESSED_CONTEXT 通道（通过 output Map 注入）
        String existingSummary = state.compressedContext().orElse("");
        String newSummary = existingSummary.isBlank()
                ? summary
                : existingSummary + "\n\n--- 后续摘要 ---\n" + summary;

        Map<String, Object> modifiedOutput = new HashMap<>(output);
        modifiedOutput.put(DeepAgentState.COMPRESSED_CONTEXT, newSummary);

        log.info("MessageCompressionMiddleware: 压缩完成, {} 条旧消息 → 摘要（{} 字符）",
                toCompress.size(), summary.length());

        return modifiedOutput;
    }

    // ========== 内部方法 ==========

    private boolean needsCompression(List<ChatMessage> messages) {
        return messages.size() > maxMessages || totalChars(messages) > maxTotalChars;
    }

    private long totalChars(List<ChatMessage> messages) {
        return messages.stream()
                .mapToLong(m -> {
                    if (m instanceof SystemMessage sm) return sm.text().length();
                    if (m instanceof UserMessage um) return um.singleText().length();
                    if (m instanceof AiMessage am) return am.text() != null ? am.text().length() : 0;
                    return m.toString().length();
                })
                .sum();
    }

    private String compressMessages(List<ChatMessage> messages) {
        try {
            StringBuilder sb = new StringBuilder();
            for (ChatMessage msg : messages) {
                sb.append(formatMessage(msg)).append("\n");
            }

            List<ChatMessage> prompt = List.of(
                    SystemMessage.from(COMPRESSION_PROMPT),
                    UserMessage.from("请压缩以下对话历史：\n\n" + sb)
            );

            ChatResponse response = chatModel.chat(prompt);
            return response.aiMessage().text();
        } catch (Exception e) {
            log.error("MessageCompressionMiddleware: LLM 压缩调用失败", e);
            return null;
        }
    }

    private String formatMessage(ChatMessage msg) {
        String role;
        String content;
        if (msg instanceof SystemMessage sm) {
            role = "System";
            content = sm.text();
        } else if (msg instanceof UserMessage um) {
            role = "User";
            content = um.singleText();
        } else if (msg instanceof AiMessage am) {
            role = "Assistant";
            content = am.text() != null ? am.text() : "";
        } else {
            role = "Unknown";
            content = msg.toString();
        }
        return "[" + role + "] " + content;
    }
}
