package com.hypersense.boot.system.model.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;

/**
 * 设计体系配置表单对象
 *
 * @author Claude
 * @since 2026/6/24
 */
@Getter
@Setter
@Schema(description = "设计体系配置表单对象")
public class DesignSystemConfigForm implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "设计体系配置ID（更新时必传）")
    private Long id;

    @Schema(description = "设计体系名称")
    @NotBlank(message = "名称不能为空")
    @Size(max = 255, message = "名称长度不能超过 255 个字符")
    private String name;

    @Schema(description = "分类(personal/official)")
    private String category;

    @Schema(description = "类型(web/app)")
    private String type;

    @Schema(description = "品牌规范 JSON 字符串")
    private String brandSpec;

    @Schema(description = "代码规范 JSON 字符串")
    private String codeSpec;

    @Schema(description = "素材库 JSON 字符串")
    private String assets;

    @Schema(description = "发布状态(0-草稿 1-已发布)")
    private Integer publishStatus;
}
