package com.hypersense.boot.system.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Schema(description = "LLM计费规则VO")
public class LlmBillingRuleVO {
    @Schema(description = "ID")
    private Long id;

    @Schema(description = "规则名称")
    private String ruleName;

    @Schema(description = "关联厂商配置ID")
    private Long vendorConfigId;

    @Schema(description = "厂商名称")
    private String vendorName;

    @Schema(description = "厂商编码")
    private String vendorCode;

    @Schema(description = "模型名称")
    private String modelName;

    @Schema(description = "输入价格")
    private BigDecimal inputPrice;

    @Schema(description = "输出价格")
    private BigDecimal outputPrice;

    @Schema(description = "计价单位")
    private String priceUnit;

    @Schema(description = "币种")
    private String currency;

    @Schema(description = "计费类型(1-按Token 2-按次 3-套餐包)")
    private Integer billingType;

    @Schema(description = "状态")
    private Integer status;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
