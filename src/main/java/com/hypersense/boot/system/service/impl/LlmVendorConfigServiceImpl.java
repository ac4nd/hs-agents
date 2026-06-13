package com.hypersense.boot.system.service.impl;

import cn.hutool.core.lang.Assert;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hypersense.boot.common.exception.BusinessException;
import com.hypersense.boot.system.converter.LlmVendorConfigConverter;
import com.hypersense.boot.system.mapper.LlmVendorConfigMapper;
import com.hypersense.boot.system.model.entity.LlmVendorConfig;
import com.hypersense.boot.system.model.form.LlmVendorConfigForm;
import com.hypersense.boot.system.model.query.LlmVendorConfigQuery;
import com.hypersense.boot.system.model.vo.LlmVendorConfigVO;
import com.hypersense.boot.system.service.LlmVendorConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LlmVendorConfigServiceImpl extends ServiceImpl<LlmVendorConfigMapper, LlmVendorConfig> implements LlmVendorConfigService {

    private final LlmVendorConfigConverter converter;

    @Override
    public Page<LlmVendorConfigVO> getVendorConfigPage(LlmVendorConfigQuery queryParams) {
        return this.baseMapper.getVendorConfigPage(new Page<>(queryParams.getPageNum(), queryParams.getPageSize()), queryParams);
    }

    @Override
    public LlmVendorConfigForm getVendorConfigForm(Long id) {
        LlmVendorConfig entity = this.getById(id);
        if (entity == null) {
            throw new BusinessException("厂商配置不存在");
        }
        return converter.toForm(entity);
    }

    @Override
    public boolean saveVendorConfig(LlmVendorConfigForm form) {
        // 校验 vendorCode 唯一
        long count = this.count(new LambdaQueryWrapper<LlmVendorConfig>()
                .eq(LlmVendorConfig::getVendorCode, form.getVendorCode()));
        Assert.isTrue(count == 0, "厂商编码已存在");

        LlmVendorConfig entity = converter.toEntity(form);
        return this.save(entity);
    }

    @Override
    public boolean updateVendorConfig(Long id, LlmVendorConfigForm form) {
        LlmVendorConfig entity = this.getById(id);
        if (entity == null) {
            throw new BusinessException("厂商配置不存在");
        }
        // 校验 vendorCode 唯一
        if (!entity.getVendorCode().equals(form.getVendorCode())) {
            long count = this.count(new LambdaQueryWrapper<LlmVendorConfig>()
                    .eq(LlmVendorConfig::getVendorCode, form.getVendorCode()));
            Assert.isTrue(count == 0, "厂商编码已存在");
        }
        LlmVendorConfig updateEntity = converter.toEntity(form);
        updateEntity.setId(id);
        return this.updateById(updateEntity);
    }

    @Override
    public void deleteVendorConfigByIds(List<String> ids) {
        this.removeByIds(ids);
    }
}
