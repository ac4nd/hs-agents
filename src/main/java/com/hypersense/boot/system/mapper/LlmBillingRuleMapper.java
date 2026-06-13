package com.hypersense.boot.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hypersense.boot.system.model.entity.LlmBillingRule;
import com.hypersense.boot.system.model.query.LlmBillingRuleQuery;
import com.hypersense.boot.system.model.vo.LlmBillingRuleVO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LlmBillingRuleMapper extends BaseMapper<LlmBillingRule> {
    Page<LlmBillingRuleVO> getBillingRulePage(Page<LlmBillingRuleVO> page, LlmBillingRuleQuery queryParams);
}
