package com.hypersense.boot.system.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Schema(description = "LLM用量日志VO")
public class LlmUsageLogVO {
    @Schema(description = "ID")
    private Long id;

    @Schema(description = "关联系统API-KEY配置ID")
    private Long apiKeyConfigId;

    @Schema(description = "API-KEY名称")
    private String apiKeyName;

    @Schema(description = "关联厂商配置ID")
    private Long vendorConfigId;

    @Schema(description = "厂商名称")
    private String vendorName;

    @Schema(description = "模型名称")
    private String modelName;

    @Schema(description = "输入Token数")
    private Integer inputTokens;

    @Schema(description = "输出Token数")
    private Integer outputTokens;

    @Schema(description = "本次费用")
    private BigDecimal cost;

    @Schema(description = "请求追踪ID")
    private String requestId;

    @Schema(description = "调用用户ID")
    private Long userId;

    @Schema(description = "响应耗时(毫秒)")
    private Integer durationMs;

    @Schema(description = "调用状态(1-成功 0-失败)")
    private Integer status;

    @Schema(description = "失败原因")
    private String errorMessage;

    @Schema(description = "调用时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
