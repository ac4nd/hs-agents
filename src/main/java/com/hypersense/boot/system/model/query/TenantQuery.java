package com.hypersense.boot.system.model.query;

import com.hypersense.boot.common.base.BaseQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "租户分页查询对象")
public class TenantQuery extends BaseQuery {

    @Schema(description = "关键字(租户名称/租户编码/域名)")
    private String keywords;

    @Schema(description = "租户状态(1-正常 0-禁用)")
    private Integer status;
}
