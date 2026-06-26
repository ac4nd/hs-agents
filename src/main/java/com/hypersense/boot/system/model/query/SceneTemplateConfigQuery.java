package com.hypersense.boot.system.model.query;

import com.hypersense.boot.common.base.BaseQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 场景模板配置分页查询对象
 *
 * @author Claude
 * @since 2026/6/24
 */
@Schema(description = "场景模板配置分页查询对象")
@Data
public class SceneTemplateConfigQuery extends BaseQuery {

    @Schema(description = "关键字(名称/slug)")
    private String keywords;

    @Schema(description = "分类(official/community)")
    private String category;

    @Schema(description = "UI显示分类（前端分类筛选）")
    private String uiCategory;

    @Schema(description = "是否官方: 1-是, 0-否")
    private Integer isOfficial;

    @Schema(description = "是否发布: 1-已发布, 0-未发布（管理端筛选）")
    private Integer isPublished;

    @Schema(description = "是否查看全部（管理员传 true 跳过可见性过滤）")
    private Boolean showAll;

}
