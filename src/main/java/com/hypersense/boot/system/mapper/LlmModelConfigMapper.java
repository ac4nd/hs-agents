package com.hypersense.boot.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hypersense.boot.system.model.entity.LlmModelConfig;
import com.hypersense.boot.system.model.query.LlmModelConfigQuery;
import com.hypersense.boot.system.model.vo.LlmModelConfigVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface LlmModelConfigMapper extends BaseMapper<LlmModelConfig> {
    Page<LlmModelConfigVO> getModelConfigPage(Page<LlmModelConfigVO> page, LlmModelConfigQuery queryParams);

    /**
     * 查询当前登录用户所属租户下所有启用的模型列表（多租户拦截器自动按 tenant_id 过滤）。
     * 用于聊天框下拉选择当前用户可用的 LLM。
     */
    List<LlmModelConfigVO> listEnabledByCurrentUser();
}
