package com.hypersense.boot.framework.agents.hitl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypersense.boot.framework.agents.config.AgentProperties;
import com.hypersense.boot.framework.agents.model.DeepAgentState;
import com.hypersense.boot.framework.agents.model.TodoItem;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 智能 HITL Gate 判断器
 * <p>
 * 在指定决策点调用 ChatModel，根据「歧义 / 风险 / 改动巨大」三个维度判断当前 Agent 动作是否需要用户确认。
 * 通过 prompt 工程（含 few-shot 示例）让 LLM 输出结构化 JSON 决策。
 * </p>
 *
 * <h3>容错策略：</h3>
 * <ul>
 *   <li>LLM 调用失败或 JSON 解析失败：默认放行（return pass）</li>
 *   <li>smart-gate 未启用：直接放行</li>
 *   <li>当前决策点未在 decisionPoints 配置中：直接放行</li>
 * </ul>
 *
 * @author Claude
 * @since 2026/6/17
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HitlGateChecker {

    private final ChatModel chatModel;
    private final AgentProperties agentProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 创建一个禁用智能门控的实例（供非 Spring 装配路径使用，如 {@code GodlikeAgent.Builder}）。
     * <p>
     * 内部 AgentProperties 的 smartGate.enabled 默认为 false，{@link #check} 会直接 pass。
     * </p>
     *
     * @param chatModel 复用外部 ChatModel（不会在禁用模式下被实际调用）
     */
    public static HitlGateChecker disabled(ChatModel chatModel) {
        AgentProperties props = new AgentProperties();
        props.getHitl().getSmartGate().setEnabled(false);
        return new HitlGateChecker(chatModel, props);
    }

    /**
     * 系统 prompt：定义角色、判断维度、输出格式
     */
    private static final String SYSTEM_PROMPT = """
            你是任务执行风险评审员。判断当前 Agent 即将执行的动作是否需要先取得用户确认。

            【判断维度】满足任一即需要确认：
            1. ambiguity（歧义）：用户原始指令意图不明确，存在 ≥2 种合理解读，且不同解读导致执行路径显著分叉
            2. risk（风险）：动作具有外部副作用、不可逆、或可能造成损失：删除/覆盖数据、付费、发邮件、调用生产 API、修改共享资源
            3. scope（改动巨大）：影响范围超出单一会话：跨多个系统/文件/服务，预计耗时 > 5min，或涉及架构级变更

            【不需要确认】：
            - 纯信息查询、计算、本地只读操作
            - 用户已明确批准的计划范围内的常规步骤
            - severity=low 的轻度提醒

            【severity 分级】
            - high：删除数据 / 付费 / 不可逆外部副作用
            - medium：跨系统改动 / 多文件批量修改 / 架构变更
            - low：本地操作 / 临时文件 / 单一只读

            你必须严格输出 JSON（仅 JSON，不要任何解释文字、markdown 包裹）：
            {"needConfirm": true|false, "severity": "low"|"medium"|"high", "dimension": "ambiguity"|"risk"|"scope"|null, "reason": "≤30 字中文说明"}

            【示例 1】
            输入：用户指令="帮我清空数据库里所有测试用户"  决策点=plan_completed
            输出：{"needConfirm":true,"severity":"high","dimension":"risk","reason":"涉及删除操作，需确认范围"}

            【示例 2】
            输入：用户指令="解释 Transformer 注意力机制"  决策点=plan_completed
            输出：{"needConfirm":false,"severity":"low","dimension":null,"reason":"纯信息查询"}

            【示例 3】
            输入：用户指令="优化代码"  决策点=plan_completed
            输出：{"needConfirm":true,"severity":"medium","dimension":"ambiguity","reason":"优化范围未明确"}

            【示例 4】
            输入：用户指令="在 /tmp 下创建 demo.py 并写入 hello world"  TODO="创建文件 /tmp/demo.py"  决策点=before_todo_execute
            输出：{"needConfirm":false,"severity":"low","dimension":null,"reason":"临时目录低风险"}

            【示例 5】
            输入：用户指令="给所有用户发送营销邮件"  TODO="批量发送邮件给 10000 用户"  决策点=before_todo_execute
            输出：{"needConfirm":true,"severity":"high","dimension":"risk","reason":"批量外部副作用，需确认"}
            """;

    /**
     * 执行智能判断
     *
     * @param state 当前图状态
     * @param point 决策点
     * @return 判断结果；任何异常情况下返回 pass()
     */
    public HitlDecision check(DeepAgentState state, DecisionPoint point) {
        AgentProperties.SmartGateConfig cfg = agentProperties.getHitl().getSmartGate();
        if (!Boolean.TRUE.equals(cfg.getEnabled())) {
            return HitlDecision.pass();
        }
        if (cfg.getDecisionPoints() == null || !cfg.getDecisionPoints().contains(point.getValue())) {
            return HitlDecision.pass();
        }

        try {
            String userPrompt = buildUserPrompt(state, point);
            List<ChatMessage> messages = List.of(
                    SystemMessage.from(SYSTEM_PROMPT),
                    UserMessage.from(userPrompt)
            );
            ChatResponse response = chatModel.chat(messages);
            String text = response.aiMessage().text();
            HitlDecision decision = parseDecision(text);

            // autoSkipLow：severity=low 时降级为放行
            if (Boolean.TRUE.equals(cfg.getAutoSkipLow())
                    && decision.isNeedConfirm()
                    && "low".equalsIgnoreCase(decision.getSeverity())) {
                log.info("HitlGateChecker[{}]: low severity 自动放行, reason={}", point.getValue(), decision.getReason());
                decision.setNeedConfirm(false);
            }

            log.info("HitlGateChecker[{}]: needConfirm={}, severity={}, dimension={}, reason={}",
                    point.getValue(), decision.isNeedConfirm(), decision.getSeverity(),
                    decision.getDimension(), decision.getReason());
            return decision;
        } catch (Exception e) {
            log.warn("HitlGateChecker[{}] 判断失败，默认放行: {}", point.getValue(), e.getMessage());
            return HitlDecision.pass();
        }
    }

    private String buildUserPrompt(DeepAgentState state, DecisionPoint point) {
        String instructions = state.instructions();
        String todosText = state.todos().stream()
                .map(t -> String.format("- [%s] %s", t.getStatus().getLabel(), t.getDescription()))
                .collect(Collectors.joining("\n"));

        StringBuilder sb = new StringBuilder();
        sb.append("用户原始指令：").append(instructions).append("\n\n");
        sb.append("当前 TODO 计划：\n").append(todosText.isEmpty() ? "（暂无）" : todosText).append("\n\n");
        sb.append("当前决策点：").append(point.getValue()).append("\n");

        if (point == DecisionPoint.BEFORE_TODO_EXECUTE) {
            TodoItem current = state.currentTodo().orElse(null);
            if (current != null) {
                sb.append("当前待执行 TODO：").append(current.getDescription()).append("\n");
                sb.append("执行策略：").append(state.executeStrategy()).append("\n");
            }
        }

        sb.append("\n请输出 JSON 决策。");
        return sb.toString();
    }

    /**
     * 解析 LLM 输出为 HitlDecision，容错处理
     */
    private HitlDecision parseDecision(String text) {
        if (text == null || text.isBlank()) {
            return HitlDecision.pass();
        }
        // 去除可能的 markdown 包裹
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('{');
            int end = trimmed.lastIndexOf('}');
            if (start >= 0 && end > start) {
                trimmed = trimmed.substring(start, end + 1);
            }
        }
        try {
            HitlDecision d = objectMapper.readValue(trimmed, HitlDecision.class);
            if (d.getSeverity() == null) d.setSeverity("low");
            return d;
        } catch (Exception e) {
            log.warn("HitlGateChecker JSON 解析失败，原文: {}，错误: {}", trimmed, e.getMessage());
            return HitlDecision.pass();
        }
    }
}
