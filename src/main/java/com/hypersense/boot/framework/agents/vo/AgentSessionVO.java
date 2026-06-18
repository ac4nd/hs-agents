package com.hypersense.boot.framework.agents.vo;

import com.hypersense.boot.framework.agents.enums.SessionStatus;
import com.hypersense.boot.framework.agents.form.ApprovalRequest;
import com.hypersense.boot.framework.agents.model.InterruptContext;
import com.hypersense.boot.framework.agents.model.TodoItem;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    /**
     * 当前会话绑定的 LLM 模型配置 ID（sys_llm_model_config.id）。
     * <p>
     * 为 null 时回退兜底 ChatModel（AgentAutoConfiguration 注入的单例）。
     * 切换模型由 {@code switch-model} 接口或 streamExecute 的 modelConfigId 参数触发，
     * 同步 invalidate graphCache + 重建图实例。
     * </p>
     */
    @Schema(description = "当前会话绑定的 LLM 模型配置 ID（sys_llm_model_config.id，null 时用默认）")
    @JsonProperty("modelConfigId")
    private Long modelConfigId;

    /**
     * 会话内对话历史（role: user/assistant，content: 文本）
     * <p>
     * 用于多轮对话上下文连续性。每次 streamExecute 完成后追加本轮 user 输入与 finalResponse。
     * 与 MemoryMiddleware 的 pgvector 长期记忆不同，本字段仅在同一 sessionId 内有效。
     * </p>
     */
    @Schema(description = "会话内对话历史")
    @JsonProperty("history")
    private List<ConversationMessage> history;

    /**
     * 已压缩的旧对话摘要（用于多轮上下文 token 控制）。
     * <p>
     * 当 history 滑窗溢出且累计待摘要条数达到阈值时，由 LLM 生成摘要覆盖此字段。
     * 渲染时会拼到 history 之前，让 LLM 看到早期对话要点而不必携带全部原文。
     * </p>
     */
    @Schema(description = "已压缩的旧对话摘要")
    @JsonProperty("historySummary")
    private String historySummary;

    /**
     * 待摘要的历史消息原文（达到阈值后合并到 historySummary）。
     * <p>
     * 滑窗溢出最早的 K 条会暂存于此；累计 ≥ summaryTriggerThreshold 时触发一次 LLM 摘要。
     * </p>
     */
    @Schema(description = "待摘要的历史消息原文")
    @JsonProperty("pendingSummarySource")
    private List<ConversationMessage> pendingSummarySource;

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

    /**
     * 会话内单条对话消息（用于多轮上下文）
     */
    @Schema(description = "会话内对话消息")
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConversationMessage {
        /** 角色：user / assistant */
        @Schema(description = "角色")
        private String role;
        /** 文本内容 */
        @Schema(description = "文本内容")
        private String content;
    }
}
