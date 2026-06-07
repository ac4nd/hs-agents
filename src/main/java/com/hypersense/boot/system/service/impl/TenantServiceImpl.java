package com.hypersense.boot.system.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hypersense.boot.common.constant.SystemConstants;
import com.hypersense.boot.framework.tenant.TenantContextHolder;
import com.hypersense.boot.common.exception.BusinessException;
import com.hypersense.boot.framework.security.util.SecurityUtils;
import com.hypersense.boot.system.converter.TenantConverter;
import com.hypersense.boot.system.mapper.TenantMapper;
import com.hypersense.boot.system.model.entity.Role;
import com.hypersense.boot.system.model.entity.Tenant;
import com.hypersense.boot.system.model.entity.User;
import com.hypersense.boot.system.model.form.DeptForm;
import com.hypersense.boot.system.model.form.RoleForm;
import com.hypersense.boot.system.model.form.TenantCreateForm;
import com.hypersense.boot.system.model.form.TenantForm;
import com.hypersense.boot.system.model.form.UserForm;
import com.hypersense.boot.system.model.query.TenantQuery;
import com.hypersense.boot.system.model.vo.TenantCreateResultVO;
import com.hypersense.boot.system.model.vo.TenantPageVO;
import com.hypersense.boot.system.model.vo.TenantVO;
import com.hypersense.boot.system.service.DeptService;
import com.hypersense.boot.system.service.RoleService;
import com.hypersense.boot.system.service.TenantMenuService;
import com.hypersense.boot.system.service.TenantPlanMenuService;
import com.hypersense.boot.system.service.TenantPlanService;
import com.hypersense.boot.system.service.TenantService;
import com.hypersense.boot.system.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 租户服务实现类
 *
 * @author Ray Hao
 * @since 4.0.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TenantServiceImpl extends ServiceImpl<TenantMapper, Tenant> implements TenantService {

    private final UserService userService;

    private final DeptService deptService;

    private final RoleService roleService;

    private final TenantPlanMenuService tenantPlanMenuService;

    private final TenantMenuService tenantMenuService;

    private final TenantPlanService tenantPlanService;

    private final TenantConverter tenantConverter;

    /**
     * 是否具备租户切换权限
     *
     * @return true 表示可切换租户
     */
    @Override
    public boolean hasTenantSwitchPermission() {
        return SecurityUtils.canSwitchTenant();
    }

    /**
     * 生成新租户管理员默认菜单ID列表
     *
     * @return 菜单ID列表
     */
    private List<Long> resolveNewTenantAdminMenuIds(Long planId) {
        return resolveTenantPlanMenuIds(planId);
    }

    /**
     * 获取租户套餐菜单边界ID集合
     * <p>
     * 如果套餐菜单未配置，则兜底为所有业务菜单
     * </p>
     *
     * @param planId 套餐ID
     * @return 菜单ID集合
     */
    private List<Long> resolveTenantPlanMenuIds(Long planId) {
        Long oldTenantId = TenantContextHolder.getTenantId();
        boolean oldIgnoreTenant = TenantContextHolder.isIgnoreTenant();

        try {
            TenantContextHolder.setIgnoreTenant(true);

            return tenantPlanMenuService.listMenuIdsByPlan(planId);
        } finally {
            TenantContextHolder.setIgnoreTenant(oldIgnoreTenant);
            if (oldTenantId != null) {
                TenantContextHolder.setTenantId(oldTenantId);
            }
        }
    }

    /**
     * 生成租户管理员角色编码
     *
     * @param tenantCode 租户编码
     * @return 角色编码
     */
    private String buildTenantAdminRoleCode(String tenantCode) {
        String code = StrUtil.toUpperCase(StrUtil.trimToEmpty(tenantCode));
        String raw = "TENANT_ADMIN_" + code;
        return StrUtil.maxLength(raw, 32);
    }

    /**
     * 生成租户管理员默认用户名
     *
     * @param tenantCode 租户编码
     * @return 用户名
     */
    private String buildTenantAdminUsername(String tenantCode) {
        String code = StrUtil.toLowerCase(StrUtil.trimToEmpty(tenantCode));
        String raw = "t_" + code + "_admin";
        return StrUtil.maxLength(raw, 64);
    }

    /**
     * 获取当前用户可访问的租户列表
     *
     * @param userId 用户ID
     * @return 租户列表
     */
    @Override
    public List<TenantVO> getAccessibleTenants(Long userId) {
        if (userId == null) {
            return List.of();
        }

        Long currentTenantId = TenantContextHolder.getTenantId();
        if (currentTenantId == null) {
            return List.of();
        }

        boolean canSwitchTenant = hasTenantSwitchPermission();

        // 无租户切换权限：仅允许访问当前租户
        if (!canSwitchTenant) {
            TenantVO tenant = getTenantById(currentTenantId);
            if (tenant == null || tenant.getStatus() == null || tenant.getStatus() != 1) {
                return List.of();
            }
            return List.of(tenant);
        }

        // 具备租户切换权限：可访问所有启用租户（由后端权限控制账号本身，避免同名账号跨租户风险）
        TenantContextHolder.setIgnoreTenant(true);
        try {
            List<Tenant> tenants = this.list(
                    new LambdaQueryWrapper<Tenant>()
                            .eq(Tenant::getStatus, 1)
                            .orderByAsc(Tenant::getCreateTime)
                            .orderByAsc(Tenant::getId)
            );

            return tenants.stream()
                    .map(tenant -> {
                        TenantVO vo = new TenantVO();
                        BeanUtils.copyProperties(tenant, vo);
                        vo.setIsDefault(SystemConstants.DEFAULT_TENANT_ID.equals(tenant.getId()));
                        return vo;
                    })
                    .collect(Collectors.toList());
        } finally {
            TenantContextHolder.setIgnoreTenant(false);
        }
    }

    /**
     * 获取租户信息
     * @param tenantId 租户ID
     * @return 租户信息
     */
    @Override
    public TenantVO getTenantById(Long tenantId) {
        Tenant tenant = this.getById(tenantId);
        if (tenant == null) {
            return null;
        }
        TenantVO vo = new TenantVO();
        BeanUtils.copyProperties(tenant, vo);
        return vo;
    }

    /**
     * 根据域名获取租户ID
     * @param domain 租户域名
     * @return 租户ID
     */
    @Override
    public Long getTenantIdByDomain(String domain) {
        Tenant tenant = this.getOne(
                new LambdaQueryWrapper<Tenant>()
                        .eq(Tenant::getDomain, domain)
                        .eq(Tenant::getStatus, 1)
                        .last("LIMIT 1")
        );
        return tenant != null ? tenant.getId() : null;
    }

    /**
     * 创建租户并初始化默认数据
     *
     * @param form 租户创建表单
     * @return 创建结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TenantCreateResultVO createTenantWithInit(TenantCreateForm form) {
        Assert.notNull(form, "请求参数不能为空");
        Assert.isTrue(hasTenantSwitchPermission(), "仅具备租户切换权限允许新增租户");

        String tenantName = StrUtil.trimToEmpty(form.getName());
        String tenantCode = StrUtil.toUpperCase(StrUtil.trimToEmpty(form.getCode()));

        Assert.isTrue(StrUtil.isNotBlank(tenantName), "租户名称不能为空");
        Assert.isTrue(StrUtil.isNotBlank(tenantCode), "租户编码不能为空");

        long tenantCodeCount = this.count(new LambdaQueryWrapper<Tenant>().eq(Tenant::getCode, tenantCode));
        Assert.isTrue(tenantCodeCount == 0, "租户编码已存在");

        if (StrUtil.isNotBlank(form.getDomain())) {
            long domainCount = this.count(new LambdaQueryWrapper<Tenant>().eq(Tenant::getDomain, form.getDomain()));
            Assert.isTrue(domainCount == 0, "租户域名已存在");
        }

        Long planId = form.getPlanId();
        Assert.notNull(planId, "租户套餐不能为空");
        Assert.isTrue(tenantPlanService.getById(planId) != null, "租户套餐不存在");

        Tenant tenant = new Tenant();
        tenant.setName(tenantName);
        tenant.setCode(tenantCode);
        tenant.setContactName(form.getContactName());
        tenant.setContactPhone(form.getContactPhone());
        tenant.setContactEmail(form.getContactEmail());
        tenant.setDomain(form.getDomain());
        tenant.setLogo(form.getLogo());
        tenant.setPlanId(planId);
        tenant.setRemark(form.getRemark());
        tenant.setExpireTime(form.getExpireTime());
        tenant.setStatus(1);

        boolean saved = this.save(tenant);
        Assert.isTrue(saved, "租户创建失败");
        Assert.notNull(tenant.getId(), "租户创建失败：未生成租户ID");

        Long newTenantId = tenant.getId();

        Long oldTenantId = TenantContextHolder.getTenantId();
        boolean oldIgnoreTenant = TenantContextHolder.isIgnoreTenant();
        try {
            TenantContextHolder.setIgnoreTenant(false);
            TenantContextHolder.setTenantId(newTenantId);

            // 1) 默认部门
            DeptForm deptForm = new DeptForm();
            deptForm.setParentId(SystemConstants.ROOT_NODE_ID);
            deptForm.setName(tenantName);
            deptForm.setCode(tenantCode);
            deptForm.setStatus(1);
            deptForm.setSort(1);
            Long deptId = deptService.saveDept(deptForm);

            // 2) 租户管理员角色（code/name 全局唯一）
            String roleCode = buildTenantAdminRoleCode(tenantCode);
            String roleName = StrUtil.maxLength("租户管理员-" + tenantCode, 64);

            RoleForm roleForm = new RoleForm();
            roleForm.setCode(roleCode);
            roleForm.setName(roleName);
            roleForm.setSort(1);
            roleForm.setStatus(1);
            roleForm.setDataScope(1);
            boolean roleSaved = roleService.saveRole(roleForm);
            Assert.isTrue(roleSaved, "租户管理员角色创建失败");

            Role role = roleService.getOne(new LambdaQueryWrapper<Role>()
                    .eq(Role::getCode, roleCode)
                    .eq(Role::getIsDeleted, 0)
                    .last("LIMIT 1"));
            Assert.notNull(role, "租户管理员角色创建失败：未查询到角色");
            Assert.notNull(role.getId(), "租户管理员角色创建失败：未生成角色ID");

            // 3) 租户管理员用户（用户名不使用 admin/root）
            String adminUsername = StrUtil.trimToEmpty(form.getAdminUsername());
            if (StrUtil.isBlank(adminUsername)) {
                adminUsername = buildTenantAdminUsername(tenantCode);
            }

            if (SystemConstants.PLATFORM_ROOT_USERNAME.equalsIgnoreCase(adminUsername)) {
                throw new BusinessException("租户管理员用户名不允许使用平台保留账号");
            }

            UserForm userForm = new UserForm();
            userForm.setUsername(adminUsername);
            userForm.setNickname(StrUtil.maxLength(tenantName + "管理员", 64));
            userForm.setStatus(1);
            userForm.setDeptId(deptId);
            userForm.setRoleIds(List.of(role.getId()));
            boolean userSaved = userService.saveUser(userForm);
            Assert.isTrue(userSaved, "租户管理员用户创建失败");

            // 4) 租户菜单初始化 + 角色菜单授权（租户侧权限）
            List<Long> menuIds = resolveNewTenantAdminMenuIds(planId);
            tenantMenuService.saveTenantMenus(newTenantId, menuIds);
            roleService.assignMenusToRole(role.getId(), menuIds);

            TenantCreateResultVO resultVo = new TenantCreateResultVO();
            resultVo.setTenantId(newTenantId);
            resultVo.setTenantCode(tenantCode);
            resultVo.setTenantName(tenantName);
            resultVo.setAdminUsername(adminUsername);
            resultVo.setAdminInitialPassword(SystemConstants.DEFAULT_PASSWORD);
            resultVo.setAdminRoleCode(roleCode);
            return resultVo;
        } finally {
            TenantContextHolder.setIgnoreTenant(oldIgnoreTenant);
            if (oldTenantId != null) {
                TenantContextHolder.setTenantId(oldTenantId);
            }
        }
    }

    /**
     * 获取租户菜单ID集合
     *
     * @param tenantId 租户ID
     * @return 菜单ID集合
     */
    @Override
    public List<Long> getTenantMenuIds(Long tenantId) {
        Assert.notNull(tenantId, "租户ID不能为空");
        Assert.isTrue(hasTenantSwitchPermission(), "仅具备租户切换权限允许查看租户菜单");
        Assert.isTrue(!SystemConstants.PLATFORM_TENANT_ID.equals(tenantId), "平台租户不支持配置菜单");

        Tenant tenant = this.getById(tenantId);
        Assert.isTrue(tenant != null, "租户不存在");

        Long planId = tenant.getPlanId();
        Assert.notNull(planId, "租户套餐不能为空");

        Long oldTenantId = TenantContextHolder.getTenantId();
        boolean oldIgnoreTenant = TenantContextHolder.isIgnoreTenant();
        try {
            TenantContextHolder.setIgnoreTenant(true);
            List<Long> tenantMenuIds = tenantMenuService.listMenuIdsByTenant(tenantId);
            List<Long> planMenuIds = resolveTenantPlanMenuIds(planId);
            if (CollectionUtil.isEmpty(planMenuIds)) {
                return tenantMenuIds;
            }
            Set<Long> planMenuIdSet = new HashSet<>(planMenuIds);
            return tenantMenuIds.stream()
                    .filter(Objects::nonNull)
                    .filter(planMenuIdSet::contains)
                    .distinct()
                    .toList();
        } finally {
            TenantContextHolder.setIgnoreTenant(oldIgnoreTenant);
            if (oldTenantId != null) {
                TenantContextHolder.setTenantId(oldTenantId);
            }
        }
    }

    /**
     * 更新租户菜单配置
     *
     * @param tenantId 租户ID
     * @param menuIds 菜单ID集合
     */
    @Override
    @Transactional
    @CacheEvict(cacheNames = "menu", key = "'routes'")
    public void updateTenantMenus(Long tenantId, List<Long> menuIds) {
        Assert.notNull(tenantId, "租户ID不能为空");
        Assert.isTrue(hasTenantSwitchPermission(), "仅具备租户切换权限允许配置租户菜单");
        Assert.isTrue(!SystemConstants.PLATFORM_TENANT_ID.equals(tenantId), "平台租户不支持配置菜单");

        Tenant tenant = this.getById(tenantId);
        Assert.isTrue(tenant != null, "租户不存在");

        Long planId = tenant.getPlanId();
        Assert.notNull(planId, "租户套餐不能为空");

        List<Long> allowedMenuIds = resolveTenantPlanMenuIds(planId);
        List<Long> distinctMenuIds = CollectionUtil.emptyIfNull(menuIds)
                .stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (CollectionUtil.isNotEmpty(distinctMenuIds)) {
            Assert.isTrue(CollectionUtil.isNotEmpty(allowedMenuIds), "租户菜单范围为空，无法配置");
            Set<Long> allowedMenuIdSet = new HashSet<>(allowedMenuIds);
            boolean allAllowed = distinctMenuIds.stream().allMatch(allowedMenuIdSet::contains);
            Assert.isTrue(allAllowed, "租户菜单只能从套餐菜单中选择");
        }

        Long oldTenantId = TenantContextHolder.getTenantId();
        boolean oldIgnoreTenant = TenantContextHolder.isIgnoreTenant();
        try {
            TenantContextHolder.setIgnoreTenant(true);
            tenantMenuService.saveTenantMenus(tenantId, distinctMenuIds);
        } finally {
            TenantContextHolder.setIgnoreTenant(oldIgnoreTenant);
            if (oldTenantId != null) {
                TenantContextHolder.setTenantId(oldTenantId);
            }
        }
    }

    /**
     * 校验用户是否可访问指定租户
     *
     * @param userId   用户ID
     * @param tenantId 租户ID
     * @return true 表示可访问
     */
    @Override
    public boolean canAccessTenant(Long userId, Long tenantId) {
        if (userId == null || tenantId == null) {
            return false;
        }

        Long currentTenantId = TenantContextHolder.getTenantId();
        if (currentTenantId == null) {
            return false;
        }

        boolean canSwitchTenant = hasTenantSwitchPermission();

        // 无租户切换权限：仅允许访问当前租户
        if (!canSwitchTenant) {
            return tenantId.equals(currentTenantId);
        }

        TenantContextHolder.setIgnoreTenant(true);
        try {
            // 具备租户切换权限：仅允许切换到启用租户
            Tenant tenant = this.getById(tenantId);
            return tenant != null && tenant.getStatus() != null && tenant.getStatus() == 1;
        } finally {
            TenantContextHolder.setIgnoreTenant(false);
        }
    }

    /**
     * 租户分页查询
     *
     * @param queryParams 查询参数
     * @return 分页结果
     */
    @Override
    public Page<TenantPageVO> getTenantPage(TenantQuery queryParams) {
        Assert.notNull(queryParams, "请求参数不能为空");
        Assert.isTrue(hasTenantSwitchPermission(), "仅具备租户切换权限允许查询租户列表");

        int pageNum = queryParams.getPageNum();
        int pageSize = queryParams.getPageSize();
        String keywords = StrUtil.trimToEmpty(queryParams.getKeywords());
        Integer status = queryParams.getStatus();

        Page<Tenant> page = this.page(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Tenant>()
                        .and(StrUtil.isNotBlank(keywords), wrapper ->
                                wrapper.like(Tenant::getName, keywords)
                                        .or().like(Tenant::getCode, keywords)
                                        .or().like(Tenant::getDomain, keywords)
                        )
                        .eq(status != null, Tenant::getStatus, status)
                        .orderByAsc(Tenant::getCreateTime)
                        .orderByAsc(Tenant::getId)
        );

        return tenantConverter.toPageVo(page);
    }

    @Override
    public TenantForm getTenantForm(Long tenantId) {
        Assert.notNull(tenantId, "租户ID不能为空");
        Assert.isTrue(hasTenantSwitchPermission(), "仅具备租户切换权限允许查看租户信息");

        Tenant tenant = this.getById(tenantId);
        Assert.isTrue(tenant != null, "租户不存在");
        return tenantConverter.toForm(tenant);
    }

    /**
     * 更新租户信息
     *
     * @param tenantId 租户ID
     * @param formData 表单数据
     * @return true 表示更新成功
     */
    @Override
    public boolean updateTenant(Long tenantId, TenantForm formData) {
        Assert.notNull(tenantId, "租户ID不能为空");
        Assert.notNull(formData, "请求参数不能为空");
        Assert.isTrue(hasTenantSwitchPermission(), "仅具备租户切换权限允许修改租户");

        Tenant old = this.getById(tenantId);
        Assert.isTrue(old != null, "租户不存在");

        String tenantName = StrUtil.trimToEmpty(formData.getName());
        String tenantCode = StrUtil.toUpperCase(StrUtil.trimToEmpty(formData.getCode()));
        String domain = StrUtil.trimToEmpty(formData.getDomain());
        if (StrUtil.isBlank(domain)) {
            domain = null;
        }

        Long planId = formData.getPlanId();
        if (!SystemConstants.PLATFORM_TENANT_ID.equals(tenantId)) {
            Assert.notNull(planId, "租户套餐不能为空");
            Assert.isTrue(tenantPlanService.getById(planId) != null, "租户套餐不存在");
        }

        Assert.isTrue(StrUtil.isNotBlank(tenantName), "租户名称不能为空");
        Assert.isTrue(StrUtil.isNotBlank(tenantCode), "租户编码不能为空");

        long tenantCodeCount = this.count(new LambdaQueryWrapper<Tenant>()
                .ne(Tenant::getId, tenantId)
                .eq(Tenant::getCode, tenantCode)
        );
        Assert.isTrue(tenantCodeCount == 0, "租户编码已存在");

        if (domain != null) {
            long domainCount = this.count(new LambdaQueryWrapper<Tenant>()
                    .ne(Tenant::getId, tenantId)
                    .eq(Tenant::getDomain, domain)
            );
            Assert.isTrue(domainCount == 0, "租户域名已存在");
        }

        Tenant tenant = tenantConverter.toEntity(formData);
        tenant.setId(tenantId);
        tenant.setName(tenantName);
        tenant.setCode(tenantCode);
        tenant.setDomain(domain);
        tenant.setPlanId(planId);

        return this.updateById(tenant);
    }

    /**
     * 删除租户
     *
     * @param ids 租户ID列表
     */
    @Override
    public void deleteTenants(String ids) {
        Assert.isTrue(StrUtil.isNotBlank(ids), "删除的租户ID不能为空");
        Assert.isTrue(hasTenantSwitchPermission(), "仅具备租户切换权限允许删除租户");

        List<Long> tenantIds = java.util.Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .map(Long::parseLong)
                .collect(Collectors.toList());

        for (Long tenantId : tenantIds) {
            Tenant tenant = this.getById(tenantId);
            Assert.isTrue(tenant != null, "租户不存在");

            Assert.isTrue(!SystemConstants.DEFAULT_TENANT_ID.equals(tenantId), "默认租户不允许删除");

            long userCount = userService.count(new LambdaQueryWrapper<User>()
                    .eq(User::getTenantId, tenantId)
                    .eq(User::getIsDeleted, 0)
            );
            Assert.isTrue(userCount == 0, "租户下存在用户，无法删除");

            boolean removed = this.removeById(tenantId);
            Assert.isTrue(removed, "租户删除失败");
        }
    }

    /**
     * 修改租户状态
     *
     * @param tenantId 租户ID
     * @param status   状态
     * @return true 表示修改成功
     */
    @Override
    public boolean updateTenantStatus(Long tenantId, Integer status) {
        Assert.notNull(tenantId, "租户ID不能为空");
        Assert.notNull(status, "状态不能为空");
        Assert.isTrue(hasTenantSwitchPermission(), "仅具备租户切换权限允许修改租户状态");

        Tenant tenant = this.getById(tenantId);
        Assert.isTrue(tenant != null, "租户不存在");

        if (SystemConstants.DEFAULT_TENANT_ID.equals(tenantId) && status == 0) {
            throw new BusinessException("默认租户不允许禁用");
        }

        tenant.setStatus(status);
        return this.updateById(tenant);
    }

}
