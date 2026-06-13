package com.hypersense.boot.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hypersense.boot.system.model.entity.LlmUsageLog;
import com.hypersense.boot.system.model.query.LlmUsageLogQuery;
import com.hypersense.boot.system.model.vo.LlmUsageLogVO;

public interface LlmUsageLogService extends IService<LlmUsageLog> {
    Page<LlmUsageLogVO> getUsageLogPage(LlmUsageLogQuery queryParams);
}
