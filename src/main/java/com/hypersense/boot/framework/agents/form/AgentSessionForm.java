package com.hypersense.boot.framework.agents.form;

import com.hypersense.boot.framework.agents.model.SubAgentDefinition;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * Agent 会话创建表单
 *
 * @author Claude
 * @since 2026/5/15
 */
@Schema(description = "创建 Agent 会话请求")
@Data
public class AgentSessionForm {

    /** Agent 系统指令 */
    @Schema(description = "Agent 系统指令", example = "你是一个专业的 Java 开发助手")
    @NotBlank(message = "系统指令不能为空")
    private String instructions;

    /** 子 Agent 定义列表 */
    @Schema(description = "子 Agent 定义列表")
    private List<SubAgentDefinition> subAgents;

    /** 启用的工具列表 */
    @Schema(description = "启用的工具名称列表", example = "[\"file_write\", \"file_read\"]")
    private List<String> enabledTools;

    // ========== HITL 审批配置 ==========

    /** 是否启用 HITL 审批（默认 false） */
    @Schema(description = "是否启用 HITL 审批", example = "false")
    private Boolean hitlEnabled;

    /** HITL 中断节点列表（默认 ["tool"]） */
    @Schema(description = "HITL 中断节点列表，图执行到这些节点前暂停等待审批", example = "[\"tool\"]")
    private List<String> hitlInterruptNodes;

    /** LLM 模型配置 ID（sys_llm_model_config.id，可空）
     * <p>为空时按顺序回退：AgentTemplate.defaultModelConfigId → 租户默认模型 → 兜底单例</p> */
    @Schema(description = "LLM 模型配置 ID（sys_llm_model_config.id，可空）")
    private Long modelConfigId;
}
