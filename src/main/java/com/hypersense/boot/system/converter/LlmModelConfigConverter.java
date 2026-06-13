package com.hypersense.boot.system.converter;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hypersense.boot.system.model.entity.LlmModelConfig;
import com.hypersense.boot.system.model.form.LlmModelConfigForm;
import com.hypersense.boot.system.model.vo.LlmModelConfigVO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface LlmModelConfigConverter {
    Page<LlmModelConfigVO> toPageVo(Page<LlmModelConfig> page);
    LlmModelConfigForm toForm(LlmModelConfig entity);
    LlmModelConfig toEntity(LlmModelConfigForm form);
}
