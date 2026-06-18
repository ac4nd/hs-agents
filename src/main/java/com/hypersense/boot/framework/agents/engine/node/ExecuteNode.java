package com.hypersense.boot.framework.agents.engine.node;

import com.hypersense.boot.framework.agents.engine.SubAgentEventBus;
import com.hypersense.boot.framework.agents.enums.AgentEventType;
import com.hypersense.boot.framework.agents.enums.TodoStatus;
import com.hypersense.boot.framework.agents.hitl.DecisionPoint;
import com.hypersense.boot.framework.agents.hitl.HitlDecision;
import com.hypersense.boot.framework.agents.hitl.HitlGateChecker;
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

import java.util.ArrayList;
import java.util.HashMap;
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
 * <p>
 * 流式事件协议：
 * <ul>
 *   <li>选定 TODO 后 → {@link AgentEventType#TODO_STARTED}（data 带 todo）</li>
 *   <li>智能判断需确认 → {@link AgentEventType#INTERRUPT}（data 带 HitlDecision）</li>
 * </ul>
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
    private final HitlGateChecker hitlGateChecker;

    private static final String EXECUTE_SYSTEM_PROMPT = """
            你是任务执行决策器。分析当前 TODO 任务描述，判断该用哪种执行方式。

            【可选方式】
            - direct: 不需要任何外部工具，直接用 LLM 知识/已有上下文回答
              适用场景：汇总、整理、总结、分析、回复用户、组织语言、生成说明、
              基于已知搜索结果编排最终答复、解释概念、闲聊
            - tool: 必须调用工具才能完成（外部信息/文件/代码/API）
              适用场景：网络搜索查实时信息、读写文件、执行代码、调用外部 API
            - delegate: 委派给专门子 Agent

            【关键判断】
            - TODO 含"汇总/整理/总结/回复/回答/撰写/编排"等措辞 → direct（即使句子里有"信息"二字）
            - TODO 明确要"搜索/查询/获取实时/读文件/写文件" → tool
            - 仅当本步需要"获取外部新信息"时才用 tool

            【输出格式】只输出一个单词：direct / tool / delegate（不要解释）
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

        // 流式推送 TODO_STARTED（带 todo 描述与策略）
        Map<String, Object> startedData = new HashMap<>();
        startedData.put("todo", todo);
        startedData.put("strategy", strategy);
        startedData.put("iteration", iteration);
        emit(AgentEventType.TODO_STARTED, "开始执行: " + todo.getDescription(), startedData);

        Map<String, Object> result = new HashMap<>();
        result.put(DeepAgentState.CURRENT_TODO, todo);
        result.put(DeepAgentState.TODOS, updatedTodos);
        result.put(DeepAgentState.ITERATION_COUNT, iteration + 1);
        result.put(DeepAgentState.EXECUTE_STRATEGY, strategy);

        // direct 策略：直接调用 LLM 完成 TODO，标记 COMPLETED，避免无限回到 plan 空转
        if ("direct".equals(strategy)) {
            String directResult = executeDirect(state, todo);
            TodoItem completedTodo = TodoItem.builder()
                    .id(todo.getId())
                    .description(todo.getDescription())
                    .status(TodoStatus.COMPLETED)
                    .result(directResult)
                    .assignedAgent(todo.getAssignedAgent())
                    .updatedAt(java.time.LocalDateTime.now())
                    .build();
            List<TodoItem> todosAfterDirect = new ArrayList<>(updatedTodos);
            int directIdx = todosAfterDirect.indexOf(todo);
            todosAfterDirect.set(directIdx, completedTodo);

            result.put(DeepAgentState.TODOS, todosAfterDirect);
            result.put(DeepAgentState.MESSAGES, AiMessage.from(
                    String.format("已完成: %s\n\n%s", todo.getDescription(), directResult)));
            emit(AgentEventType.TODO_COMPLETED, "已完成: " + todo.getDescription(),
                    Map.of("todo", completedTodo, "result", directResult));
            return result;
        }

        result.put(DeepAgentState.MESSAGES, AiMessage.from(
                String.format("开始执行: %s (策略: %s)", todo.getDescription(), strategy)));

        // 智能 HITL Gate 判断（执行 TODO 前）
        HitlDecision decision = hitlGateChecker.check(state, DecisionPoint.BEFORE_TODO_EXECUTE);
        if (decision.isNeedConfirm()) {
            Map<String, Object> gateData = new HashMap<>();
            gateData.put("decisionPoint", DecisionPoint.BEFORE_TODO_EXECUTE.getValue());
            gateData.put("severity", decision.getSeverity());
            gateData.put("dimension", decision.getDimension());
            gateData.put("reason", decision.getReason());
            gateData.put("todo", todo);
            gateData.put("strategy", strategy);
            emit(AgentEventType.INTERRUPT, "执行前需用户确认: " + decision.getReason(), gateData);

            result.put(DeepAgentState.NEED_CONFIRMATION, true);
            result.put(DeepAgentState.INTERRUPT_REASON, decision.getReason());
            result.put(DeepAgentState.INTERRUPT_SEVERITY, decision.getSeverity());
            log.info("ExecuteNode: 智能门控触发中断, severity={}, reason={}",
                    decision.getSeverity(), decision.getReason());
        }

        return result;
    }

    private String decideStrategy(DeepAgentState state, TodoItem todo) {
        try {
            // state.instructions() 包含附件上下文（由 AgentServiceImpl.injectAttachmentContext 注入），
            // 让决策 LLM 能感知附件指代，避免在附件场景误判 direct/delegate 而绕过 sandbox 工具。
            String userContext = state.instructions() != null ? state.instructions() : "";
            List<ChatMessage> messages = List.of(
                    SystemMessage.from(EXECUTE_SYSTEM_PROMPT),
                    UserMessage.from("任务描述：" + todo.getDescription()
                            + "\n\n用户上下文（含附件信息，如有）：" + userContext)
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

    /**
     * direct 策略：直接调用 LLM 完成 TODO，返回结果文本。
     * <p>
     * 用于无需外部工具的任务（如汇总、解释、规划说明等），避免 ExecuteNode 仅决定策略而不执行
     * 导致回到 plan 节点空转。
     * </p>
     */
    private String executeDirect(DeepAgentState state, TodoItem todo) {
        try {
            String prompt = String.format("""
                    用户原始指令（含历史上下文区块，已是 ground truth，可直接引用）：%s

                    当前待完成 TODO：%s

                    请直接完成该 TODO 并输出面向用户的自然语言结果，不要提及"我是 AI 助手"等元描述。
                    规则提醒：
                    - 若 TODO 提到"上面/刚才/之前"的内容，请先从【历史摘要】或【最近对话】中查找，找到后直接基于该内容执行。
                    - 若 TODO 需要保存文件，请使用 sandbox 工具写入沙箱工作目录（不要只输出文本）。
                    - 仅当 TODO 明确要求新外部信息时，才提示需要工具支持。
                    """, state.instructions(), todo.getDescription());
            List<ChatMessage> messages = List.of(
                    SystemMessage.from("你是任务执行助手，按指令完成单步任务并输出自然语言结果。"),
                    UserMessage.from(prompt)
            );
            ChatResponse response = chatModel.chat(messages);
            return response.aiMessage().text();
        } catch (Exception e) {
            log.warn("direct 策略执行失败: {}", e.getMessage());
            return "（执行失败：" + e.getMessage() + "）";
        }
    }

    private void emit(AgentEventType type, String message, Map<String, Object> data) {
        var consumer = SubAgentEventBus.get();
        if (consumer == null) return;
        AgentEvent event = AgentEvent.builder()
                .type(type)
                .message(message)
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build();
        consumer.accept(event);
    }
}
