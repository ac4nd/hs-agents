package com.hypersense.boot.framework.agents.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 中断上下文
 * <p>
 * HITL 中断时推送给前端的审批上下文信息，
 * 包含触发中断的节点、当前 TODO、摘要描述等。
 * </p>
 *
 * @author Claude
 * @since 2026/5/23
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterruptContext {

    /** 触发中断的节点名称（如 "tool"、"delegate"） */
    private String nodeName;

    /** 当前正在执行的 TODO（可为 null） */
    private TodoItem currentTodo;

    /** 中断原因摘要（如 "即将执行工具: run_command"） */
    private String summary;

    /** 待审批的操作描述（如工具调用参数） */
    private String pendingAction;

    /** 会话 ID */
    private String sessionId;
}
