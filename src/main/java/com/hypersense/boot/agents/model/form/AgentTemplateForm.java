package com.hypersense.boot.agents.model.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Agent 模板表单对象
 *
 * @author Claude
 * @since 2026/6/16
 */
@Schema(description = "Agent 模板表单对象")
@Data
public class AgentTemplateForm {

    @Schema(description = "模板ID")
    private Long id;

    @Schema(description = "名称")
    @NotBlank(message = "名称不能为空")
    private String name;

    @Schema(description = "指令文本")
    @NotBlank(message = "指令不能为空")
    private String instructions;

    @Schema(description = "子 Agent 配置(JSON)")
    private String subAgents;

    @Schema(description = "启用的工具(JSON)")
    private String enabledTools;

    @Schema(description = "是否启用 HITL 审批")
    private Boolean hitlEnabled;

    @Schema(description = "沙箱配置(JSON)")
    private String sandboxConfig;

}
