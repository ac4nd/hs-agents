package com.hypersense.boot.system.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hypersense.boot.common.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户设计体系配置实体
 *
 * <p>包含品牌规范、代码 Token 与素材库，区分个人体系与官方预设</p>
 *
 * @author Claude
 * @since 2026/6/24
 */
@TableName("sys_design_system_config")
@Getter
@Setter
@Schema(description = "设计体系配置实体")
public class DesignSystemConfig extends BaseEntity {

    /**
     * 所有者用户ID
     */
    @Schema(description = "所有者用户ID")
    private Long ownerUserId;

    /**
     * 设计体系名称
     */
    @Schema(description = "设计体系名称")
    private String name;

    /**
     * 分类(personal-个人体系/official-官方预设)
     */
    @Schema(description = "分类(personal/official)")
    private String category;

    /**
     * 类型(web-网页/app-应用)
     */
    @Schema(description = "类型(web/app)")
    private String type;

    /**
     * 品牌规范 JSON 字符串
     */
    @Schema(description = "品牌规范 v2.0：含 identity(primary/accent/neutral)、semantic、fonts、radius 等设计令牌")
    private String brandSpec;

    /**
     * 代码规范 JSON 字符串（design-tokens）
     */
    @Schema(description = "代码规范 JSON 字符串（design-tokens）")
    private String codeSpec;

    /**
     * 素材库 JSON 字符串
     */
    @Schema(description = "素材库 JSON 字符串")
    private String assets;

    /**
     * 发布状态(0-草稿 1-已发布)
     */
    @Schema(description = "发布状态(0-草稿 1-已发布)")
    private Integer publishStatus;

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
