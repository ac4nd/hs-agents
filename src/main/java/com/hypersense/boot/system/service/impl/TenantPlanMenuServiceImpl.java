package com.hypersense.boot.system.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hypersense.boot.system.mapper.TenantPlanMenuMapper;
import com.hypersense.boot.system.model.entity.TenantPlanMenu;
import com.hypersense.boot.system.service.TenantPlanMenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 租户套餐菜单服务实现类
 *
 * @author Ray Hao
 * @since 4.0.0
 */
@Service
@RequiredArgsConstructor
public class TenantPlanMenuServiceImpl extends ServiceImpl<TenantPlanMenuMapper, TenantPlanMenu> implements TenantPlanMenuService {

    /**
     * 获取套餐菜单ID集合
     *
     * @param planId 套餐ID
     * @return 菜单ID集合
     */
    @Override
    public List<Long> listMenuIdsByPlan(Long planId) {
        if (planId == null) {
            return List.of();
        }
        return this.list(new LambdaQueryWrapper<TenantPlanMenu>()
                        .select(TenantPlanMenu::getMenuId)
                        .eq(TenantPlanMenu::getPlanId, planId))
                .stream()
                .map(TenantPlanMenu::getMenuId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 保存套餐菜单配置
     *
     * @param planId 套餐ID
     * @param menuIds 菜单ID集合
     */
    @Override
    public void savePlanMenus(Long planId, List<Long> menuIds) {
        if (planId == null) {
            return;
        }

        this.remove(new LambdaQueryWrapper<TenantPlanMenu>()
                .eq(TenantPlanMenu::getPlanId, planId));

        if (CollectionUtil.isNotEmpty(menuIds)) {
            List<TenantPlanMenu> planMenus = menuIds.stream()
                    .filter(Objects::nonNull)
                    .map(menuId -> new TenantPlanMenu(planId, menuId))
                    .collect(Collectors.toList());
            this.saveBatch(planMenus);
        }
    }
}
