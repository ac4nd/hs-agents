package com.hypersense.boot.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hypersense.boot.system.model.entity.TenantPlanMenu;
import org.apache.ibatis.annotations.Mapper;

/**
 * 租户套餐菜单关联 Mapper
 *
 * @author Ray Hao
 * @since 4.0.0
 */
@Mapper
public interface TenantPlanMenuMapper extends BaseMapper<TenantPlanMenu> {

}
