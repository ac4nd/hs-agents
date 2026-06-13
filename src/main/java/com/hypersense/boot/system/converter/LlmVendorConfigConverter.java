package com.hypersense.boot.system.converter;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hypersense.boot.system.model.entity.LlmVendorConfig;
import com.hypersense.boot.system.model.form.LlmVendorConfigForm;
import com.hypersense.boot.system.model.vo.LlmVendorConfigVO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface LlmVendorConfigConverter {
    Page<LlmVendorConfigVO> toPageVo(Page<LlmVendorConfig> page);
    LlmVendorConfigForm toForm(LlmVendorConfig entity);
    LlmVendorConfig toEntity(LlmVendorConfigForm form);
}
