package com.hypersense.boot.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hypersense.boot.system.model.entity.LlmVendorConfig;
import com.hypersense.boot.system.model.form.LlmVendorConfigForm;
import com.hypersense.boot.system.model.query.LlmVendorConfigQuery;
import com.hypersense.boot.system.model.vo.LlmVendorConfigVO;
import java.util.List;

public interface LlmVendorConfigService extends IService<LlmVendorConfig> {
    Page<LlmVendorConfigVO> getVendorConfigPage(LlmVendorConfigQuery queryParams);
    LlmVendorConfigForm getVendorConfigForm(Long id);
    boolean saveVendorConfig(LlmVendorConfigForm form);
    boolean updateVendorConfig(Long id, LlmVendorConfigForm form);
    void deleteVendorConfigByIds(List<String> ids);
}
