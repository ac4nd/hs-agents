package com.hypersense.boot.agents.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hypersense.boot.common.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 设计系统实体
 *
 * @author Claude
 * @since 2026/6/16
 */
@TableName("sys_design_system")
@Getter
@Setter
@Schema(description = "设计系统实体")
public class DesignSystem extends BaseEntity {

    /**
     * 所属用户 ID
     */
    @Schema(description = "所属用户 ID")
    private Long ownerUserId;

    /**
     * 名称
     */
    @Schema(description = "名称")
    private String name;

    /**
     * 分类
     */
    @Schema(description = "分类: personal-个人, official-官方")
    private String category;

    /**
     * 类型
     */
    @Schema(description = "类型: web-网页, app-移动应用")
    private String type;

    /**
     * 品牌规范(JSON)
     */
    @Schema(description = "品牌规范(JSON)")
    private String brandSpec;

    /**
     * 代码规范(JSON)
     */
    @Schema(description = "代码规范(JSON)")
    private String codeSpec;

    /**
     * 资产(JSON)
     */
    @Schema(description = "资产(JSON)")
    private String assets;

    /**
     * 发布状态
     */
    @Schema(description = "发布状态: 1-已发布, 0-草稿")
    private Integer publishStatus;

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
