package com.hypersense.boot.system.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 设计体系配置视图对象
 *
 * <p>同时承担分页列表与详情两种用途。</p>
 *
 * @author Claude
 * @since 2026/6/24
 */
@Getter
@Setter
@Schema(description = "设计体系配置视图对象")
public class DesignSystemConfigPageVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "设计体系配置ID")
    private Long id;

    @Schema(description = "所有者用户ID")
    private Long ownerUserId;

    @Schema(description = "所有者用户名")
    private String ownerUserName;

    @Schema(description = "设计体系名称")
    private String name;

    @Schema(description = "分类(personal/official)")
    private String category;

    @Schema(description = "分类显示标签")
    private String categoryLabel;

    @Schema(description = "类型(web/app)")
    private String type;

    @Schema(description = "类型显示标签")
    private String typeLabel;

    @Schema(description = "品牌规范 JSON 字符串")
    private String brandSpec;

    @Schema(description = "代码规范 JSON 字符串")
    private String codeSpec;

    @Schema(description = "素材库 JSON 字符串")
    private String assets;

    @Schema(description = "发布状态(0-草稿 1-已发布)")
    private Integer publishStatus;

    @Schema(description = "发布状态显示标签")
    private String publishStatusLabel;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
