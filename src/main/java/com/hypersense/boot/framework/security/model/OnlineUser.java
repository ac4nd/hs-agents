package com.hypersense.boot.framework.security.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

/**
 * 在线用户信息
 *
 * @author Ray
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnlineUser implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 部门ID
     */
    private Long deptId;

    /**
     * 数据权限列表
     */
    private List<RoleDataScope> dataScopes;

    /**
     * 租户ID
     */
    private Long tenantId;

    /**
     * 能否切换租户
     */
    private Boolean canSwitchTenant;

    /**
     * 角色集合
     */
    private Set<String> roles;

}
