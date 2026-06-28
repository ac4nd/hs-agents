package com.hypersense.boot.framework.agents.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * Agent 事件类型枚举（用于 SSE 推送）
 * <p>
 * JSON 序列化使用 {@link #value}（小写字符串），与前端事件路由保持一致。
 * </p>
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
    /** 文件已创建（file_write 成功落盘后通知前端刷新附件列表） */
    FILE_CREATED("file_created"),
    /**
     * 代码生成中（流式）
     * <p>ToolNode 调用流式 LLM 生成 file_write content 时，节流 200ms 推送累积片段，
     * 前端实时展示正在生成的代码 + 工作区 loading 动画。</p>
     * <p>data 字段：todoId / todoDescription / sessionId / delta（增量）/ accumulated（累积全文）</p>
     */
    CODE_STREAMING("code_streaming"),
    /** 工具调用错误（透传工具失败原因给前端） */
    TOOL_ERROR("tool_error"),
    /** 审计警告（Finalize 输出含编造路径等违规内容时通知前端） */
    AUDIT_WARNING("audit_warning"),
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

    /**
     * JSON 序列化：输出小写 value（如 "plan_created"），匹配前端 switch-case。
     */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * JSON 反序列化：兼容 value 或 name()。
     */
    @JsonCreator
    public static AgentEventType fromValue(String raw) {
        if (raw == null) return null;
        for (AgentEventType t : values()) {
            if (t.value.equalsIgnoreCase(raw) || t.name().equalsIgnoreCase(raw)) {
                return t;
            }
        }
        return null;
    }
}

