package com.hypersense.boot.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hypersense.boot.system.model.entity.LlmModelConfig;
import com.hypersense.boot.system.model.form.LlmModelConfigForm;
import com.hypersense.boot.system.model.query.LlmModelConfigQuery;
import com.hypersense.boot.system.model.vo.LlmModelConfigVO;
import java.util.List;

public interface LlmModelConfigService extends IService<LlmModelConfig> {
    Page<LlmModelConfigVO> getModelConfigPage(LlmModelConfigQuery queryParams);
    LlmModelConfigForm getModelConfigForm(Long id);
    boolean saveModelConfig(LlmModelConfigForm form);
    boolean updateModelConfig(Long id, LlmModelConfigForm form);
    void deleteModelConfigByIds(List<String> ids);

    /**
     * 查询当前登录用户所属租户下所有启用的模型列表。
     * 用于聊天框下拉选择当前用户可用的 LLM。
     */
    List<LlmModelConfigVO> listByCurrentUser();
}
