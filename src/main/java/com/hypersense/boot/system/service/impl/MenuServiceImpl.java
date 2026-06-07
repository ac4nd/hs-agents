package com.hypersense.boot.system.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hypersense.boot.codegen.model.entity.GenTable;
import com.hypersense.boot.framework.security.util.SecurityUtils;
import com.hypersense.boot.system.converter.MenuConverter;
import com.hypersense.boot.system.mapper.MenuMapper;
import com.hypersense.boot.system.model.entity.Menu;
import com.hypersense.boot.system.model.entity.Tenant;
import com.hypersense.boot.system.model.form.MenuForm;
import com.hypersense.boot.system.model.query.MenuQuery;
import com.hypersense.boot.system.model.vo.MenuVO;
import com.hypersense.boot.system.model.vo.RouteVO;
import com.hypersense.boot.common.constant.SystemConstants;
import com.hypersense.boot.framework.tenant.TenantContextHolder;
import com.hypersense.boot.system.enums.MenuTypeEnum;
import com.hypersense.boot.common.enums.StatusEnum;
import com.hypersense.boot.common.model.KeyValue;
import com.hypersense.boot.common.model.Option;
import com.hypersense.boot.system.service.MenuService;
import com.hypersense.boot.system.service.RoleMenuService;
import com.hypersense.boot.system.service.TenantMenuService;
import com.hypersense.boot.system.service.TenantPlanMenuService;
import com.hypersense.boot.system.service.TenantService;
import com.hypersense.boot.system.enums.MenuScopeEnum;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 菜单服务实现类
 *
 * @author Ray.Hao
 * @since 2020/11/06
 */
@Service
@RequiredArgsConstructor
public class MenuServiceImpl extends ServiceImpl<MenuMapper, Menu> implements MenuService {

    private final MenuConverter menuConverter;

    private final RoleMenuService roleMenuService;

    private final TenantMenuService tenantMenuService;

    private final TenantPlanMenuService tenantPlanMenuService;

    private final TenantService tenantService;


    /**
     * 菜单列表
     *
     * @param queryParams {@link MenuQuery}
     */
    @Override
    public List<MenuVO> listMenus(MenuQuery queryParams) {
        List<Menu> menus = this.list(new LambdaQueryWrapper<Menu>()
                .like(StrUtil.isNotBlank(queryParams.getKeywords()), Menu::getName, queryParams.getKeywords())
                .orderByAsc(Menu::getSort)
        );
        // 获取所有菜单ID
        Set<Long> menuIds = menus.stream()
                .map(Menu::getId)
                .collect(Collectors.toSet());

        // 获取所有父级ID
        Set<Long> parentIds = menus.stream()
                .map(Menu::getParentId)
                .collect(Collectors.toSet());

        // 获取根节点ID（递归的起点），即父节点ID中不包含在部门ID中的节点，注意这里不能拿顶级菜单 O 作为根节点，因为菜单筛选的时候 O 会被过滤掉
        List<Long> rootIds = parentIds.stream()
                .filter(id -> !menuIds.contains(id))
                .toList();

        // 使用递归函数来构建菜单树
        return rootIds.stream()
                .flatMap(rootId -> buildMenuTree(rootId, menus).stream())
                .collect(Collectors.toList());
    }

    /**
     * 递归生成菜单列表
     *
     * @param parentId 父级ID
     * @param menuList 菜单列表
     * @return 菜单列表
     */
    private List<MenuVO> buildMenuTree(Long parentId, List<Menu> menuList) {
        return CollectionUtil.emptyIfNull(menuList)
                .stream()
                .filter(menu -> menu.getParentId().equals(parentId))
                .map(entity -> {
                    MenuVO menuVO = menuConverter.toVo(entity);
                    List<MenuVO> children = buildMenuTree(entity.getId(), menuList);
                    menuVO.setChildren(children);
                    return menuVO;
                }).toList();
    }

