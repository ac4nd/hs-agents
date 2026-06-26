package com.hypersense.boot.system.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;

/**
 * 官方设计体系配置模板视图对象
 *
 * @author Claude
 * @since 2026/6/24
 */
@Getter
@Setter
@Schema(description = "官方设计体系配置模板视图对象")
public class DesignSystemConfigTemplateVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "模板ID")
    private Long id;

    @Schema(description = "模板名称")
    private String name;

    @Schema(description = "类型(web/app)")
    private String type;

    @Schema(description = "分类显示标签")
    private String categoryLabel;

    @Schema(description = "品牌规范 JSON 字符串")
    private String brandSpec;

    @Schema(description = "代码规范 JSON 字符串")
    private String codeSpec;

    @Schema(description = "素材库 JSON 字符串")
    private String assets;

    @Schema(description = "缩略图 URL")
    private String thumbnailUrl;
}
