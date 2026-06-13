package com.hypersense.boot.system.model.query;

import com.hypersense.boot.common.base.BaseQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "LLM模型配置分页查询")
public class LlmModelConfigQuery extends BaseQuery {
    @Schema(description = "关键字(模型名称)")
    private String keywords;

    @Schema(description = "API-KEY配置ID")
    private Long apiKeyConfigId;

    @Schema(description = "状态")
    private Integer status;
}
