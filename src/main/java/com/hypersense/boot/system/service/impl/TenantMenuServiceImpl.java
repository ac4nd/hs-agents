package com.hypersense.boot.system.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hypersense.boot.system.mapper.TenantMenuMapper;
import com.hypersense.boot.system.model.entity.TenantMenu;
import com.hypersense.boot.system.service.TenantMenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 租户菜单服务实现类
 *
 * @author Ray.Hao
 * @since 3.0.0
 */
@Service
@RequiredArgsConstructor
public class TenantMenuServiceImpl extends ServiceImpl<TenantMenuMapper, TenantMenu> implements TenantMenuService {

    /**
     * 获取租户可用菜单ID集合
     *
     * @param tenantId 租户ID
     * @return 菜单ID集合
     */
    @Override
    public List<Long> listMenuIdsByTenant(Long tenantId) {
        if (tenantId == null) {
            return List.of();
        }
        return this.list(new LambdaQueryWrapper<TenantMenu>()
                        .select(TenantMenu::getMenuId)
                        .eq(TenantMenu::getTenantId, tenantId))
                .stream()
                .map(TenantMenu::getMenuId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 保存租户菜单配置
     *
     * @param tenantId 租户ID
     * @param menuIds 菜单ID集合
     */
    @Override
    public void saveTenantMenus(Long tenantId, List<Long> menuIds) {
        if (tenantId == null) {
            return;
        }

        this.remove(new LambdaQueryWrapper<TenantMenu>()
                .eq(TenantMenu::getTenantId, tenantId));

        if (CollectionUtil.isNotEmpty(menuIds)) {
            List<TenantMenu> tenantMenus = menuIds.stream()
                    .filter(Objects::nonNull)
                    .map(menuId -> new TenantMenu(tenantId, menuId))
                    .collect(Collectors.toList());
            this.saveBatch(tenantMenus);
        }
    }
}
