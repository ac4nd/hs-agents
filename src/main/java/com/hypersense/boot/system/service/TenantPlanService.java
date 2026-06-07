package com.hypersense.boot.system.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hypersense.boot.common.model.Option;
import com.hypersense.boot.system.model.entity.TenantPlan;
import com.hypersense.boot.system.model.form.TenantPlanForm;
import com.hypersense.boot.system.model.query.TenantPlanQuery;
import com.hypersense.boot.system.model.vo.TenantPlanPageVO;

import java.util.List;

/**
 * 租户套餐业务接口
 *
 * @author Ray Hao
 * @since 4.0.0
 */
public interface TenantPlanService extends IService<TenantPlan> {

    /**
     * 租户套餐分页列表
     *
     * @param queryParams 查询参数
     * @return 租户套餐分页列表
     */
    IPage<TenantPlanPageVO> getTenantPlanPage(TenantPlanQuery queryParams);

    /**
     * 租户套餐下拉列表
     *
     * @return 套餐下拉列表
     */
    List<Option<Long>> listTenantPlanOptions();

    /**
     * 获取套餐表单数据
     *
     * @param planId 套餐ID
     * @return 套餐表单数据
     */
    TenantPlanForm getTenantPlanForm(Long planId);

    /**
     * 新增套餐
     *
     * @param formData 套餐表单
     * @return 是否新增成功
     */
    boolean saveTenantPlan(TenantPlanForm formData);

    /**
     * 更新套餐
     *
     * @param planId 套餐ID
     * @param formData 套餐表单
     * @return 是否更新成功
     */
    boolean updateTenantPlan(Long planId, TenantPlanForm formData);

    /**
     * 删除套餐
     *
     * @param ids 套餐ID，多个以英文逗号(,)分割
     */
    void deleteTenantPlans(String ids);

    /**
     * 获取套餐菜单ID集合
     *
     * @param planId 套餐ID
     * @return 菜单ID集合
     */
    List<Long> getTenantPlanMenuIds(Long planId);

    /**
     * 更新套餐菜单
     *
     * @param planId 套餐ID
     * @param menuIds 菜单ID集合
     */
    void updateTenantPlanMenus(Long planId, List<Long> menuIds);
}
