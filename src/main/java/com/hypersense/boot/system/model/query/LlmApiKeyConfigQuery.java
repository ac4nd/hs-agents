package com.hypersense.boot.system.model.query;

import com.hypersense.boot.common.base.BaseQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "系统API-KEY配置分页查询")
public class LlmApiKeyConfigQuery extends BaseQuery {
    @Schema(description = "关键字(API-KEY名称)")
    private String keywords;

    @Schema(description = "厂商配置ID")
    private Long vendorConfigId;

    @Schema(description = "状态")
    private Integer status;
}
