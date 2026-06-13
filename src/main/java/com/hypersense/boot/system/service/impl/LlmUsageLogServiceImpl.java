package com.hypersense.boot.system.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hypersense.boot.system.mapper.LlmUsageLogMapper;
import com.hypersense.boot.system.model.entity.LlmUsageLog;
import com.hypersense.boot.system.model.query.LlmUsageLogQuery;
import com.hypersense.boot.system.model.vo.LlmUsageLogVO;
import com.hypersense.boot.system.service.LlmUsageLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LlmUsageLogServiceImpl extends ServiceImpl<LlmUsageLogMapper, LlmUsageLog> implements LlmUsageLogService {

    @Override
    public Page<LlmUsageLogVO> getUsageLogPage(LlmUsageLogQuery queryParams) {
        return this.baseMapper.getUsageLogPage(new Page<>(queryParams.getPageNum(), queryParams.getPageSize()), queryParams);
    }
}
