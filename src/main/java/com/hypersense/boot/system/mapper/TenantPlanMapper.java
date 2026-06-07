package com.hypersense.boot.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hypersense.boot.system.model.entity.TenantPlan;
import org.apache.ibatis.annotations.Mapper;

/**
 * 租户套餐 Mapper
 *
 * @author Ray Hao
 * @since 4.0.0
 */
@Mapper
public interface TenantPlanMapper extends BaseMapper<TenantPlan> {
}
