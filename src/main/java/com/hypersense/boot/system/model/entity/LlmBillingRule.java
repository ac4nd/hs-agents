package com.hypersense.boot.system.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("sys_llm_billing_rule")
@Data
@Schema(description = "LLM计费规则实体")
public class LlmBillingRule implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "规则名称")
    private String ruleName;

    @Schema(description = "关联厂商配置ID")
    private Long vendorConfigId;

    @Schema(description = "模型名称")
    private String modelName;

    @Schema(description = "输入价格(每单位token)")
    private BigDecimal inputPrice;

    @Schema(description = "输出价格(每单位token)")
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

    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
