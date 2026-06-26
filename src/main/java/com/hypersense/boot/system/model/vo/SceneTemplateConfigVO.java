package com.hypersense.boot.system.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 场景模板配置视图对象
 *
 * @author Claude
 * @since 2026/6/24
 */
@Schema(description = "场景模板配置视图对象")
@Data
public class SceneTemplateConfigVO {

    @Schema(description = "模板ID")
    private Long id;

    @Schema(description = "所属用户ID")
    private Long ownerUserId;

    @Schema(description = "URL slug（目录名）")
    private String slug;

    @Schema(description = "模板名称")
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

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

}
