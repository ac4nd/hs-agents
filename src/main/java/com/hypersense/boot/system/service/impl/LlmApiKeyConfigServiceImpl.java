package com.hypersense.boot.system.service.impl;

import cn.hutool.core.lang.Assert;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hypersense.boot.common.exception.BusinessException;
import com.hypersense.boot.system.converter.LlmApiKeyConfigConverter;
import com.hypersense.boot.system.mapper.LlmApiKeyConfigMapper;
import com.hypersense.boot.system.mapper.LlmModelConfigMapper;
import com.hypersense.boot.system.model.entity.LlmApiKeyConfig;
import com.hypersense.boot.system.model.entity.LlmModelConfig;
import com.hypersense.boot.system.model.entity.LlmVendorConfig;
import com.hypersense.boot.system.model.form.LlmApiKeyConfigForm;
import com.hypersense.boot.system.model.query.LlmApiKeyConfigQuery;
import com.hypersense.boot.system.model.vo.LlmApiKeyConfigVO;
import com.hypersense.boot.system.service.LlmApiKeyConfigService;
import com.hypersense.boot.system.service.LlmVendorConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LlmApiKeyConfigServiceImpl extends ServiceImpl<LlmApiKeyConfigMapper, LlmApiKeyConfig> implements LlmApiKeyConfigService {

    private final LlmApiKeyConfigConverter converter;
    private final LlmVendorConfigService vendorConfigService;
    private final LlmModelConfigMapper modelConfigMapper;

    @Override
    public Page<LlmApiKeyConfigVO> getApiKeyConfigPage(LlmApiKeyConfigQuery queryParams) {
        Page<LlmApiKeyConfigVO> page = this.baseMapper.getApiKeyConfigPage(
                new Page<>(queryParams.getPageNum(), queryParams.getPageSize()), queryParams);
        // 脱敏：列表出口对 secret 掩码处理，避免明文泄露
        page.getRecords().forEach(vo -> vo.setSecret(maskSecret(vo.getSecret())));
        return page;
    }

    /**
     * secret 掩码：保留前 12 位与后 4 位，中间以 **** 替代。
     * 例：sk-sys-abcde1234fghjklmnopqrstuvwxyz1234 -> sk-sys-abcde****1234
     */
    private String maskSecret(String secret) {
        if (secret == null || secret.length() <= 16) {
            return secret;
        }
        int len = secret.length();
        return secret.substring(0, 12) + "****" + secret.substring(len - 4);
    }

    @Override
    public LlmApiKeyConfigForm getApiKeyConfigForm(Long id) {
        LlmApiKeyConfig entity = this.getById(id);
        if (entity == null) {
            throw new BusinessException("API-KEY配置不存在");
        }
        return converter.toForm(entity);
    }

    @Override
    public boolean saveApiKeyConfig(LlmApiKeyConfigForm form) {
        // 校验厂商配置存在
        LlmVendorConfig vendorConfig = vendorConfigService.getById(form.getVendorConfigId());
        if (vendorConfig == null) {
            throw new BusinessException("关联的厂商配置不存在");
        }

        LlmApiKeyConfig entity = converter.toEntity(form);
        // 自动生成签名密钥
        entity.setSecret("sk-sys-" + UUID.randomUUID().toString().replace("-", ""));
        // 初始化用量
        entity.setUsedTokens(0L);
        entity.setTotalCost(java.math.BigDecimal.ZERO);
        return this.save(entity);
    }

    @Override
    public boolean updateApiKeyConfig(Long id, LlmApiKeyConfigForm form) {
        LlmApiKeyConfig entity = this.getById(id);
        if (entity == null) {
            throw new BusinessException("API-KEY配置不存在");
        }
        // 校验厂商配置存在
        LlmVendorConfig vendorConfig = vendorConfigService.getById(form.getVendorConfigId());
        if (vendorConfig == null) {
            throw new BusinessException("关联的厂商配置不存在");
        }

        LlmApiKeyConfig updateEntity = converter.toEntity(form);
        updateEntity.setId(id);
        // 不允许通过更新修改 secret 和用量字段
        updateEntity.setSecret(null);
        updateEntity.setUsedTokens(null);
        updateEntity.setTotalCost(null);
        return this.updateById(updateEntity);
    }

    @Override
    public void deleteApiKeyConfigByIds(List<String> ids) {
        List<Long> longIds = ids.stream().map(Long::valueOf).toList();
        // 关联校验：被 sys_llm_model_config 引用的 KEY 不允许删除，避免产生孤儿引用
        Long refCount = modelConfigMapper.selectCount(
                new LambdaQueryWrapper<LlmModelConfig>()
                        .in(LlmModelConfig::getApiKeyConfigId, longIds));
        if (refCount != null && refCount > 0) {
            throw new BusinessException("所选API-KEY已被模型配置引用，无法删除，请先解除关联");
        }
        this.removeByIds(longIds);
    }
}
