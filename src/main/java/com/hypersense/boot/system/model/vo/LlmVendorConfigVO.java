package com.hypersense.boot.system.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Schema(description = "LLM厂商配置VO")
public class LlmVendorConfigVO {
    @Schema(description = "ID")
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

    @Schema(description = "可用额度")
    private BigDecimal availableQuota;

    @Schema(description = "已用额度")
    private BigDecimal usedQuota;

    @Schema(description = "额度单位")
    private String quotaUnit;

    @Schema(description = "状态(1-启用 0-禁用)")
    private Integer status;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
