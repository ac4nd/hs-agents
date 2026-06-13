package com.hypersense.boot.system.service.impl;

import cn.hutool.core.lang.Assert;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hypersense.boot.common.exception.BusinessException;
import com.hypersense.boot.system.converter.LlmModelConfigConverter;
import com.hypersense.boot.system.mapper.LlmModelConfigMapper;
import com.hypersense.boot.system.model.entity.LlmApiKeyConfig;
import com.hypersense.boot.system.model.entity.LlmModelConfig;
import com.hypersense.boot.system.model.form.LlmModelConfigForm;
import com.hypersense.boot.system.model.query.LlmModelConfigQuery;
import com.hypersense.boot.system.model.vo.LlmModelConfigVO;
import com.hypersense.boot.system.service.LlmApiKeyConfigService;
import com.hypersense.boot.system.service.LlmModelConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LlmModelConfigServiceImpl extends ServiceImpl<LlmModelConfigMapper, LlmModelConfig> implements LlmModelConfigService {

    private final LlmModelConfigConverter converter;
    private final LlmApiKeyConfigService apiKeyConfigService;

    @Override
    public Page<LlmModelConfigVO> getModelConfigPage(LlmModelConfigQuery queryParams) {
        return this.baseMapper.getModelConfigPage(new Page<>(queryParams.getPageNum(), queryParams.getPageSize()), queryParams);
    }

    @Override
    public LlmModelConfigForm getModelConfigForm(Long id) {
        LlmModelConfig entity = this.getById(id);
        if (entity == null) {
            throw new BusinessException("模型配置不存在");
        }
        return converter.toForm(entity);
    }

    @Override
    public boolean saveModelConfig(LlmModelConfigForm form) {
        // 校验API-KEY配置存在
        LlmApiKeyConfig apiKeyConfig = apiKeyConfigService.getById(form.getApiKeyConfigId());
        if (apiKeyConfig == null) {
            throw new BusinessException("关联的API-KEY配置不存在");
        }
        // 校验同一API-KEY下模型名称唯一
        long count = this.count(new LambdaQueryWrapper<LlmModelConfig>()
                .eq(LlmModelConfig::getApiKeyConfigId, form.getApiKeyConfigId())
                .eq(LlmModelConfig::getModelName, form.getModelName()));
        Assert.isTrue(count == 0, "该API-KEY下模型配置已存在");

        LlmModelConfig entity = converter.toEntity(form);
        return this.save(entity);
    }

    @Override
    public boolean updateModelConfig(Long id, LlmModelConfigForm form) {
        LlmModelConfig entity = this.getById(id);
        if (entity == null) {
            throw new BusinessException("模型配置不存在");
        }
        // 校验API-KEY配置存在
        LlmApiKeyConfig apiKeyConfig = apiKeyConfigService.getById(form.getApiKeyConfigId());
        if (apiKeyConfig == null) {
            throw new BusinessException("关联的API-KEY配置不存在");
        }
        // 校验唯一性
        if (!entity.getApiKeyConfigId().equals(form.getApiKeyConfigId()) || !entity.getModelName().equals(form.getModelName())) {
            long count = this.count(new LambdaQueryWrapper<LlmModelConfig>()
                    .eq(LlmModelConfig::getApiKeyConfigId, form.getApiKeyConfigId())
                    .eq(LlmModelConfig::getModelName, form.getModelName()));
            Assert.isTrue(count == 0, "该API-KEY下模型配置已存在");
        }
        LlmModelConfig updateEntity = converter.toEntity(form);
        updateEntity.setId(id);
        return this.updateById(updateEntity);
    }

    @Override
    public void deleteModelConfigByIds(List<String> ids) {
        this.removeByIds(ids);
    }
}
