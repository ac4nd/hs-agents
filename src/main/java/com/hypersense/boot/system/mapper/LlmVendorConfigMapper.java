package com.hypersense.boot.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hypersense.boot.system.model.entity.LlmVendorConfig;
import com.hypersense.boot.system.model.query.LlmVendorConfigQuery;
import com.hypersense.boot.system.model.vo.LlmVendorConfigVO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LlmVendorConfigMapper extends BaseMapper<LlmVendorConfig> {
    Page<LlmVendorConfigVO> getVendorConfigPage(Page<LlmVendorConfigVO> page, LlmVendorConfigQuery queryParams);
}
