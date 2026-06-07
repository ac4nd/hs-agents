package com.hypersense.boot.system.converter;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hypersense.boot.system.model.entity.Tenant;
import com.hypersense.boot.system.model.form.TenantForm;
import com.hypersense.boot.system.model.vo.TenantPageVO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TenantConverter {

    Page<TenantPageVO> toPageVo(Page<Tenant> page);

    TenantForm toForm(Tenant entity);

    Tenant toEntity(TenantForm formData);
}
