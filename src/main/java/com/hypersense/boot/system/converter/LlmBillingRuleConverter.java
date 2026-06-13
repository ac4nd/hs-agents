package com.hypersense.boot.system.converter;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hypersense.boot.system.model.entity.LlmBillingRule;
import com.hypersense.boot.system.model.form.LlmBillingRuleForm;
import com.hypersense.boot.system.model.vo.LlmBillingRuleVO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface LlmBillingRuleConverter {
    Page<LlmBillingRuleVO> toPageVo(Page<LlmBillingRule> page);
    LlmBillingRuleForm toForm(LlmBillingRule entity);
    LlmBillingRule toEntity(LlmBillingRuleForm form);
}
