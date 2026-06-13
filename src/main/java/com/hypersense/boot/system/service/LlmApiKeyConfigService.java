package com.hypersense.boot.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hypersense.boot.system.model.entity.LlmApiKeyConfig;
import com.hypersense.boot.system.model.form.LlmApiKeyConfigForm;
import com.hypersense.boot.system.model.query.LlmApiKeyConfigQuery;
import com.hypersense.boot.system.model.vo.LlmApiKeyConfigVO;
import java.util.List;

public interface LlmApiKeyConfigService extends IService<LlmApiKeyConfig> {
    Page<LlmApiKeyConfigVO> getApiKeyConfigPage(LlmApiKeyConfigQuery queryParams);
    LlmApiKeyConfigForm getApiKeyConfigForm(Long id);
    boolean saveApiKeyConfig(LlmApiKeyConfigForm form);
    boolean updateApiKeyConfig(Long id, LlmApiKeyConfigForm form);
    void deleteApiKeyConfigByIds(List<String> ids);
}
