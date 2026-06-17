package com.hypersense.boot.framework.agents.engine.node;

import com.hypersense.boot.framework.agents.engine.SubAgentEventBus;
import com.hypersense.boot.framework.agents.enums.AgentEventType;
import com.hypersense.boot.framework.agents.model.AgentEvent;
import com.hypersense.boot.framework.agents.model.DeepAgentState;
import com.hypersense.boot.framework.agents.model.TodoItem;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 最终汇总节点
 * <p>
 * 汇总所有 TODO 执行结果，调用 LLM 生成最终响应。
 * 完成后推送 {@link AgentEventType#FINAL_RESPONSE} 事件（data 带 finalResponse 文本）。
 * </p>
 *
 * @author Claude
 * @since 2026/5/15
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FinalizeNode implements NodeAction<DeepAgentState> {

    private final ChatModel chatModel;

    private static final String FINALIZE_SYSTEM_PROMPT = """
            你是一个结果汇总专家。根据任务执行情况，生成简洁、清晰的最终报告。

            报告要求：
            1. 总结完成的任务
            2. 列出关键发现和结果
            3. 如有未完成或失败的任务，说明原因
            """;

    @Override
    public Map<String, Object> apply(DeepAgentState state) {
        log.info("FinalizeNode: 开始汇总结果");

        // 短路：PlanNode 已直接回复（如简单问候），跳过 LLM 重新汇总，避免重复输出
        Optional<String> existing = state.finalResponse();
        if (existing.isPresent() && !existing.get().isBlank()) {
            String direct = existing.get();
            log.info("FinalizeNode: 复用 PlanNode 的直接回复（跳过汇总）");
            return Map.of(
                    DeepAgentState.FINAL_RESPONSE, direct,
                    DeepAgentState.MESSAGES, AiMessage.from(direct)
            );
        }

        List<TodoItem> todos = state.todos();
        String todoSummary = todos.stream()
                .map(t -> String.format("- [%s] %s: %s",
                        t.getStatus().getLabel(),
                        t.getDescription(),
                        t.getResult() != null ? t.getResult() : ""))
                .collect(Collectors.joining("\n"));

        // 产物文件摘要
        Map<String, String> files = state.files();
        String filesSummary = files.isEmpty()
                ? "（无产物文件）"
                : files.keySet().stream()
                .map(k -> "- " + k)
                .collect(Collectors.joining("\n"));

        String userPrompt = String.format("""
                原始指令：%s

                任务执行情况：
                %s

                产物文件：
                %s

                请生成最终报告。
                """, state.instructions(), todoSummary, filesSummary);

        List<ChatMessage> messages = List.of(
                SystemMessage.from(FINALIZE_SYSTEM_PROMPT),
                UserMessage.from(userPrompt)
        );

        String finalResponse;
        try {
            ChatResponse response = chatModel.chat(messages);
            finalResponse = response.aiMessage().text();
            log.info("FinalizeNode: 最终报告生成完成");
        } catch (Exception e) {
            finalResponse = "报告生成失败: " + e.getMessage();
            log.error("FinalizeNode: 报告生成异常", e);
        }

        // 流式推送 FINAL_RESPONSE（带 finalResponse 文本）
        var consumer = SubAgentEventBus.get();
        if (consumer != null) {
            AgentEvent event = AgentEvent.builder()
                    .type(AgentEventType.FINAL_RESPONSE)
                    .message("任务全部完成")
                    .data(Map.of("finalResponse", finalResponse))
                    .timestamp(System.currentTimeMillis())
                    .build();
            consumer.accept(event);
        }

        return Map.of(
                DeepAgentState.FINAL_RESPONSE, finalResponse,
                DeepAgentState.MESSAGES, AiMessage.from("任务全部完成。\n\n" + finalResponse)
        );
    }
}

