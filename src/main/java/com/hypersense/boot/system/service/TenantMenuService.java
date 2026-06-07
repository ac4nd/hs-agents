package com.hypersense.boot.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hypersense.boot.system.model.entity.TenantMenu;

import java.util.List;

/**
 * 租户菜单业务接口
 *
 * @author Ray.Hao
 * @since 3.0.0
 */
public interface TenantMenuService extends IService<TenantMenu> {

    /**
     * 获取租户可用菜单ID集合
     *
     * @param tenantId 租户ID
     * @return 菜单ID集合
     */
    List<Long> listMenuIdsByTenant(Long tenantId);

    /**
     * 保存租户菜单配置
     *
     * @param tenantId 租户ID
     * @param menuIds 菜单ID集合
     */
    void saveTenantMenus(Long tenantId, List<Long> menuIds);
}
