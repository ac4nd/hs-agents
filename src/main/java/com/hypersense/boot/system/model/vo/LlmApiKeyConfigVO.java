package com.hypersense.boot.system.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Schema(description = "系统API-KEY配置VO")
public class LlmApiKeyConfigVO {
    @Schema(description = "ID")
    private Long id;

    @Schema(description = "API-KEY名称")
    private String keyName;

    @Schema(description = "签名密钥")
    private String secret;

    @Schema(description = "关联厂商配置ID")
    private Long vendorConfigId;

    @Schema(description = "厂商名称")
    private String vendorName;

    @Schema(description = "厂商编码")
    private String vendorCode;

    @Schema(description = "每分钟请求限制")
    private Integer rateLimitRpm;

    @Schema(description = "每日Token消耗限制")
    private Integer rateLimitTpd;

    @Schema(description = "每日最大Token数")
    private Long maxTokensPerDay;

    @Schema(description = "已使用Token数")
    private Long usedTokens;

    @Schema(description = "累计费用")
    private BigDecimal totalCost;

    @Schema(description = "过期时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expiresAt;

    @Schema(description = "状态")
    private Integer status;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
