package com.hypersense.boot.agents.model.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.Range;

/**
 * 设计系统表单对象
 *
 * @author Claude
 * @since 2026/6/16
 */
@Schema(description = "设计系统表单对象")
@Data
public class DesignSystemForm {

    @Schema(description = "设计系统ID")
    private Long id;

    @Schema(description = "名称")
    @NotBlank(message = "名称不能为空")
    private String name;

    @Schema(description = "分类: personal-个人, official-官方")
    @NotBlank(message = "分类不能为空")
    private String category;

    @Schema(description = "类型: web-网页, app-移动应用")
    @NotBlank(message = "类型不能为空")
    private String type;

    @Schema(description = "品牌规范(JSON)")
    private String brandSpec;

    @Schema(description = "代码规范(JSON)")
    private String codeSpec;

    @Schema(description = "资产(JSON)")
    private String assets;

    @Schema(description = "发布状态: 1-已发布, 0-草稿")
    @Range(max = 1, min = 0, message = "发布状态值不正确")
    private Integer publishStatus;

}
