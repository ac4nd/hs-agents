package com.hypersense.boot.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hypersense.boot.system.model.entity.TenantPlanMenu;

import java.util.List;

/**
 * 租户套餐菜单业务接口
 *
 * @author Ray Hao
 * @since 4.0.0
 */
public interface TenantPlanMenuService extends IService<TenantPlanMenu> {

    /**
     * 获取套餐菜单ID集合
     *
     * @param planId 套餐ID
     * @return 菜单ID集合
     */
    List<Long> listMenuIdsByPlan(Long planId);

    /**
     * 保存套餐菜单配置
     *
     * @param planId 套餐ID
     * @param menuIds 菜单ID集合
     */
    void savePlanMenus(Long planId, List<Long> menuIds);
}
