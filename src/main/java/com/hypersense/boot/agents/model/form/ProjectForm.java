package com.hypersense.boot.agents.model.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.Range;

/**
 * 项目表单对象
 *
 * @author Claude
 * @since 2026/6/16
 */
@Schema(description = "项目表单对象")
@Data
public class ProjectForm {

    @Schema(description = "项目ID")
    private Long id;

    @Schema(description = "项目名称")
    @NotBlank(message = "项目名称不能为空")
    private String name;

    @Schema(description = "项目描述")
    private String description;

    @Schema(description = "沙箱类型: local-sandbox-本地沙箱, remote-cloud-远程云端, third-party-api-第三方 API")
    @NotBlank(message = "沙箱类型不能为空")
    private String sandboxType;

    @Schema(description = "状态: 1-正常, 0-禁用")
    @Range(max = 1, min = 0, message = "状态值不正确")
    @NotNull(message = "状态不能为空")
    private Integer status;

}
