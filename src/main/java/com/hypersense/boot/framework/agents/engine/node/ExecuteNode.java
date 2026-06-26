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
import com.hypersense.boot.framework.agents.serializer.AttachmentContext;
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
 * </p>
 * <ul>
 *   <li>{@code tool}：当前 TODO 需要调用工具（file_write / file_read / internet_search / sandbox / reply_text 等）</li>
 *   <li>{@code delegate}：当前 TODO 复杂度高，需要委派给子 Agent</li>
 * </ul>
 * <p>
 * <b>direct 策略已废除</b>：所有问候、知识问答、解释、总结类需求必须使用 {@code reply_text} 工具，
 * 通过 {@code tool} 策略执行，使所有输出都经 ToolNode 留痕、可审计。
 * 若 LLM 仍输出 direct，{@link com.hypersense.boot.framework.agents.engine.route.RouteAfterExecute}
 * 会强制改路由到 ToolNode，并在 state 中注入警告提示。
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
    private final AttachmentContext attachmentContext;

    /**
     * 已废除的 direct 策略常量。
     * <p>
     * 保留用于路由器检测：当 LLM 仍输出 direct 时，路由器会强制改路由到 ToolNode，
     * 并在 state 中注入「请使用 reply_text 工具完成回复」的提示。
     * </p>
     *
     * @deprecated direct 策略已废除，LLM 不应再选择。若 LLM 仍输出 direct，
     * 会被 {@link com.hypersense.boot.framework.agents.engine.route.RouteAfterExecute}
     * 强制改为 tool 路由并使用 reply_text 工具完成回复。
     */
    @Deprecated
    public static final String STRATEGY_DIRECT = "direct";

    /** tool 策略常量：调用工具（含 reply_text）完成 TODO */
    public static final String STRATEGY_TOOL = "tool";

    /** delegate 策略常量：委派给子 Agent */
    public static final String STRATEGY_DELEGATE = "delegate";

    private static final String EXECUTE_SYSTEM_PROMPT = """
            你是任务执行策略决策器。根据当前 TODO 和上下文，从以下策略中选择一个：

            - tool: 当前 TODO 需要调用工具（file_write / file_read / internet_search / sandbox / reply_text 等）
              适用场景：
              * 网络搜索查实时信息、读写文件、执行代码、调用外部 API
              * 问候、闲聊、身份介绍、知识问答、概念解释、汇总整理、澄清确认等纯文本回复
                —— 这些场景必须使用 reply_text 工具，通过 tool 策略执行
            - delegate: 当前 TODO 复杂度高，需要委派给子 Agent

            禁止选择 direct 策略（已废除）。所有问候、知识问答、解释、总结类需求必须使用 reply_text 工具，
            通过 tool 策略执行，使所有输出都经工具留痕、可审计。

            【关键判断】
            - TODO 含"汇总/整理/总结/回复/回答/撰写/编排/解释/问候/澄清"等措辞 → tool（使用 reply_text 工具）
            - TODO 明确要"搜索/查询/获取实时/读文件/写文件" → tool（使用对应工具）
            - TODO 需要由专门子 Agent 处理 → delegate

            【输出格式】只输出一个单词：tool / delegate（不要解释，禁止输出 direct）
            """;

    @Override
    public Map<String, Object> apply(DeepAgentState state) {
        // allDone 短路：所有 TODO 都已完成时，不再选 TODO 执行，直接返回让 RouteAfterExecute 路由到 finalize。
        // 取消 plan 循环（tool → execute 回流）后的关键防御：避免在所有 TODO 已结束时仍重复触发 LLM 策略决策，
        // 也避免无 PENDING TODO 时被路由器兜底回 plan 触发漂移。
        java.util.List<TodoItem> allTodos = state.todos();
        if (allTodos != null && !allTodos.isEmpty()) {
            boolean allDone = allTodos.stream().allMatch(t ->
                    t.getStatus() == TodoStatus.COMPLETED || t.getStatus() == TodoStatus.FAILED);
            if (allDone) {
                log.info("ExecuteNode: 所有 TODO 已结束，跳过执行，等待 finalize");
                return java.util.Map.of(
                        DeepAgentState.MESSAGES, AiMessage.from("所有任务已完成")
                );
            }
        }

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

        // 让 LLM 决定执行策略（已废除 direct，决策器只会输出 tool / delegate；
        // 若 LLM 仍误输出 direct，decideStrategy 内部会强制降级为 tool）
        StrategyDecision strategyDecision = decideStrategy(state, todo);
        String strategy = strategyDecision.strategy;
        boolean directDowngraded = strategyDecision.directDowngraded;

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

        // direct 策略已废除：原 executeDirect 短路逻辑移除。
        // 若 LLM 误输出 direct（理论上 decideStrategy 已降级为 tool），此处不再特殊处理，
        // 交由 RouteAfterExecute 强制改路由到 ToolNode，并在 state 中注入警告。
        // 所有回复类需求必须通过 reply_text 工具完成，使输出可审计。

        // 构造面向下游的消息：若 decideStrategy 检测到 direct 降级，附带 reply_text 引导提示，
        // 让 ToolNode / RouteAfterExecute 在 state 中可见这次降级事件。
        String messageBody = String.format("开始执行: %s (策略: %s)", todo.getDescription(), strategy);
        if (directDowngraded) {
            messageBody = messageBody + "。LLM 输出了已废除的 direct 策略，请使用 reply_text 工具完成回复。";
        }
        result.put(DeepAgentState.MESSAGES, AiMessage.from(messageBody));

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

    /**
     * 调用 LLM 决定执行策略。
     * <p>
     * 已废除 direct 策略：LLM 输出 direct 时强制降级为 tool，使所有回复场景都走 reply_text 工具。
     * 异常时默认 tool（而非旧逻辑的 direct），确保不绕过工具链路。
     * </p>
     */
    private StrategyDecision decideStrategy(DeepAgentState state, TodoItem todo) {
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
            if (decision.contains("delegate")) {
                return new StrategyDecision(STRATEGY_DELEGATE, false);
            }
            // tool 关键字命中 → tool（包括含 reply_text 的回复场景）
            if (decision.contains("tool")) {
                return new StrategyDecision(STRATEGY_TOOL, false);
            }
            // direct 已废除：若 LLM 误输出 direct，强制降级为 tool，并标记 directDowngraded
            if (decision.contains("direct")) {
                log.warn("ExecuteNode: LLM 输出了已废除的 direct 策略，强制降级为 tool "
                        + "(TODO={}, 原始决策={})", todo.getDescription(), decision);
                return new StrategyDecision(STRATEGY_TOOL, true);
            }
            // 兜底：默认 tool，避免误判回退到 direct 绕过工具
            return new StrategyDecision(STRATEGY_TOOL, false);
        } catch (Exception e) {
            // 异常时默认 tool（不再回退 direct），确保不绕过工具链路
            log.warn("执行策略决策失败，默认使用 tool: {}", e.getMessage());
            return new StrategyDecision(STRATEGY_TOOL, false);
        }
    }

    /**
     * 策略决策结果载体。
     *
     * @param strategy          最终生效的策略（tool / delegate）
     * @param directDowngraded  true 表示 LLM 原输出 direct 被强制降级为 tool，
     *                          用于在 state 中注入警告提示
     */
    private record StrategyDecision(String strategy, boolean directDowngraded) {
    }

    // direct 策略已废除，原 executeDirect 方法已移除。
    // 所有回复类需求（问候/知识问答/解释/总结/澄清）改由 PlanNode 构造虚拟 reply_text TODO，
    // 或由 LLM 在 plan 阶段生成含 reply_text 工具引用的 TODO，统一经 ToolNode 执行。

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
