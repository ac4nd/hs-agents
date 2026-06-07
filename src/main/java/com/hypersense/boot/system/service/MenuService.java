package com.hypersense.boot.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hypersense.boot.codegen.model.entity.GenTable;
import com.hypersense.boot.system.model.form.MenuForm;
import com.hypersense.boot.common.model.Option;
import com.hypersense.boot.system.model.entity.Menu;
import com.hypersense.boot.system.model.query.MenuQuery;
import com.hypersense.boot.system.model.vo.MenuVO;
import com.hypersense.boot.system.model.vo.RouteVO;

import java.util.List;

/**
 * 菜单业务接口
 * 
 * @author Ray.Hao
 * @since 2020/11/06
 */
public interface MenuService extends IService<Menu> {

    /**
     * 获取菜单表格列表
     */
    List<MenuVO> listMenus(MenuQuery queryParams);

    /**
     * 获取菜单下拉列表
     *
     * @param onlyParent 是否只查询父级菜单
     * @param scope 菜单范围(1=平台 2=租户)
     */
    List<Option<Long>> listMenuOptions(boolean onlyParent, Integer scope);

    /**
     * 新增菜单
     *
     * @param menuForm  菜单表单对象
     */
    boolean saveMenu(MenuForm menuForm);

    /**
     * 获取当前用户的菜单路由列表
     */
    List<RouteVO> listCurrentUserRoutes();

    /**
     * 修改菜单显示状态
     * 
     * @param menuId 菜单ID
     * @param visible 是否显示(1-显示 0-隐藏)
     */
    boolean updateMenuVisible(Long menuId, Integer visible);

    /**
     * 获取菜单表单数据
     *
     * @param id 菜单ID
     */
    MenuForm getMenuForm(Long id);

    /**
     * 删除菜单
     *
     * @param id 菜单ID
     */
    boolean deleteMenu(Long id);

    /**
     * 代码生成时添加菜单
     *
     * @param parentMenuId 父菜单ID
     * @param genConfig   实体名
     */
    void addMenuForCodegen(Long parentMenuId, GenTable genTable);
}