    /**
     * 菜单下拉数据
     *
     * @param onlyParent 是否只查询父级菜单 如果为true，排除按钮
     * @param scope 菜单范围(1=平台 2=租户)
     */
    @Override
    public List<Option<Long>> listMenuOptions(boolean onlyParent, Integer scope) {
        Long tenantId = TenantContextHolder.getTenantId();
        List<Long> tenantMenuIds = Collections.emptyList();
        if (tenantId != null && !SystemConstants.PLATFORM_TENANT_ID.equals(tenantId)) {
            tenantMenuIds = tenantMenuService.listMenuIdsByTenant(tenantId);
            if (CollectionUtil.isEmpty(tenantMenuIds)) {
                return Collections.emptyList();
            }
        }

        List<Menu> menuList = this.list(new LambdaQueryWrapper<Menu>()
                .in(CollectionUtil.isNotEmpty(tenantMenuIds), Menu::getId, tenantMenuIds)
                .eq(scope != null, Menu::getScope, scope)
                .in(onlyParent, Menu::getType, MenuTypeEnum.CATALOG.getValue(), MenuTypeEnum.MENU.getValue())
                .orderByAsc(Menu::getSort)
        );
        return buildMenuOptions(SystemConstants.ROOT_NODE_ID, menuList);
    }

    /**
     * 递归生成菜单下拉层级列表
     *
     * @param parentId 父级ID
     * @param menuList 菜单列表
     * @return 菜单下拉列表
     */
    private List<Option<Long>> buildMenuOptions(Long parentId, List<Menu> menuList) {
        List<Option<Long>> menuOptions = new ArrayList<>();

        for (Menu menu : menuList) {
            if (menu.getParentId().equals(parentId)) {
                Option<Long> option = new Option<>(menu.getId(), menu.getName());
                List<Option<Long>> subMenuOptions = buildMenuOptions(menu.getId(), menuList);
                if (!subMenuOptions.isEmpty()) {
                    option.setChildren(subMenuOptions);
                }
                menuOptions.add(option);
            }
        }

        return menuOptions;
    }

    /**
     * 获取当前用户的菜单路由列表
     */
    @Override
    public List<RouteVO> listCurrentUserRoutes() {
        Set<String> roleCodes = SecurityUtils.getRoles();

        if (CollectionUtil.isEmpty(roleCodes)) {
            return Collections.emptyList();
        }

        Long originalTenantId = TenantContextHolder.getTenantId();
        boolean originalIgnoreTenant = TenantContextHolder.isIgnoreTenant();
        boolean canSwitchTenant = SecurityUtils.canSwitchTenant();

        if (originalTenantId == null) {
            return Collections.emptyList();
        }

        Long roleTenantId = canSwitchTenant ? SystemConstants.PLATFORM_TENANT_ID : originalTenantId;
        Long targetTenantId = originalTenantId;

        List<Menu> menuList;
        try {
            // 使用手动 tenant_id 过滤，避免租户上下文影响联合查询
            TenantContextHolder.setIgnoreTenant(true);
            Set<Long> planMenuIdSet = Collections.emptySet();
            if (!SystemConstants.PLATFORM_TENANT_ID.equals(targetTenantId)) {
                planMenuIdSet = resolveTenantPlanMenuIdSet(targetTenantId);
                if (CollectionUtil.isEmpty(planMenuIdSet)) {
                    return Collections.emptyList();
                }
            }

            if (SecurityUtils.isRoot()) {
                if (SystemConstants.PLATFORM_TENANT_ID.equals(targetTenantId)) {
                    menuList = this.list(new LambdaQueryWrapper<Menu>()
                            .ne(Menu::getType, MenuTypeEnum.BUTTON.getValue())
                            .orderByAsc(Menu::getSort)
                    );
                } else {
                    List<Long> tenantMenuIds = tenantMenuService.listMenuIdsByTenant(targetTenantId);
                    if (CollectionUtil.isEmpty(tenantMenuIds)) {
                        return Collections.emptyList();
                    }
                    menuList = this.list(new LambdaQueryWrapper<Menu>()
                            .in(Menu::getId, tenantMenuIds)
                            .eq(Menu::getScope, MenuScopeEnum.TENANT.getValue())
                            .ne(Menu::getType, MenuTypeEnum.BUTTON.getValue())
                            .orderByAsc(Menu::getSort)
                    );
                }
            } else {
                menuList = this.baseMapper.getMenusByRoleCodesAndTenant(roleCodes, roleTenantId, targetTenantId);
            }

            if (!planMenuIdSet.isEmpty()) {
                Set<Long> allowedMenuIds = planMenuIdSet;
                menuList = menuList.stream()
                        .filter(menu -> allowedMenuIds.contains(menu.getId()))
                        .collect(Collectors.toList());
            }
            return buildRoutes(SystemConstants.ROOT_NODE_ID, menuList);
        } finally {
            TenantContextHolder.setIgnoreTenant(originalIgnoreTenant);
        }
    }

