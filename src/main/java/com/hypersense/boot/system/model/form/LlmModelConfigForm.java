package com.hypersense.boot.system.model.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Schema(description = "LLM模型配置表单")
public class LlmModelConfigForm {
    @Schema(description = "ID")
    private Long id;

    @NotNull(message = "API-KEY配置ID不能为空")
    @Schema(description = "关联系统API-KEY配置ID")
    private Long apiKeyConfigId;

    @NotBlank(message = "模型名称不能为空")
    @Schema(description = "模型名称")
    private String modelName;

    @Schema(description = "模型显示名称")
    private String modelDisplayName;

    @NotNull(message = "上下文窗口大小不能为空")
    @Schema(description = "上下文窗口大小(token数)")
    private Integer contextWindowSize;

    @NotNull(message = "最大输出Token数不能为空")
    @Schema(description = "最大输出Token数")
    private Integer maxOutputTokens;

    @Schema(description = "模型能力标签(JSON数组)")
    private String modelCapabilities;

    @Schema(description = "默认温度参数")
    private BigDecimal temperature;

    @Schema(description = "默认Top-P参数")
    private BigDecimal topP;

    @Schema(description = "是否启用流式输出(0-否 1-是)")
    private Integer isStreaming;

    @Schema(description = "状态(1-启用 0-禁用)")
    private Integer status;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "备注")
    private String remark;
}
