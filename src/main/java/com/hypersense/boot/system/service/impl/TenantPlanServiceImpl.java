package com.hypersense.boot.system.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hypersense.boot.common.enums.StatusEnum;
import com.hypersense.boot.common.model.Option;
import com.hypersense.boot.system.converter.TenantPlanConverter;
import com.hypersense.boot.system.enums.MenuScopeEnum;
import com.hypersense.boot.system.mapper.MenuMapper;
import com.hypersense.boot.system.mapper.TenantPlanMapper;
import com.hypersense.boot.system.model.entity.TenantPlanMenu;
import com.hypersense.boot.system.model.entity.TenantPlan;
import com.hypersense.boot.system.model.entity.Menu;
import com.hypersense.boot.system.model.form.TenantPlanForm;
import com.hypersense.boot.system.model.query.TenantPlanQuery;
import com.hypersense.boot.system.model.vo.TenantPlanPageVO;
import com.hypersense.boot.system.service.TenantPlanMenuService;
import com.hypersense.boot.system.service.TenantPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 租户套餐服务实现类
 *
 * @author Ray Hao
 * @since 4.0.0
 */
@Service
@RequiredArgsConstructor
public class TenantPlanServiceImpl extends ServiceImpl<TenantPlanMapper, TenantPlan> implements TenantPlanService {

    private final TenantPlanConverter tenantPlanConverter;
    private final TenantPlanMenuService tenantPlanMenuService;
    private final MenuMapper menuMapper;

    /**
     * 租户套餐分页列表
     *
     * @param queryParams 查询参数
     * @return 租户套餐分页列表
     */
    @Override
    public IPage<TenantPlanPageVO> getTenantPlanPage(TenantPlanQuery queryParams) {
        String keywords = queryParams.getKeywords();
        Integer status = queryParams.getStatus();

        Page<TenantPlan> page = this.page(
                new Page<>(queryParams.getPageNum(), queryParams.getPageSize()),
                new LambdaQueryWrapper<TenantPlan>()
                        .and(StrUtil.isNotBlank(keywords), wrapper ->
                                wrapper.like(TenantPlan::getName, keywords)
                                        .or()
                                        .like(TenantPlan::getCode, keywords))
                        .eq(status != null, TenantPlan::getStatus, status)
                        .orderByAsc(TenantPlan::getSort)
                        .orderByDesc(TenantPlan::getCreateTime)
        );
        return tenantPlanConverter.toPageVo(page);
    }

    /**
     * 租户套餐下拉列表
     *
     * @return 套餐下拉列表
     */
    @Override
    public List<Option<Long>> listTenantPlanOptions() {
        List<TenantPlan> planList = this.list(new LambdaQueryWrapper<TenantPlan>()
                .eq(TenantPlan::getStatus, StatusEnum.ENABLE.getValue())
                .select(TenantPlan::getId, TenantPlan::getName)
                .orderByAsc(TenantPlan::getSort)
        );
        if (CollectionUtil.isEmpty(planList)) {
            return List.of();
        }
        return planList.stream()
                .map(plan -> new Option<>(plan.getId(), plan.getName()))
                .toList();
    }

    /**
     * 获取套餐表单数据
     *
     * @param planId 套餐ID
     * @return 套餐表单数据
     */
    @Override
    public TenantPlanForm getTenantPlanForm(Long planId) {
        TenantPlan entity = this.getById(planId);
        return tenantPlanConverter.toForm(entity);
    }

    /**
     * 新增套餐
     *
     * @param formData 套餐表单
     * @return 是否新增成功
     */
    @Override
    public boolean saveTenantPlan(TenantPlanForm formData) {
        long count = this.count(new LambdaQueryWrapper<TenantPlan>()
                .eq(TenantPlan::getCode, formData.getCode()));
        Assert.isTrue(count == 0, "套餐编码已存在");
        TenantPlan entity = tenantPlanConverter.toEntity(formData);
        return this.save(entity);
    }

    /**
     * 更新套餐
     *
     * @param planId 套餐ID
     * @param formData 套餐表单
     * @return 是否更新成功
     */
    @Override
    public boolean updateTenantPlan(Long planId, TenantPlanForm formData) {
        long count = this.count(new LambdaQueryWrapper<TenantPlan>()
                .eq(TenantPlan::getCode, formData.getCode())
                .ne(TenantPlan::getId, planId));
        Assert.isTrue(count == 0, "套餐编码已存在");
        TenantPlan entity = tenantPlanConverter.toEntity(formData);
        entity.setId(planId);
        return this.updateById(entity);
    }

    /**
     * 删除套餐
     *
     * @param ids 套餐ID，多个以英文逗号(,)分割
     */
    @Override
    @Transactional
    public void deleteTenantPlans(String ids) {
        Assert.isTrue(StrUtil.isNotBlank(ids), "删除的套餐ID不能为空");
        List<Long> planIds = Arrays.stream(ids.split(","))
                .map(Long::parseLong)
                .toList();

        if (CollectionUtil.isNotEmpty(planIds)) {
            tenantPlanMenuService.remove(new LambdaQueryWrapper<TenantPlanMenu>()
                    .in(TenantPlanMenu::getPlanId, planIds));
            this.removeByIds(planIds);
        }
    }

    /**
     * 获取套餐菜单ID集合
     *
     * @param planId 套餐ID
     * @return 菜单ID集合
     */
    @Override
    public List<Long> getTenantPlanMenuIds(Long planId) {
        return tenantPlanMenuService.listMenuIdsByPlan(planId);
    }

    /**
     * 更新套餐菜单
     *
     * @param planId 套餐ID
     * @param menuIds 菜单ID集合
     */
    @Override
    @Transactional
    public void updateTenantPlanMenus(Long planId, List<Long> menuIds) {
        // 套餐菜单仅允许配置业务菜单（scope=2）
        if (CollectionUtil.isNotEmpty(menuIds)) {
            List<Long> distinctMenuIds = menuIds.stream()
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            long allowedCount = menuMapper.selectCount(new LambdaQueryWrapper<Menu>()
                    .in(Menu::getId, distinctMenuIds)
                    .eq(Menu::getScope, MenuScopeEnum.TENANT.getValue()));
            Assert.isTrue(allowedCount == distinctMenuIds.size(), "套餐菜单只能选择业务菜单");
        }
        tenantPlanMenuService.savePlanMenus(planId, menuIds);
    }
}
