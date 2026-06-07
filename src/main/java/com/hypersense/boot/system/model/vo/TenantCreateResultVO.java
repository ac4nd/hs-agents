package com.hypersense.boot.system.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

@Schema(description = "新增租户初始化结果")
@Data
public class TenantCreateResultVO implements Serializable {

    @Schema(description = "租户ID")
    private Long tenantId;

    @Schema(description = "租户编码")
    private String tenantCode;

    @Schema(description = "租户名称")
    private String tenantName;

    @Schema(description = "租户管理员用户名")
    private String adminUsername;

    @Schema(description = "租户管理员初始密码")
    private String adminInitialPassword;

    @Schema(description = "租户管理员角色编码")
    private String adminRoleCode;
}
