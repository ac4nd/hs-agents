package com.hypersense.boot.system.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hypersense.boot.common.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 官方设计体系配置模板实体
 *
 * <p>跨租户共享的市场预设模板，用户可基于模板创建自己的设计体系</p>
 *
 * @author Claude
 * @since 2026/6/24
 */
@TableName("sys_design_system_config_template")
@Getter
@Setter
@Schema(description = "官方设计体系配置模板实体")
public class DesignSystemConfigTemplate extends BaseEntity {

    /**
     * 模板名称
     */
    @Schema(description = "模板名称")
    private String name;

    /**
     * 类型(web-网页/app-应用)
     */
    @Schema(description = "类型(web/app)")
    private String type;

    /**
     * 显示用分类标签（如 "AI & LLM"）
     */
    @Schema(description = "显示用分类标签")
    private String categoryLabel;

    /**
     * 品牌规范 JSON 字符串
     */
    @Schema(description = "品牌规范 JSON 字符串")
    private String brandSpec;

    /**
     * 代码规范 JSON 字符串
     */
    @Schema(description = "代码规范 JSON 字符串")
    private String codeSpec;

    /**
     * 素材库 JSON 字符串
     */
    @Schema(description = "素材库 JSON 字符串")
    private String assets;

    /**
     * 缩略图 URL
     */
    @Schema(description = "缩略图 URL")
    private String thumbnailUrl;

    /**
     * 排序值（升序）
     */
    @Schema(description = "排序值（升序）")
    private Integer sortOrder;

    /**
     * 是否启用(1-是 0-否)
     */
    @Schema(description = "是否启用(1-是 0-否)")
    @TableField("is_active")
    private Integer isActive;

    /**
     * 创建人ID
     */
    @Schema(description = "创建人ID")
    private Long createBy;

    /**
     * 更新人ID
     */
    @Schema(description = "更新人ID")
    private Long updateBy;

    /**
     * 逻辑删除标识(0-未删除 1-已删除)
     */
    @Schema(description = "逻辑删除标识(0-未删除 1-已删除)")
    private Integer isDeleted;
}
