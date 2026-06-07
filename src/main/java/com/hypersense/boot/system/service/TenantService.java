package com.hypersense.boot.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hypersense.boot.system.model.entity.Tenant;
import com.hypersense.boot.system.model.form.TenantCreateForm;
import com.hypersense.boot.system.model.form.TenantForm;
import com.hypersense.boot.system.model.query.TenantQuery;
import com.hypersense.boot.system.model.vo.TenantCreateResultVO;
import com.hypersense.boot.system.model.vo.TenantPageVO;
import com.hypersense.boot.system.model.vo.TenantVO;

import java.util.List;

/**
 * 租户服务接口
 *
 * @author Ray.Hao
 * @since 3.0.0
 */
public interface TenantService extends IService<Tenant> {

    boolean hasTenantSwitchPermission();

    /**
     * 获取用户可访问的租户列表
     * <p>
     * 通过用户名查询该用户在所有租户下的账户，返回可访问的租户列表
     * </p>
     *
     * @param userId 用户ID
     * @return 可访问的租户列表
     */
    List<TenantVO> getAccessibleTenants(Long userId);

    /**
     * 根据租户ID查询租户信息
     *
     * @param tenantId 租户ID
     * @return 租户信息
     */
    TenantVO getTenantById(Long tenantId);

    /**
     * 根据域名查询租户ID
     *
     * @param domain 域名
     * @return 租户ID
     */
    Long getTenantIdByDomain(String domain);

    /**
     * 新增租户并初始化默认数据
     *
     * @param form 新增租户表单
     * @return 初始化结果
     */
    TenantCreateResultVO createTenantWithInit(TenantCreateForm form);

    /**
     * 获取租户分页列表
     *
     * @param queryParams 分页查询参数
     * @return 租户分页列表
     */
    Page<TenantPageVO> getTenantPage(TenantQuery queryParams);

    /**
     * 获取租户表单数据
     *
     * @param tenantId 租户ID
     * @return 租户表单数据
     */
    TenantForm getTenantForm(Long tenantId);

    /**
     * 更新租户信息
     *
     * @param tenantId 租户ID
     * @param formData 租户表单数据
     * @return 更新结果
     */
    boolean updateTenant(Long tenantId, TenantForm formData);

    /**
     * 删除租户
     *
     * @param ids 租户ID列表
     */
    void deleteTenants(String ids);

    /**
     * 更新租户状态
     *
     * @param tenantId 租户ID
     * @param status   租户状态
     * @return 更新结果
     */
    boolean updateTenantStatus(Long tenantId, Integer status);

    /**
     * 获取租户菜单ID集合
     *
     * @param tenantId 租户ID
     * @return 菜单ID集合
     */
    List<Long> getTenantMenuIds(Long tenantId);

    /**
     * 更新租户菜单配置
     *
     * @param tenantId 租户ID
     * @param menuIds 菜单ID集合
     */
    void updateTenantMenus(Long tenantId, List<Long> menuIds);

    /**
     * 检查用户是否可以访问指定租户
     * <p>
     * 验证该用户名在目标租户下是否存在账户
     * </p>
     *
     * @param userId   用户ID
     * @param tenantId 租户ID
     * @return true-可访问，false-不可访问
     */
    boolean canAccessTenant(Long userId, Long tenantId);
}
