package com.hypersense.boot.framework.agents.engine.node;

import com.hypersense.boot.framework.agents.enums.TodoStatus;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 执行节点
 * <p>
 * 选择下一个待执行的 TODO，决定执行策略：
 * - 直接由 LLM 回答
 * - 调用工具
 * - 委派给子 Agent
 * </p>
 *
 * @author Claude
 * @since 2026/5/15
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExecuteNode implements NodeAction<DeepAgentState> {

    private final ChatModel chatModel;

    private static final String EXECUTE_SYSTEM_PROMPT = """
            你是一个任务执行决策器。分析当前任务并决定执行方式。

            可选执行方式：
            1. "direct" — 直接生成回答
            2. "tool" — 需要调用工具（如文件读写等）
            3. "delegate" — 委派给专门的子 Agent

            回复格式（只回复一个词）：
            direct / tool / delegate
            """;

    @Override
    public Map<String, Object> apply(DeepAgentState state) {
        List<TodoItem> todos = state.todos();
        int iteration = state.iterationCount();

        log.info("ExecuteNode: 迭代 {}, 当前 {} 个 TODO", iteration, todos.size());

        // 选择下一个 PENDING 或 IN_PROGRESS 的 TODO
        Optional<TodoItem> nextTodo = todos.stream()
                .filter(t -> t.getStatus() == TodoStatus.PENDING || t.getStatus() == TodoStatus.IN_PROGRESS)
                .findFirst();

        if (nextTodo.isEmpty()) {
            log.info("ExecuteNode: 所有 TODO 已完成");
            return Map.of();
        }

        TodoItem todo = nextTodo.get();
        log.info("ExecuteNode: 执行 TODO [{}]", todo.getDescription());

        // 更新状态为 IN_PROGRESS
        List<TodoItem> updatedTodos = new ArrayList<>(todos);
        int idx = updatedTodos.indexOf(todo);
        todo = TodoItem.builder()
                .id(todo.getId())
                .description(todo.getDescription())
                .status(TodoStatus.IN_PROGRESS)
                .result(todo.getResult())
                .assignedAgent(todo.getAssignedAgent())
                .updatedAt(java.time.LocalDateTime.now())
                .build();
        updatedTodos.set(idx, todo);

        // 让 LLM 决定执行策略
        String strategy = decideStrategy(state, todo);

        return Map.of(
                DeepAgentState.CURRENT_TODO, todo,
                DeepAgentState.TODOS, updatedTodos,
                DeepAgentState.ITERATION_COUNT, iteration + 1,
                DeepAgentState.EXECUTE_STRATEGY, strategy,
                DeepAgentState.MESSAGES, AiMessage.from(
                        String.format("开始执行: %s (策略: %s)", todo.getDescription(), strategy))
        );
    }

    private String decideStrategy(DeepAgentState state, TodoItem todo) {
        try {
            List<ChatMessage> messages = List.of(
                    SystemMessage.from(EXECUTE_SYSTEM_PROMPT),
                    UserMessage.from("任务描述：" + todo.getDescription())
            );
            ChatResponse response = chatModel.chat(messages);
            String decision = response.aiMessage().text().trim().toLowerCase();
            if (decision.contains("tool")) return "tool";
            if (decision.contains("delegate")) return "delegate";
            return "direct";
        } catch (Exception e) {
            log.warn("执行策略决策失败，默认使用 direct: {}", e.getMessage());
            return "direct";
        }
    }
}
