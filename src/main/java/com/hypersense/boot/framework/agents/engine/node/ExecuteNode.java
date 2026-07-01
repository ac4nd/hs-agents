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
    private final com.hypersense.boot.framework.agents.profile.CapabilityProfileRegistry profileRegistry;
    private final com.hypersense.boot.framework.agents.profile.impl.TddPhaseManager tddPhaseManager;
    private final com.hypersense.boot.framework.agents.profile.lint.SymbolRegistry symbolRegistry;

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

        // 写入当前 TDD phase（仅 code profile 生效），供下游 ToolNode / 前端感知
        applyTddPhase(state, result);

        return result;
    }

    /**
     * 将当前 TDD phase 写入 state.CURRENT_PHASE（仅 code profile 生效）。
     * <p>phase 不可用时静默跳过，不影响主流程。</p>
     */
    private void applyTddPhase(DeepAgentState state, Map<String, Object> result) {
        String activeProfileId = state.<String>value(DeepAgentState.ACTIVE_PROFILE).orElse(null);
        if (!"code".equals(activeProfileId)) return;
        if (tddPhaseManager == null) return; // 非 Spring 路径未注入，静默跳过
        String sessionId = state.<String>value(DeepAgentState.SESSION_ID).orElse("__default__");
        try {
            com.hypersense.boot.framework.agents.profile.impl.TddPhase phase = tddPhaseManager.current(sessionId);
            result.put(DeepAgentState.CURRENT_PHASE, phase.name());
        } catch (Exception ignore) { /* 静默 */ }
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
                    SystemMessage.from(buildStrategyHint(state) + EXECUTE_SYSTEM_PROMPT),
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
     * 按 active profile 的 planStrategy 返回拆分 TODO 的策略提示（Plan A 框架接入点）。
     * profile 未设置/加载失败时返回空串，保留原拆分逻辑。
     */
    private String buildStrategyHint(DeepAgentState state) {
        if (state == null) return "";
        String activeProfileId = state.<String>value(com.hypersense.boot.framework.agents.model.DeepAgentState.ACTIVE_PROFILE).orElse(null);
        if (activeProfileId == null || activeProfileId.isBlank()) return "";
        try {
            String sessionId = state.<String>value(DeepAgentState.SESSION_ID).orElse(null);
            java.util.Map<String, Object> hints = state.<java.util.Map<String, Object>>value(DeepAgentState.PROFILE_HINTS).orElse(java.util.Map.of());
            com.hypersense.boot.framework.agents.profile.CapabilityProfile profile = profileRegistry.get(activeProfileId, sessionId, hints);
            return switch (profile.planStrategy()) {
                case OUTLINE_DEMO -> "\n【拆分策略】先输出 outline + 1 个 demo 项，待用户审批后再批量输出剩余 TODO。\n";
                case TDD -> {
                    StringBuilder sb = new StringBuilder("\n【拆分策略】TODO 顺序：(1) file_read 相关代码 (2) file_write 失败测试 (3) 等待用户审批测试方向 (4) file_write 实现 (5) sandbox_exec 测试 (6) lint。\n");
                    String tddSessionId = state.<String>value(DeepAgentState.SESSION_ID).orElse("__default__");
                    try {
                        com.hypersense.boot.framework.agents.profile.impl.TddPhase cur = tddPhaseManager.current(tddSessionId);
                        sb.append("当前 TDD 阶段：").append(cur.description())
                          .append("。本批 TODO 必须仅服务此阶段（不要跨阶段产出）。\n");
                    } catch (Exception ignore) { /* phase 不可用时静默，hint 仍有效 */ }
                    yield sb.toString();
                }
                case DIVERGE_THEN_STRUCTURE -> "\n【拆分策略】TODO 顺序：(1) 多轮 internet_search/web_reader 调研 (2) 收敛结论 (3) 推导 SMART 任务 (4) HITL 审批。\n";
                case OUTLINE_THEN_FILL -> "\n【拆分策略】TODO 顺序：(1) 输出文档 outline (2) 逐板块撰写。\n";
                case LAYERED_LEARNING -> "\n【拆分策略】TODO 顺序：(1) 评估用户水平 (2) 制定学习路径 (3) 由浅入深配示例。\n";
                case GENERIC -> "";
            };
        } catch (Exception e) {
            return "";
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

    /**
     * 工具执行后回调（由 ToolNode 在工具执行完后调用，Task 13 接入）。
     * <p>仅 code profile 生效。根据 toolName 推进 TddPhase 状态机：
     * <ul>
     *   <li>file_write → READ→TEST / TEST→TEST_HITL / IMPL→EXEC，并抽取源码 import 注册到 SymbolRegistry</li>
     *   <li>sandbox_exec → EXEC→LINT</li>
     * </ul>
     * </p>
     * <p>失败（如非法状态迁移）静默吞掉 + log.warn，不阻塞主流程。</p>
     *
     * @param state       当前 agent state（读 ACTIVE_PROFILE / SESSION_ID）
     * @param toolName    工具名（file_write / sandbox_exec 等）
     * @param toolResult  工具结果 Map（含写盘内容等；可空）
     */
    public void onToolExecuted(DeepAgentState state, String toolName, Map<String, Object> toolResult) {
        if (state == null || toolName == null) return;
        String activeProfileId = state.<String>value(DeepAgentState.ACTIVE_PROFILE).orElse(null);
        if (!"code".equals(activeProfileId)) return;
        if (tddPhaseManager == null) return; // 非 Spring 路径未注入，静默跳过
        String sessionId = state.<String>value(DeepAgentState.SESSION_ID).orElse("__default__");

        try {
            com.hypersense.boot.framework.agents.profile.impl.TddPhase cur = tddPhaseManager.current(sessionId);
            if ("file_write".equals(toolName)) {
                switch (cur) {
                    case READ -> tddPhaseManager.transition(sessionId, com.hypersense.boot.framework.agents.profile.impl.TddPhase.TEST);
                    case TEST -> tddPhaseManager.transition(sessionId, com.hypersense.boot.framework.agents.profile.impl.TddPhase.TEST_HITL);
                    case IMPL -> tddPhaseManager.transition(sessionId, com.hypersense.boot.framework.agents.profile.impl.TddPhase.EXEC);
                    default -> { /* 其他阶段不因 file_write 推进 */ }
                }
                registerImportsFromCode(extractFileContent(toolResult), sessionId);
            } else if ("sandbox_exec".equals(toolName)) {
                if (cur == com.hypersense.boot.framework.agents.profile.impl.TddPhase.EXEC) {
                    tddPhaseManager.transition(sessionId, com.hypersense.boot.framework.agents.profile.impl.TddPhase.LINT);
                }
            }
        } catch (Exception e) {
            log.warn("ExecuteNode.onToolExecuted: 推进 TDD 阶段失败 tool={}, session={}, err={}",
                    toolName, sessionId, e.getMessage());
        }
    }

    /** 从工具结果 Map 中提取写盘内容（FileWriteTool 的产物）。无则返回 null。 */
    private String extractFileContent(Map<String, Object> toolResult) {
        if (toolResult == null) return null;
        Object content = toolResult.get("content");
        if (content == null) content = toolResult.get("text");
        return content == null ? null : content.toString();
    }

    /**
     * 抽取源码 import 并注册到 SymbolRegistry（防 LLM 用 package_lookup 之外的 API）。
     * <ul>
     *   <li>Python：from X import Y / import X.Y → 注册 Y 或末段</li>
     *   <li>JS：import { Y } from 'X' → 注册每个 Y（含 as alias 处理）</li>
     * </ul>
     */
    private void registerImportsFromCode(String code, String sessionId) {
        if (code == null || code.isBlank()) return;
        // Python
        java.util.regex.Pattern pyImport = java.util.regex.Pattern.compile(
                "(?m)^\\s*(?:from\\s+([\\w.]+)\\s+import\\s+(\\w+)|import\\s+([\\w.]+))\\s*$");
        java.util.regex.Matcher m = pyImport.matcher(code);
        while (m.find()) {
            if (m.group(2) != null && !m.group(2).isEmpty()) {
                symbolRegistry.register(sessionId, m.group(2));
            }
            if (m.group(3) != null && !m.group(3).isEmpty()) {
                String[] parts = m.group(3).split("\\.");
                if (parts.length > 1) {
                    symbolRegistry.register(sessionId, parts[parts.length - 1]);
                }
            }
        }
        // JS
        java.util.regex.Pattern jsImport = java.util.regex.Pattern.compile(
                "import\\s+[^;]*?\\{([^}]+)\\}\\s*from\\s+['\"][^'\"]+['\"]");
        java.util.regex.Matcher jm = jsImport.matcher(code);
        while (jm.find()) {
            for (String s : jm.group(1).split(",")) {
                String sym = s.trim().split("\\s+as\\s+")[0].trim();
                if (!sym.isEmpty()) symbolRegistry.register(sessionId, sym);
            }
        }
    }
}
