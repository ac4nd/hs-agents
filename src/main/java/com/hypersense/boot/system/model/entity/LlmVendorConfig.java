package com.hypersense.boot.system.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("sys_llm_vendor_config")
@Data
@Schema(description = "LLM厂商配置实体")
public class LlmVendorConfig implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "厂商名称")
    private String vendorName;

    @Schema(description = "厂商编码")
    private String vendorCode;

    @Schema(description = "API-KEY配置键名")
    private String configKey;

    @Schema(description = "是否为编码套餐(0-否 1-是)")
    private Integer isCodingPlan;

    @Schema(description = "接入标准(1-标准 2-高级 3-旗舰)")
    private Integer accessLevel;

    @Schema(description = "API基础地址")
    private String baseUrl;

    @Schema(description = "可用额度(NULL表示无限制)")
    private BigDecimal availableQuota;

    @Schema(description = "已用额度")
    private BigDecimal usedQuota;

    @Schema(description = "额度单位(CNY/USD/TOKENS)")
    private String quotaUnit;

    @Schema(description = "状态(1-启用 0-禁用)")
    private Integer status;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建人ID")
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "修改人ID")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @Schema(description = "逻辑删除标识(0-未删除 1-已删除)")
    @TableLogic
    private Integer isDeleted;
}
