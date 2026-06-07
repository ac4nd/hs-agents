package com.hypersense.boot.system.model.dto;

import lombok.Data;
import java.util.Set;

/**
 * 角色权限集合
 *
 * @author Ray.Hao
 * @since 3.0.0
 */
@Data
public class RolePermsDTO {

    private Long tenantId;

    /**
     * 角色编码
     */
    private String roleCode;

    /**
     * 权限集合
     */
    private Set<String> perms;
}
