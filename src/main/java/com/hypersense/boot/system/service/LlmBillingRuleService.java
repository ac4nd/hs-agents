package com.hypersense.boot.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hypersense.boot.system.model.entity.LlmBillingRule;
import com.hypersense.boot.system.model.form.LlmBillingRuleForm;
import com.hypersense.boot.system.model.query.LlmBillingRuleQuery;
import com.hypersense.boot.system.model.vo.LlmBillingRuleVO;
import java.util.List;

public interface LlmBillingRuleService extends IService<LlmBillingRule> {
    Page<LlmBillingRuleVO> getBillingRulePage(LlmBillingRuleQuery queryParams);
    LlmBillingRuleForm getBillingRuleForm(Long id);
    boolean saveBillingRule(LlmBillingRuleForm form);
    boolean updateBillingRule(Long id, LlmBillingRuleForm form);
    void deleteBillingRuleByIds(List<String> ids);
}
