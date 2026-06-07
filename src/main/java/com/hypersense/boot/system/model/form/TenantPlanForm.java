package com.hypersense.boot.system.model.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.Range;

/**
 * 租户套餐表单对象
 *
 * @author Ray Hao
 * @since 4.0.0
 */
@Data
@Schema(description = "租户套餐表单")
public class TenantPlanForm {

    @Schema(description = "套餐ID")
    private Long id;

    @Schema(description = "套餐名称")
    @NotBlank(message = "套餐名称不能为空")
    private String name;

    @Schema(description = "套餐编码")
    @NotBlank(message = "套餐编码不能为空")
    private String code;

    @Schema(description = "状态(1-启用 0-停用)")
    @Range(max = 1, min = 0, message = "套餐状态不正确")
    private Integer status;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "备注")
    private String remark;
}
