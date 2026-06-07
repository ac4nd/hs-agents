package com.hypersense.boot.system.converter;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hypersense.boot.system.model.entity.TenantPlan;
import com.hypersense.boot.system.model.form.TenantPlanForm;
import com.hypersense.boot.system.model.vo.TenantPlanPageVO;
import org.mapstruct.Mapper;

/**
 * 租户套餐对象转换器
 *
 * @author Ray Hao
 * @since 4.0.0
 */
@Mapper(componentModel = "spring")
public interface TenantPlanConverter {

    Page<TenantPlanPageVO> toPageVo(Page<TenantPlan> page);

    TenantPlan toEntity(TenantPlanForm formData);

    TenantPlanForm toForm(TenantPlan entity);
}
