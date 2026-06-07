package com.hypersense.boot.framework.agents.vo;

import com.hypersense.boot.framework.agents.enums.SessionStatus;
import com.hypersense.boot.framework.agents.form.ApprovalRequest;
import com.hypersense.boot.framework.agents.model.InterruptContext;
import com.hypersense.boot.framework.agents.model.TodoItem;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Agent 会话视图对象
 *
 * @author Claude
 * @since 2026/5/15
 */
@Schema(description = "Agent 会话信息")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentSessionVO {

    /** 会话 ID */
    @Schema(description = "会话 ID")
    private String sessionId;

    /** 所属用户 ID（用于安全鉴权，防止越权访问） */
    @Schema(description = "所属用户 ID")
    private Long userId;

    /** 会话状态 */
    @Schema(description = "会话状态")
    private SessionStatus status;

    /** TODO 列表 */
    @Schema(description = "TODO 列表")
    private List<TodoItem> todos;

    /** 产物文件 */
    @Schema(description = "产物文件")
    private Map<String, String> files;

    /** 最终响应 */
    @Schema(description = "最终响应")
    private String finalResponse;

    /** 启用的工具列表 */
    @Schema(description = "启用的工具名称列表")
    private List<String> enabledTools;

    /** 创建时间 */
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    /** 更新时间 */
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;

    // ========== HITL 审批相关字段 ==========

    /** 是否启用 HITL 审批 */
    @Schema(description = "是否启用 HITL 审批")
    private Boolean hitlEnabled;

    /** HITL 中断节点列表 */
    @Schema(description = "HITL 中断节点列表")
    private List<String> hitlInterruptNodes;

    /** 触发中断的节点名 */
    @Schema(description = "触发中断的节点名")
    private String interruptedNode;

    /** 中断上下文 */
    @Schema(description = "中断上下文")
    private InterruptContext interruptContext;

    /** 待处理的审批请求 */
    @Schema(description = "待处理的审批请求")
    private ApprovalRequest pendingApproval;
}
