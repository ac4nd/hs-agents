package com.hypersense.boot.framework.agents.enums;

import lombok.Getter;

/**
 * Agent 事件类型枚举（用于 SSE 推送）
 *
 * @author Claude
 * @since 2026/5/15
 */
@Getter
public enum AgentEventType {

    /** 计划创建/更新 */
    PLAN_CREATED("plan_created"),
    /** TODO 开始执行 */
    TODO_STARTED("todo_started"),
    /** TODO 执行完成 */
    TODO_COMPLETED("todo_completed"),
    /** 工具调用 */
    TOOL_CALL("tool_call"),
    /** 子 Agent 委派 */
    SUB_AGENT_DELEGATED("sub_agent_delegated"),
    /** 子 Agent 开始执行 */
    SUB_AGENT_STARTED("sub_agent_started"),
    /** 子 Agent 内部节点执行 */
    SUB_AGENT_NODE_EXECUTION("sub_agent_node_execution"),
    /** 子 Agent 执行完成 */
    SUB_AGENT_COMPLETED("sub_agent_completed"),
    /** 子 Agent 执行失败 */
    SUB_AGENT_FAILED("sub_agent_failed"),
    /** 最终响应 */
    FINAL_RESPONSE("final_response"),
    /** 错误 */
    ERROR("error"),
    /** 节点执行 */
    NODE_EXECUTION("node_execution"),
    // ========== HITL 事件类型 ==========
    /** HITL 中断（等待人工审批） */
    INTERRUPT("interrupt"),
    /** 审批已接收 */
    APPROVAL_RECEIVED("approval_received"),
    /** 等待审批中 */
    AWAITING_APPROVAL("awaiting_approval");

    private final String value;

    AgentEventType(String value) {
        this.value = value;
    }
}
