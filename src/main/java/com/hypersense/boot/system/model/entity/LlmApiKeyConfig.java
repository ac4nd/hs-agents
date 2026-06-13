package com.hypersense.boot.system.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.hypersense.boot.common.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("sys_llm_api_key_config")
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "系统API-KEY配置实体")
public class LlmApiKeyConfig extends BaseEntity {

    @Schema(description = "API-KEY名称")
    private String keyName;

    @Schema(description = "系统生成的签名密钥")
    @TableField(updateStrategy = FieldStrategy.NOT_NULL)
    private String secret;

    @Schema(description = "关联厂商配置ID(容量池)")
    private Long vendorConfigId;

    @Schema(description = "每分钟请求限制")
    private Integer rateLimitRpm;

    @Schema(description = "每日Token消耗限制")
    private Integer rateLimitTpd;

    @Schema(description = "每日最大Token数")
    private Long maxTokensPerDay;

    @Schema(description = "已使用Token数")
    @TableField(updateStrategy = FieldStrategy.NOT_NULL)
    private Long usedTokens;

    @Schema(description = "累计费用")
    @TableField(updateStrategy = FieldStrategy.NOT_NULL)
    private BigDecimal totalCost;

    @Schema(description = "过期时间")
    private LocalDateTime expiresAt;

    @Schema(description = "状态(1-启用 0-禁用)")
    private Integer status;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建人ID")
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    @Schema(description = "修改人ID")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    @Schema(description = "逻辑删除标识")
    @TableLogic
    private Integer isDeleted;
}
