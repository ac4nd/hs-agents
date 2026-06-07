package com.hypersense.boot.framework.security.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 角色数据权限
 *
 * @author Ray
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoleDataScope implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 角色编码
     */
    private String roleCode;

    /**
     * 数据权限范围
     */
    private Integer dataScope;

    /**
     * 自定义部门ID列表
     */
    private List<Long> customDeptIds;

}
