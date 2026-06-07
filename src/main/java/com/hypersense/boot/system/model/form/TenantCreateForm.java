package com.hypersense.boot.system.model.form;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "新增租户表单")
@Data
public class TenantCreateForm {

    @Schema(description = "租户名称")
    @NotBlank(message = "租户名称不能为空")
    private String name;

    @Schema(description = "租户编码")
    @NotBlank(message = "租户编码不能为空")
    private String code;

    @Schema(description = "联系人姓名")
    private String contactName;

    @Schema(description = "联系人电话")
    private String contactPhone;

    @Schema(description = "联系人邮箱")
    private String contactEmail;

    @Schema(description = "租户域名")
    private String domain;

    @Schema(description = "租户Logo")
    private String logo;

    @Schema(description = "套餐ID")
    @NotNull(message = "租户套餐不能为空")
    private Long planId;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "过期时间（NULL表示永不过期）")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireTime;

    @Schema(description = "租户管理员登录名（为空则系统生成）")
    private String adminUsername;
}