    /**
     * 获取租户套餐允许的菜单ID集合
     * <p>
     * 优先返回租户套餐菜单；若未配置则兜底为所有业务菜单。
     * </p>
     *
     * @param tenantId 租户ID
     * @return 菜单ID集合
     */
    private Set<Long> resolveTenantPlanMenuIdSet(Long tenantId) {
        Long oldTenantId = TenantContextHolder.getTenantId();
        boolean oldIgnoreTenant = TenantContextHolder.isIgnoreTenant();

        try {
            TenantContextHolder.setIgnoreTenant(true);
            Tenant tenant = tenantService.getOne(new LambdaQueryWrapper<Tenant>()
                    .select(Tenant::getPlanId)
                    .eq(Tenant::getId, tenantId));
            Long planId = tenant == null ? null : tenant.getPlanId();
            if (planId == null) {
                return Collections.emptySet();
            }
            List<Long> planMenuIds = tenantPlanMenuService.listMenuIdsByPlan(planId);
            if (CollectionUtil.isNotEmpty(planMenuIds)) {
                return new HashSet<>(planMenuIds);
            }

            List<Long> fallbackMenuIds = this.list(new LambdaQueryWrapper<Menu>()
                            .select(Menu::getId)
                            .eq(Menu::getScope, MenuScopeEnum.TENANT.getValue()))
                    .stream()
                    .map(Menu::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            return new HashSet<>(fallbackMenuIds);
        } finally {
            TenantContextHolder.setIgnoreTenant(oldIgnoreTenant);
            if (oldTenantId != null) {
                TenantContextHolder.setTenantId(oldTenantId);
            }
        }
    }

    /**
     * 递归生成菜单路由层级列表
     *
     * @param parentId 父级ID
     * @param menuList 菜单列表
     * @return 路由层级列表
     */
    private List<RouteVO> buildRoutes(Long parentId, List<Menu> menuList) {
        List<RouteVO> routeList = new ArrayList<>();

        for (Menu menu : menuList) {
            if (menu.getParentId().equals(parentId)) {
                RouteVO routeVO = toRouteVO(menu);
                List<RouteVO> children = buildRoutes(menu.getId(), menuList);
                if (!children.isEmpty()) {
                    routeVO.setChildren(children);
                }
                routeList.add(routeVO);
            }
        }

        return routeList;
    }

    /**
     * 根据RouteBO创建RouteVO
     */
    private RouteVO toRouteVO(Menu menu) {
        RouteVO routeVO = new RouteVO();
        String routePath = menu.getRoutePath();
        boolean externalLink = StrUtil.startWithAny(routePath, "http://", "https://");

        // 获取路由名称
        String routeName = menu.getRouteName();
        if (StrUtil.isBlank(routeName)) {
            // 外链不做驼峰转换，使用唯一占位，避免 http:// 被解析异常
            routeName = externalLink
                    ? "ext-" + menu.getId()
                    : StringUtils.capitalize(StrUtil.toCamelCase(routePath, '-'));
        }
        // 根据name路由跳转 this.$router.push({name:xxx})
        routeVO.setName(routeName);

        // 根据path路由跳转 this.$router.push({path:xxx})
        routeVO.setPath(routePath);
        routeVO.setRedirect(menu.getRedirect());
        // 外链无组件
        routeVO.setComponent(externalLink ? null : menu.getComponent());

        RouteVO.Meta meta = new RouteVO.Meta();
        meta.setTitle(menu.getName());
        meta.setIcon(menu.getIcon());
        meta.setHidden(StatusEnum.DISABLE.getValue().equals(menu.getVisible()));
        // 【菜单】是否开启页面缓存
        if (MenuTypeEnum.MENU.getValue().equals(menu.getType())
                && ObjectUtil.equals(menu.getKeepAlive(), 1)) {
            meta.setKeepAlive(true);
        }
        meta.setAlwaysShow(ObjectUtil.equals(menu.getAlwaysShow(), 1));

        Map<String, Object> paramsMap = menu.getParams();
        if (paramsMap != null && !paramsMap.isEmpty()) {
            Map<String, String> paramMap = paramsMap.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> String.valueOf(e.getValue())));
            meta.setParams(paramMap);
        }
        routeVO.setMeta(meta);
        return routeVO;
    }

    /**
     * 新增/修改菜单
     */
    @Override
    @CacheEvict(cacheNames = "menu", key = "'routes'")
    public boolean saveMenu(MenuForm menuForm) {

        String menuType = menuForm.getType();
        boolean isExternalLink = MenuTypeEnum.MENU.getValue().equals(menuType)
                && StrUtil.startWithAny(menuForm.getRoutePath(), "http://", "https://");

        if (MenuTypeEnum.CATALOG.getValue().equals(menuType)) {  // 如果是目录
            String path = menuForm.getRoutePath();
            if (menuForm.getParentId() == 0 && !path.startsWith("/")) {
                menuForm.setRoutePath("/" + path); // 一级目录需以 / 开头
            }
            menuForm.setComponent("Layout");
        } else if (isExternalLink) {
            // 外链菜单组件设置为 null，通过 routePath 判断外链
            menuForm.setComponent(null);
        }
        if (Objects.equals(menuForm.getParentId(), menuForm.getId())) {
            throw new RuntimeException("父级菜单不能为当前菜单");
        }
        if (MenuTypeEnum.BUTTON.getValue().equals(menuType)) {
            // 按钮菜单继承父级范围，避免跨平台/业务菜单混用
            Long parentId = menuForm.getParentId();
            if (parentId != null) {
                Menu parent = this.getById(parentId);
                if (parent != null) {
                    menuForm.setScope(parent.getScope());
                }
            }
            if (menuForm.getScope() == null) {
                menuForm.setScope(MenuScopeEnum.TENANT.getValue());
            }
        }

        Menu entity = menuConverter.toEntity(menuForm);
        String treePath = generateMenuTreePath(menuForm.getParentId());
        entity.setTreePath(treePath);

        List<KeyValue> params = menuForm.getParams();
        if (CollectionUtil.isNotEmpty(params)) {
            entity.setParams(params.stream()
                    .collect(Collectors.toMap(KeyValue::getKey, KeyValue::getValue)));
        } else {
            entity.setParams(null);
        }
        // 新增类型为菜单时候 路由名称唯一
        if (MenuTypeEnum.MENU.getValue().equals(menuType) && !isExternalLink) {
            Assert.isFalse(this.exists(new LambdaQueryWrapper<Menu>()
                    .eq(Menu::getRouteName, entity.getRouteName())
                    .ne(menuForm.getId() != null, Menu::getId, menuForm.getId())
            ), "路由名称已存在");
        } else {
            // 其他类型时 给路由名称赋值为空
            entity.setRouteName(null);
        }

        boolean result = this.saveOrUpdate(entity);
        if (result) {
            // 编辑刷新角色权限缓存
            if (menuForm.getId() != null) {
                roleMenuService.refreshRolePermsCache();
            }
        }
        // 修改菜单如果有子菜单，则更新子菜单的树路径
        updateChildrenTreePath(entity.getId(), treePath);
        return result;
    }

    /**
     * 更新子菜单树路径
     *
     * @param id       当前菜单ID
     * @param treePath 当前菜单树路径
     */
    private void updateChildrenTreePath(Long id, String treePath) {
        List<Menu> children = this.list(new LambdaQueryWrapper<Menu>().eq(Menu::getParentId, id));
        if (CollectionUtil.isNotEmpty(children)) {
            // 子菜单的树路径等于父菜单的树路径加上父菜单ID
            String childTreePath = treePath + "," + id;
            this.update(new LambdaUpdateWrapper<Menu>()
                    .eq(Menu::getParentId, id)
                    .set(Menu::getTreePath, childTreePath)
            );
            for (Menu child : children) {
                // 递归更新子菜单
                updateChildrenTreePath(child.getId(), childTreePath);
            }
        }
    }

    /**
     * 部门路径生成
     *
     * @param parentId 父ID
     * @return 父节点路径以英文逗号(, )分割，eg: 1,2,3
     */
    private String generateMenuTreePath(Long parentId) {
        if (SystemConstants.ROOT_NODE_ID.equals(parentId)) {
            return String.valueOf(parentId);
        } else {
            Menu parent = this.getById(parentId);
            return parent != null ? parent.getTreePath() + "," + parent.getId() : null;
        }
    }


    /**
     * 修改菜单显示状态
     *
     * @param menuId  菜单ID
     * @param visible 是否显示(1->显示；2->隐藏)
     * @return 是否修改成功
     */
    @Override
    @CacheEvict(cacheNames = "menu", key = "'routes'")
    public boolean updateMenuVisible(Long menuId, Integer visible) {
        return this.update(new LambdaUpdateWrapper<Menu>()
                .eq(Menu::getId, menuId)
                .set(Menu::getVisible, visible)
        );
    }

    /**
     * 获取菜单表单数据
     *
     * @param id 菜单ID
     * @return 菜单表单数据
     */
    @Override
    public MenuForm getMenuForm(Long id) {
        Menu entity = this.getById(id);
        Assert.isTrue(entity != null, "菜单不存在");
        MenuForm formData = menuConverter.toForm(entity);
        // 路由参数 Map 转换为 [{key:"id", value:"1"}, {key:"name", value:"张三"}]
        Map<String, Object> paramsMap = entity.getParams();
        if (paramsMap != null && !paramsMap.isEmpty()) {
            List<KeyValue> transformedList = paramsMap.entrySet().stream()
                    .map(entry -> new KeyValue(entry.getKey(), String.valueOf(entry.getValue())))
                    .toList();
            formData.setParams(transformedList);
        }
        return formData;
    }

    /**
     * 删除菜单
     *
     * @param id 菜单ID
     * @return 是否删除成功
     */
    @Override
    @CacheEvict(cacheNames = "menu", key = "'routes'")
    public boolean deleteMenu(Long id) {
        boolean result = this.remove(new LambdaQueryWrapper<Menu>()
                .eq(Menu::getId, id)
                .or()
                .apply("CONCAT (',',tree_path,',') LIKE CONCAT('%,',{0},',%')", id));


        // 刷新角色权限缓存
        if (result) {
            roleMenuService.refreshRolePermsCache();
        }
        return result;

    }

    /**
     * 代码生成时添加菜单
     *
     * @param parentMenuId 父菜单ID
     * @param genTable    实体名称
     */
    @Override
    public void addMenuForCodegen(Long parentMenuId, GenTable genTable) {
        Menu parentMenu = this.getById(parentMenuId);
        Assert.notNull(parentMenu, "上级菜单不存在");

        String entityName = genTable.getEntityName();

        long count = this.count(new LambdaQueryWrapper<Menu>().eq(Menu::getRouteName, entityName));
        if (count > 0) {
            return;
        }

        // 获取父级菜单子菜单最带的排序
        Menu maxSortMenu = this.getOne(new LambdaQueryWrapper<Menu>().eq(Menu::getParentId, parentMenuId)
                .orderByDesc(Menu::getSort)
                .last("limit 1")
        );
        int sort = 1;
        if (maxSortMenu != null) {
            sort = maxSortMenu.getSort() + 1;
        }

        Menu menu = new Menu();
        menu.setParentId(parentMenuId);
        menu.setName(genTable.getBusinessName());

        menu.setRouteName(entityName);
        menu.setRoutePath(StrUtil.toSymbolCase(entityName, '-'));
        menu.setComponent(genTable.getModuleName() + "/" + StrUtil.toSymbolCase(entityName, '-') + "/index");
        menu.setType(MenuTypeEnum.MENU.getValue());
        menu.setSort(sort);
        menu.setVisible(1);
        boolean result = this.save(menu);

        if (result) {
            // 生成treePath
            String treePath = generateMenuTreePath(parentMenuId);
            menu.setTreePath(treePath);
            this.updateById(menu);

            // 生成CURD按钮权限
            String permPrefix = genTable.getModuleName() + ":" + genTable.getTableName().replace("_", "-") + ":";
            String[] actions = {"查询", "新增", "修改", "删除"};
            String[] perms = {"list", "create", "update", "delete"};

            for (int i = 0; i < actions.length; i++) {
                Menu button = new Menu();
                button.setParentId(menu.getId());
                button.setType(MenuTypeEnum.BUTTON.getValue());
                button.setName(actions[i]);
                button.setPerm(permPrefix + perms[i]);
                button.setSort(i + 1);
                this.save(button);

                // 生成treePath
                button.setTreePath(treePath + "," + button.getId());
                this.updateById(button);
            }
        }
    }

}
