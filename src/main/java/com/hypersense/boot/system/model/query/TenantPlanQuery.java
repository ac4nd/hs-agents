package com.hypersense.boot.system.model.query;

import com.hypersense.boot.common.base.BaseQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 租户套餐分页查询对象
 *
 * @author Ray Hao
 * @since 4.0.0
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "租户套餐分页查询对象")
public class TenantPlanQuery extends BaseQuery {

    @Schema(description = "关键字(套餐名称/套餐编码)")
    private String keywords;

    @Schema(description = "套餐状态(1-启用 0-停用)")
    private Integer status;
}
