package com.hypersense.boot.agents.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设计系统视图对象
 *
 * @author Claude
 * @since 2026/6/16
 */
@Schema(description = "设计系统视图对象")
@Data
public class DesignSystemVO {

    @Schema(description = "设计系统ID")
    private Long id;

    @Schema(description = "所属用户ID")
    private Long ownerUserId;

    @Schema(description = "所属用户名")
    private String ownerUserName;

    @Schema(description = "名称")
    private String name;

    @Schema(description = "分类")
    private String category;

    @Schema(description = "分类标签")
    private String categoryLabel;

    @Schema(description = "类型")
    private String type;

    @Schema(description = "类型标签")
    private String typeLabel;

    @Schema(description = "品牌规范(JSON)")
    private String brandSpec;

    @Schema(description = "代码规范(JSON)")
    private String codeSpec;

    @Schema(description = "资产(JSON)")
    private String assets;

    @Schema(description = "发布状态")
    private Integer publishStatus;

    @Schema(description = "发布状态标签")
    private String publishStatusLabel;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

}
