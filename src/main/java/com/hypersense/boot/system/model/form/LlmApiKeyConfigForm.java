package com.hypersense.boot.system.model.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Schema(description = "系统API-KEY配置表单")
public class LlmApiKeyConfigForm {
    @Schema(description = "ID")
    private Long id;

    @NotBlank(message = "API-KEY名称不能为空")
    @Schema(description = "API-KEY名称")
    private String keyName;

    @NotNull(message = "厂商配置ID不能为空")
    @Schema(description = "关联厂商配置ID")
    private Long vendorConfigId;

    @Schema(description = "每分钟请求限制")
    private Integer rateLimitRpm;

    @Schema(description = "每日Token消耗限制")
    private Integer rateLimitTpd;

    @Schema(description = "每日最大Token数")
    private Long maxTokensPerDay;

    @Schema(description = "过期时间")
    private LocalDateTime expiresAt;

    @Schema(description = "状态(1-启用 0-禁用)")
    private Integer status;

    @Schema(description = "备注")
    private String remark;
}
