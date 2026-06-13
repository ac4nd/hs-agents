package com.hypersense.boot.system.model.query;

import com.hypersense.boot.common.base.BaseQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "LLM用量日志分页查询")
public class LlmUsageLogQuery extends BaseQuery {
    @Schema(description = "模型名称")
    private String modelName;

    @Schema(description = "API-KEY配置ID")
    private Long apiKeyConfigId;

    @Schema(description = "厂商配置ID")
    private Long vendorConfigId;

    @Schema(description = "调用状态(1-成功 0-失败)")
    private Integer status;

    @Schema(description = "开始时间")
    private LocalDateTime startTime;

    @Schema(description = "结束时间")
    private LocalDateTime endTime;
}
