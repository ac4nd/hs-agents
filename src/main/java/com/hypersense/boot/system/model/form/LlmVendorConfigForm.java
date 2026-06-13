package com.hypersense.boot.system.model.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Schema(description = "LLM厂商配置表单")
public class LlmVendorConfigForm {
    @Schema(description = "ID")
    private Long id;

    @NotBlank(message = "厂商名称不能为空")
    @Schema(description = "厂商名称")
    private String vendorName;

    @NotBlank(message = "厂商编码不能为空")
    @Schema(description = "厂商编码")
    private String vendorCode;

    @NotBlank(message = "配置键名不能为空")
    @Schema(description = "API-KEY配置键名(如:llm.vendor.zhipu.key-1)")
    private String configKey;

    @Schema(description = "是否为编码套餐(0-否 1-是)")
    private Integer isCodingPlan;

    @Schema(description = "接入标准(1-标准 2-高级 3-旗舰)")
    private Integer accessLevel;

    @NotBlank(message = "API基础地址不能为空")
    @Schema(description = "API基础地址")
    private String baseUrl;

    @Schema(description = "可用额度(NULL表示无限制)")
    private BigDecimal availableQuota;

    @Schema(description = "额度单位(CNY/USD/TOKENS)")
    private String quotaUnit;

    @Schema(description = "状态(1-启用 0-禁用)")
    private Integer status;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "备注")
    private String remark;
}
