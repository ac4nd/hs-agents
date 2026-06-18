package com.hypersense.boot.system.model.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

/**
 * LLM 模型配置表单。
 * <p>
 * 参数限制策略：
 * <ul>
 *   <li>temperature / topP：OpenAI 兼容协议标准范围，所有厂商通用，做硬约束</li>
 *   <li>maxOutputTokens / contextWindowSize：各模型差异极大（GLM-4.7 上限 393216，
 *       DeepSeek-V3 上限 8192，GPT-4o 上限 16384），由填写者参照厂商文档，
 *       后端不做范围校验，避免限制过死或过松</li>
 * </ul>
 * </p>
 *
 * @author Claude
 * @since 2026/6/18
 */
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
    @Schema(description = "上下文窗口大小(token数)，按厂商实际填写")
    private Integer contextWindowSize;

    @NotNull(message = "最大输出Token数不能为空")
    @Schema(description = "最大输出Token数，按厂商实际填写（GLM-4=4096, GLM-4.7=393216, DeepSeek-V3=8192, GPT-4o=16384）")
    private Integer maxOutputTokens;

    @Schema(description = "模型能力标签(JSON数组)")
    private String modelCapabilities;

    @DecimalMin(value = "0.0", message = "temperature 最小为 0.0")
    @DecimalMax(value = "2.0", message = "temperature 最大为 2.0")
    @Schema(description = "默认温度参数，范围 [0.0, 2.0]，推荐 0.7")
    private BigDecimal temperature;

    @DecimalMin(value = "0.0", message = "topP 最小为 0.0")
    @DecimalMax(value = "1.0", message = "topP 最大为 1.0")
    @Schema(description = "默认Top-P参数，范围 [0.0, 1.0]，推荐 1.0")
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
