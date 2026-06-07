package com.hypersense.boot.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hypersense.boot.system.model.entity.Menu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Set;

/**
 * 菜单访问层
 *
 * @author Ray
 * @since 2022/1/24
 */

@Mapper
public interface MenuMapper extends BaseMapper<Menu> {

    /**
     * 获取菜单路由列表
     *
     * @param roleCodes 角色编码集合
     */
    List<Menu> getMenusByRoleCodes(@Param("roleCodes") Set<String> roleCodes);

    /**
     * 获取菜单路由列表（角色菜单 ∩ 租户可用菜单）
     *
     * @param roleCodes 角色编码集合
     * @param roleTenantId 角色归属租户ID
     * @param tenantId 目标租户ID
     */
    List<Menu> getMenusByRoleCodesAndTenant(
            @Param("roleCodes") Set<String> roleCodes,
            @Param("roleTenantId") Long roleTenantId,
            @Param("tenantId") Long tenantId
    );

}
