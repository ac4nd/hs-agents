package com.hypersense.boot.framework.agents.engine.node;

import com.hypersense.boot.framework.agents.model.DeepAgentState;
import com.hypersense.boot.framework.agents.model.TodoItem;
import com.hypersense.boot.framework.agents.enums.TodoStatus;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 规划节点
 * <p>
 * 调用 LLM 分析当前任务状态，生成或更新 TODO 计划。
 * 这是 DeepAgents「显式规划」能力的核心实现。
 * </p>
 *
 * @author Claude
 * @since 2026/5/15
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlanNode implements NodeAction<DeepAgentState> {

    private final ChatModel chatModel;

    /** 连续解析失败的最大容忍次数，超过后将所有 PENDING TODO 标记为 FAILED 以打破循环 */
    private static final int MAX_PARSE_FAILURES = 3;

    private static final String PLAN_SYSTEM_PROMPT = """
            你是一个任务规划专家。根据用户的指令和当前进度，制定清晰的执行计划。

            规则：
            1. 将复杂任务分解为可独立执行的具体 TODO 项
            2. 每个 TODO 项描述必须明确、可操作
            3. 如果已有 TODO 列表，根据已完成项更新剩余计划
            4. 按优先级排序 TODO 项

            重要：你的回复必须只包含 TODO 行，不要输出其他解释性文字。
            每行严格使用以下格式：
            TODO: [任务描述]
            TODO: [任务描述]
            ...
            """;

    @Override
    public Map<String, Object> apply(DeepAgentState state) {
        log.info("PlanNode: 开始规划任务");

        // 构建提示上下文
        String instructions = state.instructions();
        List<TodoItem> existingTodos = state.todos();
        String existingSummary = existingTodos.isEmpty()
                ? "（暂无计划）"
                : existingTodos.stream()
                .map(t -> String.format("- [%s] %s", t.getStatus().getLabel(), t.getDescription()))
                .collect(Collectors.joining("\n"));

        String userPrompt = String.format("""
                用户指令：%s

                当前计划状态：
                %s

                请制定或更新执行计划。
                """, instructions, existingSummary);

        // 调用 LLM 生成计划
        List<ChatMessage> messages = List.of(
                SystemMessage.from(PLAN_SYSTEM_PROMPT),
                UserMessage.from(userPrompt)
        );

        ChatResponse response = chatModel.chat(messages);
        String planText = response.aiMessage().text();
        log.debug("PlanNode: LLM 规划结果\n{}", planText);

        // 解析 TODO 列表
        List<TodoItem> todos = parseTodos(planText, existingTodos);

        // 解析失败保护：当解析结果为空且存在未完成计划时，检测连续失败
        if (todos == existingTodos && !existingTodos.isEmpty()) {
            boolean hasPending = existingTodos.stream()
                    .anyMatch(t -> t.getStatus() == TodoStatus.PENDING || t.getStatus() == TodoStatus.IN_PROGRESS);
            if (hasPending) {
                log.warn("PlanNode: LLM 未输出有效 TODO 格式，planText 前 200 字符: {}",
                        planText.substring(0, Math.min(200, planText.length())));

                // 连续失败达到上限，强制将所有 PENDING TODO 标记为 FAILED（打破无限循环）
                int failCount = state.<Integer>value(DeepAgentState.ITERATION_COUNT).orElse(0);
                if (failCount >= MAX_PARSE_FAILURES) {
                    log.error("PlanNode: 连续 {} 次规划未能产生有效 TODO，强制终止", failCount);
                    List<TodoItem> failedTodos = existingTodos.stream()
                            .map(t -> t.getStatus() == TodoStatus.PENDING || t.getStatus() == TodoStatus.IN_PROGRESS
                                    ? TodoItem.builder().id(t.getId()).description(t.getDescription())
                                    .status(TodoStatus.FAILED).result("规划节点连续解析失败，强制终止")
                                    .assignedAgent(t.getAssignedAgent()).updatedAt(LocalDateTime.now()).build()
                                    : t)
                            .toList();
                    return Map.of(
                            DeepAgentState.TODOS, failedTodos,
                            DeepAgentState.MESSAGES, AiMessage.from("规划连续失败，任务已强制终止")
                    );
                }
            }
        }

        return Map.of(
                DeepAgentState.TODOS, todos,
                DeepAgentState.MESSAGES, AiMessage.from("规划完成，共 " + todos.size() + " 个任务项")
        );
    }

    /**
     * 解析 LLM 输出为 TodoItem 列表
     */
    private List<TodoItem> parseTodos(String planText, List<TodoItem> existingTodos) {
        List<TodoItem> todos = new ArrayList<>();
        String[] lines = planText.split("\n");

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isBlank()) continue;

            // 支持多种格式：TODO:, - TODO:, 1. TODO:, * TODO:
            String description = null;
            if (trimmed.contains("TODO:") || trimmed.contains("TODO ：")) {
                // 提取 TODO: 后面的描述
                int idx = trimmed.indexOf("TODO");
                int colonIdx = trimmed.indexOf(':', idx);
                if (colonIdx >= 0) {
                    description = trimmed.substring(colonIdx + 1).trim();
                }
            }

            if (description != null && !description.isBlank()) {
                TodoStatus status = findExistingStatus(description, existingTodos);
                todos.add(TodoItem.builder()
                        .id(UUID.randomUUID().toString().substring(0, 8))
                        .description(description)
                        .status(status)
                        .updatedAt(LocalDateTime.now())
                        .build());
            }
        }

        // 如果解析结果为空，保留原有计划
        return todos.isEmpty() ? existingTodos : todos;
    }

    /**
     * 查找已有 TODO 的状态
     */
    private TodoStatus findExistingStatus(String description, List<TodoItem> existingTodos) {
        return existingTodos.stream()
                .filter(t -> t.getDescription().equalsIgnoreCase(description))
                .map(TodoItem::getStatus)
                .findFirst()
                .orElse(TodoStatus.PENDING);
    }
}
