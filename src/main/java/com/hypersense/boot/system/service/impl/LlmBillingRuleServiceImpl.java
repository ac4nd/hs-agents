package com.hypersense.boot.system.service.impl;

import cn.hutool.core.lang.Assert;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hypersense.boot.common.exception.BusinessException;
import com.hypersense.boot.system.converter.LlmBillingRuleConverter;
import com.hypersense.boot.system.mapper.LlmBillingRuleMapper;
import com.hypersense.boot.system.model.entity.LlmBillingRule;
import com.hypersense.boot.system.model.entity.LlmVendorConfig;
import com.hypersense.boot.system.model.form.LlmBillingRuleForm;
import com.hypersense.boot.system.model.query.LlmBillingRuleQuery;
import com.hypersense.boot.system.model.vo.LlmBillingRuleVO;
import com.hypersense.boot.system.service.LlmBillingRuleService;
import com.hypersense.boot.system.service.LlmVendorConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LlmBillingRuleServiceImpl extends ServiceImpl<LlmBillingRuleMapper, LlmBillingRule> implements LlmBillingRuleService {

    private final LlmBillingRuleConverter converter;
    private final LlmVendorConfigService vendorConfigService;

    @Override
    public Page<LlmBillingRuleVO> getBillingRulePage(LlmBillingRuleQuery queryParams) {
        return this.baseMapper.getBillingRulePage(new Page<>(queryParams.getPageNum(), queryParams.getPageSize()), queryParams);
    }

    @Override
    public LlmBillingRuleForm getBillingRuleForm(Long id) {
        LlmBillingRule entity = this.getById(id);
        if (entity == null) {
            throw new BusinessException("计费规则不存在");
        }
        return converter.toForm(entity);
    }

    @Override
    public boolean saveBillingRule(LlmBillingRuleForm form) {
        // 校验厂商配置存在
        LlmVendorConfig vendorConfig = vendorConfigService.getById(form.getVendorConfigId());
        if (vendorConfig == null) {
            throw new BusinessException("关联的厂商配置不存在");
        }
        // 校验同一厂商下模型名称唯一
        long count = this.count(new LambdaQueryWrapper<LlmBillingRule>()
                .eq(LlmBillingRule::getVendorConfigId, form.getVendorConfigId())
                .eq(LlmBillingRule::getModelName, form.getModelName()));
        Assert.isTrue(count == 0, "该厂商下模型计费规则已存在");

        LlmBillingRule entity = converter.toEntity(form);
        return this.save(entity);
    }

    @Override
    public boolean updateBillingRule(Long id, LlmBillingRuleForm form) {
        LlmBillingRule entity = this.getById(id);
        if (entity == null) {
            throw new BusinessException("计费规则不存在");
        }
        // 校验厂商配置存在
        LlmVendorConfig vendorConfig = vendorConfigService.getById(form.getVendorConfigId());
        if (vendorConfig == null) {
            throw new BusinessException("关联的厂商配置不存在");
        }
        // 校验唯一性
        if (!entity.getVendorConfigId().equals(form.getVendorConfigId()) || !entity.getModelName().equals(form.getModelName())) {
            long count = this.count(new LambdaQueryWrapper<LlmBillingRule>()
                    .eq(LlmBillingRule::getVendorConfigId, form.getVendorConfigId())
                    .eq(LlmBillingRule::getModelName, form.getModelName()));
            Assert.isTrue(count == 0, "该厂商下模型计费规则已存在");
        }
        LlmBillingRule updateEntity = converter.toEntity(form);
        updateEntity.setId(id);
        return this.updateById(updateEntity);
    }

    @Override
    public void deleteBillingRuleByIds(List<String> ids) {
        this.removeByIds(ids);
    }
}
