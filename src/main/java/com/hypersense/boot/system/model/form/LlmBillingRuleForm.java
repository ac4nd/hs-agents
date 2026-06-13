package com.hypersense.boot.system.model.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Schema(description = "LLM计费规则表单")
public class LlmBillingRuleForm {
    @Schema(description = "ID")
    private Long id;

    @NotBlank(message = "规则名称不能为空")
    @Schema(description = "规则名称")
    private String ruleName;

    @NotNull(message = "厂商配置ID不能为空")
    @Schema(description = "关联厂商配置ID")
    private Long vendorConfigId;

    @NotBlank(message = "模型名称不能为空")
    @Schema(description = "模型名称")
    private String modelName;

    @NotNull(message = "输入价格不能为空")
    @Schema(description = "输入价格")
    private BigDecimal inputPrice;

    @NotNull(message = "输出价格不能为空")
    @Schema(description = "输出价格")
    private BigDecimal outputPrice;

    @Schema(description = "计价单位")
    private String priceUnit;

    @Schema(description = "币种")
    private String currency;

    @Schema(description = "计费类型(1-按Token 2-按次 3-套餐包)")
    private Integer billingType;

    @Schema(description = "状态(1-启用 0-禁用)")
    private Integer status;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "备注")
    private String remark;
}
