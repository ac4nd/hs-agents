package com.hypersense.boot.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hypersense.boot.system.model.entity.TenantMenu;
import org.apache.ibatis.annotations.Mapper;

/**
 * 租户菜单 Mapper
 *
 * @author Ray.Hao
 * @since 3.0.0
 */
@Mapper
public interface TenantMenuMapper extends BaseMapper<TenantMenu> {
}
