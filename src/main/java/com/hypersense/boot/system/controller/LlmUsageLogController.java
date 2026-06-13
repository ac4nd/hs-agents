package com.hypersense.boot.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hypersense.boot.common.annotation.Log;
import com.hypersense.boot.common.enums.ActionTypeEnum;
import com.hypersense.boot.common.enums.LogModuleEnum;
import com.hypersense.boot.common.result.PageResult;
import com.hypersense.boot.system.model.query.LlmUsageLogQuery;
import com.hypersense.boot.system.model.vo.LlmUsageLogVO;
import com.hypersense.boot.system.service.LlmUsageLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "LLM用量日志")
@RestController
@RequestMapping("/api/v1/llm/usage-logs")
@RequiredArgsConstructor
public class LlmUsageLogController {

    private final LlmUsageLogService usageLogService;

    @Operation(summary = "用量日志分页列表")
    @GetMapping
    @PreAuthorize("@ss.hasPerm('sys:llm-usage-log:list')")
    @Log(module = LogModuleEnum.LLM_CONFIG, value = ActionTypeEnum.LIST)
    public PageResult<LlmUsageLogVO> getUsageLogPage(LlmUsageLogQuery queryParams) {
        Page<LlmUsageLogVO> result = usageLogService.getUsageLogPage(queryParams);
        return PageResult.success(result);
    }
}
