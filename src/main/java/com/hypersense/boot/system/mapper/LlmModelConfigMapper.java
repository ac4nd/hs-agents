package com.hypersense.boot.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hypersense.boot.system.model.entity.LlmModelConfig;
import com.hypersense.boot.system.model.query.LlmModelConfigQuery;
import com.hypersense.boot.system.model.vo.LlmModelConfigVO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LlmModelConfigMapper extends BaseMapper<LlmModelConfig> {
    Page<LlmModelConfigVO> getModelConfigPage(Page<LlmModelConfigVO> page, LlmModelConfigQuery queryParams);
}
