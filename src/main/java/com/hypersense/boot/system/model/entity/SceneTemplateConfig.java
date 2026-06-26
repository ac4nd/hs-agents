package com.hypersense.boot.system.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hypersense.boot.common.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 场景模板配置实体
 *
 * <p>HTML 设计模板的元数据，支持官方模板与用户自定义模板</p>
 *
 * @author Claude
 * @since 2026/6/24
 */
@TableName("sys_scene_template_config")
@Getter
@Setter
@Schema(description = "场景模板配置实体")
public class SceneTemplateConfig extends BaseEntity {

    /**
     * 所属用户 ID（官方模板为 0）
     */
    @Schema(description = "所属用户 ID")
    private Long ownerUserId;

    /**
     * URL slug（目录名）
     */
    @Schema(description = "URL slug（目录名）")
    private String slug;

    /**
     * 模板名称
     */
    @Schema(description = "模板名称")
    private String name;

    /**
     * 一句话描述
     */
    @Schema(description = "一句话描述")
    private String tagline;

    /**
     * 分类(official/community)
     */
    @Schema(description = "分类(official/community)")
    private String category;

    /**
     * UI显示分类（从 SKILL.md od.scenario 派生，用于前端分类筛选）
     */
    @Schema(description = "UI显示分类")
    private String uiCategory;

    /**
     * 情绪风格 JSON
     */
    @Schema(description = "情绪风格 JSON")
    private String mood;

    /**
     * 调色板 JSON
     */
    @Schema(description = "调色板 JSON")
    private String palette;

    /**
     * 排版规范 JSON
     */
    @Schema(description = "排版规范 JSON")
    private String typography;

    /**
     * 幻灯片页数
     */
    @Schema(description = "幻灯片页数")
    private Integer slideCount;

    /**
     * 源文件路径或仓库地址
     */
    @Schema(description = "源文件路径或仓库地址")
    private String sourceUrl;

    /**
     * HTML 模板的 MinIO 访问 URL
     */
    @Schema(description = "HTML 模板的 MinIO 访问 URL")
    private String htmlUrl;

    /**
     * 缩略图 URL
     */
    @Schema(description = "缩略图 URL")
    private String thumbnailUrl;

    /**
     * 是否官方(1-是 0-否)
     */
    @Schema(description = "是否官方: 1-是, 0-否")
    private Integer isOfficial;

    /**
     * 是否发布(1-已发布 0-未发布)；发布后对所有用户可见
     */
    @Schema(description = "是否发布: 1-已发布, 0-未发布")
    private Integer isPublished;

    /**
     * 排序值（升序）
     */
    @Schema(description = "排序值（升序）")
    private Integer sort;

    /**
     * 创建人 ID
     */
    @Schema(description = "创建人 ID")
    private Long createBy;

    /**
     * 更新人 ID
     */
    @Schema(description = "更新人 ID")
    private Long updateBy;

    /**
     * 是否删除(0-否 1-是)
     */
    @Schema(description = "是否删除: 0-否, 1-是")
    private Integer isDeleted;

}
