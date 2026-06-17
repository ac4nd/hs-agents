package com.hypersense.boot.agents.model.query;

import com.hypersense.boot.common.base.BaseQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 设计系统分页查询对象
 *
 * @author Claude
 * @since 2026/6/16
 */
@Schema(description = "设计系统分页查询对象")
@Data
public class DesignSystemQuery extends BaseQuery {

    @Schema(description = "关键字(名称)")
    private String keywords;

    @Schema(description = "分类: personal-个人, official-官方")
    private String category;

    @Schema(description = "类型: web-网页, app-移动应用")
    private String type;

    @Schema(description = "发布状态")
    private Integer publishStatus;

}
