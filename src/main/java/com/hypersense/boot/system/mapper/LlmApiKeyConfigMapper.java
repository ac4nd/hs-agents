package com.hypersense.boot.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hypersense.boot.system.model.entity.LlmApiKeyConfig;
import com.hypersense.boot.system.model.query.LlmApiKeyConfigQuery;
import com.hypersense.boot.system.model.vo.LlmApiKeyConfigVO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LlmApiKeyConfigMapper extends BaseMapper<LlmApiKeyConfig> {
    Page<LlmApiKeyConfigVO> getApiKeyConfigPage(Page<LlmApiKeyConfigVO> page, LlmApiKeyConfigQuery queryParams);
}
