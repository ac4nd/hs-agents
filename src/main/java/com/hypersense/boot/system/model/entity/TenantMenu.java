package com.hypersense.boot.system.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 租户菜单关联表
 *
 * @author Ray.Hao
 * @since 3.0.0
 */
@TableName("sys_tenant_menu")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TenantMenu {

    /**
     * 租户ID
     */
    private Long tenantId;

    /**
     * 菜单ID
     */
    private Long menuId;
}
