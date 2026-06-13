package com.hypersense.boot.system.converter;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hypersense.boot.system.model.entity.LlmApiKeyConfig;
import com.hypersense.boot.system.model.form.LlmApiKeyConfigForm;
import com.hypersense.boot.system.model.vo.LlmApiKeyConfigVO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface LlmApiKeyConfigConverter {
    Page<LlmApiKeyConfigVO> toPageVo(Page<LlmApiKeyConfig> page);
    LlmApiKeyConfigForm toForm(LlmApiKeyConfig entity);
    LlmApiKeyConfig toEntity(LlmApiKeyConfigForm form);
}
