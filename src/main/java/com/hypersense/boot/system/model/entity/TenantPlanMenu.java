package com.hypersense.boot.system.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 租户套餐菜单关联实体
 *
 * @author Ray Hao
 * @since 4.0.0
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("sys_tenant_plan_menu")
public class TenantPlanMenu {

    /**
     * 套餐ID
     */
    private Long planId;

    /**
     * 菜单ID
     */
    private Long menuId;
}
