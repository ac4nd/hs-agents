package com.hypersense.boot.framework.agents.engine.node;

import com.hypersense.boot.framework.agents.engine.SubAgentEventBus;
import com.hypersense.boot.framework.agents.enums.AgentEventType;
import com.hypersense.boot.framework.agents.config.AgentProperties;
import com.hypersense.boot.framework.agents.hitl.DecisionPoint;
import com.hypersense.boot.framework.agents.hitl.HitlDecision;
import com.hypersense.boot.framework.agents.hitl.HitlGateChecker;
import com.hypersense.boot.framework.agents.model.AgentEvent;
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
import java.util.HashMap;
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
 * <p>
 * 流式事件协议：
 * <ul>
 *   <li>开始 → {@link AgentEventType#NODE_EXECUTION}</li>
 *   <li>完成 → {@link AgentEventType#PLAN_CREATED}（data 带 todos）</li>
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
public class PlanNode implements NodeAction<DeepAgentState> {

    private final ChatModel chatModel;
    private final HitlGateChecker hitlGateChecker;
    private final AgentProperties agentProperties;

    /** 连续解析失败的最大容忍次数，超过后将所有 PENDING TODO 标记为 FAILED 以打破循环 */
    private static final int MAX_PARSE_FAILURES = 3;

    private static final String PLAN_SYSTEM_PROMPT = """
            你是任务规划专家。先判断用户指令的复杂度，再决定响应方式。

            【上下文区块说明】（重要！）
            用户指令中可能包含以下结构化区块，全部是已发生的事实，必须当作 ground truth 使用：
            - 【历史摘要】：早期对话的要点压缩，可直接引用，不需要再问用户。
            - 【最近对话】：最近几轮的 User/Assistant 完整原文。其中 Assistant 的内容是上一轮 Agent 实际给出的回复。
            - 【当前用户指令】：本轮用户的最新输入，可能引用"上面/上面那个/刚才的内容"，均指代【历史摘要】或【最近对话】中已存在的内容。
            当用户指令出现指代词（如"上面那个"、"刚才的信息"、"它"、"这个"）时，必须先在【最近对话】中查找指代对象，
            找到就直接基于该内容制定计划，禁止把"向用户确认信息来源"作为 TODO。
            只有当指代对象在历史区块中确实找不到时，才允许生成"向用户确认"类 TODO。

            【必须走 TODO 计划的场景】满足任一即必须生成 TODO（不能 DIRECT_REPLY）：
            1. 需要实时/外部信息：天气、新闻、股价、汇率、赛事比分、最新动态、当前时间相关的查询
            2. 需要调用工具：网络搜索、文件读写、代码执行、数据库查询、API 调用
            3. 需要多步骤协作、跨系统操作、生成产物（文件/代码/报告）
            4. 用户明确要求"查"、"搜"、"获取"、"打开"、"生成"、"创建"等动作动词
            对于上述场景，TODO 描述要明确包含工具使用意图，例如「使用网络搜索查询 X」。
            注意：若所需内容已经在【历史摘要】或【最近对话】中，无需再调工具获取，
            应直接基于已有内容生成后续步骤（如整理/保存/转换格式等）。

            【可以直接 DIRECT_REPLY 的场景】（仅限以下情形）：
            - 问候、闲聊、身份介绍（"你好"、"你是谁"、"谢谢"）
            - 用户已有信息范围内的纯知识问答（"什么是闭包"、"解释 Transformer 原理"）
            - 对历史对话的澄清/确认（"我刚才说的是…"、"明白了"）
            - 基于历史内容的小幅补充说明，无需新外部信息或工具调用

            【输出格式】严格二选一，不要任何额外解释、markdown 包裹：
            - DIRECT_REPLY: <自然回复正文>
            - TODO: <任务描述>（可多行）

            【示例 1】用户指令="你好"
            输出：DIRECT_REPLY: 你好！很高兴见到你。有什么我可以帮你的吗？

            【示例 2】用户指令="解释一下什么是闭包"
            输出：DIRECT_REPLY: 闭包是指……（自然语言解释）

            【示例 3】用户指令="查询福州今天的天气"
            输出：
            TODO: 使用网络搜索工具查询福州今日天气
            TODO: 汇总天气信息回复用户

            【示例 4】用户指令="最新 AI 新闻有哪些"
            输出：
            TODO: 使用网络搜索工具查询最新 AI 新闻
            TODO: 整理新闻要点回复用户

            【示例 5】用户指令="帮我生成一个 Python 数据清洗脚本并保存到 data_clean.py"
            输出：
            TODO: 设计数据清洗逻辑
            TODO: 编写 Python 脚本并保存为 data_clean.py
            """;

    @Override
    public Map<String, Object> apply(DeepAgentState state) {
        log.info("PlanNode: 开始规划任务");
        emit(AgentEventType.NODE_EXECUTION, "规划节点开始", Map.of("node", "plan"));

        // 迭代上限保护：超过配置的 max-iterations 强制 finalize，避免 plan↔execute 空转
        int iteration = state.iterationCount();
        Integer maxIterCfg = agentProperties.getDeep().getMaxIterations();
        int maxIter = maxIterCfg != null && maxIterCfg > 0 ? maxIterCfg : 25;
        if (iteration >= maxIter) {
            log.warn("PlanNode: 迭代次数已达上限 {}，强制结束（保留现有 todos 状态进入 finalize）", maxIter);
            String msg = String.format("已达迭代上限（%d 次），任务中止以防无限循环。", maxIter);
            List<TodoItem> frozenTodos = state.todos().stream()
                    .map(t -> (t.getStatus() == TodoStatus.PENDING || t.getStatus() == TodoStatus.IN_PROGRESS)
                            ? TodoItem.builder().id(t.getId()).description(t.getDescription())
                            .status(TodoStatus.FAILED).result("迭代超限未执行")
                            .assignedAgent(t.getAssignedAgent()).updatedAt(LocalDateTime.now()).build()
                            : t)
                    .toList();
            return Map.of(
                    DeepAgentState.TODOS, frozenTodos,
                    DeepAgentState.MESSAGES, AiMessage.from(msg)
            );
        }

        // 短路：已存在 todos 且全部已结束（COMPLETED/FAILED）→ 不再调用 LLM 重新规划，
        // 保留原状态让 RouteAfterPlan 路由到 finalize，避免无谓的 LLM 调用和描述漂移
        List<TodoItem> existingTodos = state.todos();
        if (!existingTodos.isEmpty()) {
            boolean allDone = existingTodos.stream().allMatch(t ->
                    t.getStatus() == TodoStatus.COMPLETED || t.getStatus() == TodoStatus.FAILED);
            if (allDone) {
                log.info("PlanNode: 所有 TODO 已结束，跳过 LLM 重新规划，直接进入 finalize");
                return Map.of(
                        DeepAgentState.TODOS, existingTodos,
                        DeepAgentState.MESSAGES, AiMessage.from("所有任务已完成")
                );
            }
            // 已有 todos 且至少一个处于 PENDING/IN_PROGRESS 时，跳过 LLM 重新规划，
            // 保留原 todos 让 RouteAfterPlan 路由到 execute 继续推进；
            // 避免 LLM 描述漂移（如"搜索"→"查询"）导致 findExistingStatus 精确匹配失败，
            // 已 COMPLETED 的 TODO 被当作新 PENDING 重新生成 → 死循环
            boolean hasPending = existingTodos.stream().anyMatch(t ->
                    t.getStatus() == TodoStatus.PENDING || t.getStatus() == TodoStatus.IN_PROGRESS);
            if (hasPending) {
                log.info("PlanNode: 已有 {} 个 TODO 含未完成项，跳过 LLM 重新规划，继续推进执行",
                        existingTodos.size());
                emit(AgentEventType.NODE_EXECUTION, "继续执行现有计划",
                        Map.of("node", "plan", "todos", existingTodos));
                return Map.of(
                        DeepAgentState.TODOS, existingTodos,
                        DeepAgentState.MESSAGES, AiMessage.from("继续执行现有计划")
                );
            }
        }

        // 构建提示上下文
        // MemoryMiddleware.before 通过 ThreadLocal 旁路注入记忆增强后的 instructions
        // （LangGraph4j 节点执行期间 state.data() 不可修改，无法直接 put）
        // ThreadLocal 清理由 wrapWithMemoryMiddleware 的 finally 块统一负责，避免节点异常时泄漏
        String memoryEnhanced = com.hypersense.boot.framework.agents.memory.MemoryMiddleware.ENHANCED_INSTRUCTIONS.get();
        String instructions = memoryEnhanced != null ? memoryEnhanced : state.instructions();
        String existingSummary = existingTodos.isEmpty()
                ? "（暂无计划）"
                : existingTodos.stream()
                .map(t -> String.format("- [%s] %s", t.getStatus().getLabel(), t.getDescription()))
                .collect(Collectors.joining("\n"));

        String userPrompt = String.format("""
                用户指令（含历史上下文区块，已是 ground truth，请直接基于其内容规划）：%s

                当前计划状态：
                %s

                请制定或更新执行计划。
                规则提醒：若用户指令引用了"上面/刚才/之前"的内容，且该内容出现在【历史摘要】或【最近对话】中，
                必须直接复用该内容规划后续步骤，禁止生成"向用户确认信息来源"类 TODO。
                """, instructions, existingSummary);

        // 调用 LLM 生成计划
        List<ChatMessage> messages = List.of(
                SystemMessage.from(PLAN_SYSTEM_PROMPT),
                UserMessage.from(userPrompt)
        );

        ChatResponse response = chatModel.chat(messages);
        String planText = response.aiMessage().text();
        log.debug("PlanNode: LLM 规划结果\n{}", planText);

        // 短路：LLM 直接回复简单对话（DIRECT_REPLY: 前缀），跳过 TODO 计划
        String directReply = parseDirectReply(planText);
        if (directReply != null) {
            log.info("PlanNode: 检测到简单对话，直接回复用户（不进入 TODO 流程）");
            Map<String, Object> directData = new HashMap<>();
            directData.put("finalResponse", directReply);
            emit(AgentEventType.FINAL_RESPONSE, "已回复用户", directData);

            Map<String, Object> result = new HashMap<>();
            result.put(DeepAgentState.TODOS, List.of());
            result.put(DeepAgentState.FINAL_RESPONSE, directReply);
            result.put(DeepAgentState.MESSAGES, AiMessage.from(directReply));
            return result;
        }

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
                // 类型守卫：若 ITERATION_COUNT 通道被污染为非 Integer（如 checkpoint 反序列化降级），
                // value() 会抛 ClassCastException 让死循环保护失效。捕获后回退为 0，触发强制终止路径。
                int failCount;
                try {
                    failCount = state.<Integer>value(DeepAgentState.ITERATION_COUNT).orElse(0);
                } catch (ClassCastException cce) {
                    log.warn("PlanNode: ITERATION_COUNT 类型异常，回退为 0: {}", cce.getMessage());
                    failCount = 0;
                }
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

        // 流式推送 PLAN_CREATED（带 todos）
        emit(AgentEventType.PLAN_CREATED, "规划完成，共 " + todos.size() + " 个任务项", Map.of("todos", todos));

        // 智能 HITL Gate 判断
        HitlDecision decision = hitlGateChecker.check(state, DecisionPoint.PLAN_COMPLETED);
        Map<String, Object> result = new HashMap<>();
        result.put(DeepAgentState.TODOS, todos);
        result.put(DeepAgentState.MESSAGES, AiMessage.from("规划完成，共 " + todos.size() + " 个任务项"));

        if (decision.isNeedConfirm()) {
            // 触发智能中断：emit INTERRUPT，置 NEED_CONFIRMATION=true
            Map<String, Object> gateData = new HashMap<>();
            gateData.put("decisionPoint", DecisionPoint.PLAN_COMPLETED.getValue());
            gateData.put("severity", decision.getSeverity());
            gateData.put("dimension", decision.getDimension());
            gateData.put("reason", decision.getReason());
            gateData.put("todos", todos);
            emit(AgentEventType.INTERRUPT, "规划需用户确认: " + decision.getReason(), gateData);

            result.put(DeepAgentState.NEED_CONFIRMATION, true);
            result.put(DeepAgentState.INTERRUPT_REASON, decision.getReason());
            result.put(DeepAgentState.INTERRUPT_SEVERITY, decision.getSeverity());
            log.info("PlanNode: 智能门控触发中断, severity={}, reason={}",
                    decision.getSeverity(), decision.getReason());
        }

        return result;
    }

    /**
     * 识别 DIRECT_REPLY: 前缀。匹配时返回去掉前缀的回复；否则返回 null。
     */
    private String parseDirectReply(String planText) {
        if (planText == null) return null;
        String trimmed = planText.trim();
        // 大小写不敏感 + 容忍全角冒号
        String prefix = "DIRECT_REPLY";
        if (trimmed.regionMatches(true, 0, prefix, 0, prefix.length())) {
            int colonIdx = trimmed.indexOf(':', prefix.length());
            if (colonIdx < 0) {
                colonIdx = trimmed.indexOf('：', prefix.length());
            }
            if (colonIdx >= 0) {
                String reply = trimmed.substring(colonIdx + 1).trim();
                if (!reply.isBlank()) {
                    return reply;
                }
            }
        }
        return null;
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

    /**
     * 通过 SubAgentEventBus 推送事件（线程本地，由 AgentServiceImpl 设置的消费者接收）
     */
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
