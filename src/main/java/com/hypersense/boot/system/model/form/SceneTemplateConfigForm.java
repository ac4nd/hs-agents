package com.hypersense.boot.system.model.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 场景模板配置表单对象
 *
 * @author Claude
 * @since 2026/6/24
 */
@Schema(description = "场景模板配置表单对象")
@Data
public class SceneTemplateConfigForm {

    @Schema(description = "模板ID")
    private Long id;

    @Schema(description = "URL slug（目录名）")
    @NotBlank(message = "slug 不能为空")
    private String slug;

    @Schema(description = "模板名称")
    @NotBlank(message = "名称不能为空")
    private String name;

    @Schema(description = "一句话描述")
    private String tagline;

    @Schema(description = "分类(official/community)")
    private String category;

    @Schema(description = "UI显示分类")
    private String uiCategory;

    @Schema(description = "情绪风格 JSON")
    private String mood;

    @Schema(description = "调色板 JSON")
    private String palette;

    @Schema(description = "排版规范 JSON")
    private String typography;

    @Schema(description = "幻灯片页数")
    private Integer slideCount;

    @Schema(description = "源文件路径或仓库地址")
    private String sourceUrl;

    @Schema(description = "HTML 模板的 MinIO 访问 URL")
    private String htmlUrl;

    @Schema(description = "缩略图 URL")
    private String thumbnailUrl;

    @Schema(description = "是否官方: 1-是, 0-否")
    private Integer isOfficial;

    @Schema(description = "是否发布: 1-已发布, 0-未发布")
    private Integer isPublished;

    @Schema(description = "排序值（升序）")
    private Integer sort;

}
