package com.hypersense.boot.system.model.query;

import com.hypersense.boot.common.base.BaseQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 设计体系配置分页查询对象
 *
 * @author Claude
 * @since 2026/6/24
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "设计体系配置查询对象")
public class DesignSystemConfigQuery extends BaseQuery {

    @Schema(description = "关键字（名称模糊匹配）")
    private String keywords;

    @Schema(description = "分类(personal/official)")
    private String category;

    @Schema(description = "类型(web/app)")
    private String type;

    @Schema(description = "发布状态(0-草稿 1-已发布)")
    private Integer publishStatus;
}
