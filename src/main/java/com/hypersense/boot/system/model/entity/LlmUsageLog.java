package com.hypersense.boot.system.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.hypersense.boot.common.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;

@TableName("sys_llm_usage_log")
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "LLM调用用量日志实体")
public class LlmUsageLog extends BaseEntity {

    @Schema(description = "关联系统API-KEY配置ID")
    private Long apiKeyConfigId;

    @Schema(description = "关联厂商配置ID")
    private Long vendorConfigId;

    @Schema(description = "实际调用的模型名称")
    private String modelName;

    @Schema(description = "本次输入Token数")
    private Integer inputTokens;

    @Schema(description = "本次输出Token数")
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
}
