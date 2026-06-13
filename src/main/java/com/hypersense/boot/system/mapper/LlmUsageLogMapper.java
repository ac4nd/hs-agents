package com.hypersense.boot.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hypersense.boot.system.model.entity.LlmUsageLog;
import com.hypersense.boot.system.model.query.LlmUsageLogQuery;
import com.hypersense.boot.system.model.vo.LlmUsageLogVO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LlmUsageLogMapper extends BaseMapper<LlmUsageLog> {
    Page<LlmUsageLogVO> getUsageLogPage(Page<LlmUsageLogVO> page, LlmUsageLogQuery queryParams);
}
