package com.hypersense.boot.system.model.query;

import com.hypersense.boot.common.base.BaseQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "LLM厂商配置分页查询")
public class LlmVendorConfigQuery extends BaseQuery {
    @Schema(description = "关键字(厂商名称/编码)")
    private String keywords;

    @Schema(description = "状态(1-启用 0-禁用)")
    private Integer status;
}
