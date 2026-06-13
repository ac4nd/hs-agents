package com.hypersense.boot.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hypersense.boot.common.annotation.Log;
import com.hypersense.boot.common.annotation.RepeatSubmit;
import com.hypersense.boot.common.enums.ActionTypeEnum;
import com.hypersense.boot.common.enums.LogModuleEnum;
import com.hypersense.boot.common.result.PageResult;
import com.hypersense.boot.common.result.Result;
import com.hypersense.boot.system.model.form.LlmApiKeyConfigForm;
import com.hypersense.boot.system.model.query.LlmApiKeyConfigQuery;
import com.hypersense.boot.system.model.vo.LlmApiKeyConfigVO;
import com.hypersense.boot.system.service.LlmApiKeyConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Arrays;

@Tag(name = "LLM API-KEY配置")
@RestController
@RequestMapping("/api/v1/llm/api-key-configs")
@RequiredArgsConstructor
public class LlmApiKeyConfigController {

    private final LlmApiKeyConfigService apiKeyConfigService;

    @Operation(summary = "API-KEY配置分页列表")
    @GetMapping
    @PreAuthorize("@ss.hasPerm('sys:llm-api-key-config:list')")
    @Log(module = LogModuleEnum.LLM_CONFIG, value = ActionTypeEnum.LIST)
    public PageResult<LlmApiKeyConfigVO> getApiKeyConfigPage(LlmApiKeyConfigQuery queryParams) {
        Page<LlmApiKeyConfigVO> result = apiKeyConfigService.getApiKeyConfigPage(queryParams);
        return PageResult.success(result);
    }

    @Operation(summary = "获取API-KEY配置表单")
    @PreAuthorize("@ss.hasPerm('sys:llm-api-key-config:update')")
    @GetMapping("/{id}/form")
    public Result<LlmApiKeyConfigForm> getApiKeyConfigForm(@PathVariable Long id) {
        LlmApiKeyConfigForm form = apiKeyConfigService.getApiKeyConfigForm(id);
        return Result.success(form);
    }

    @Operation(summary = "新增API-KEY配置")
    @PostMapping
    @PreAuthorize("@ss.hasPerm('sys:llm-api-key-config:create')")
    @RepeatSubmit
    @Log(module = LogModuleEnum.LLM_CONFIG, value = ActionTypeEnum.INSERT)
    public Result<?> saveApiKeyConfig(@Valid @RequestBody LlmApiKeyConfigForm form) {
        boolean result = apiKeyConfigService.saveApiKeyConfig(form);
        return Result.judge(result);
    }

    @Operation(summary = "修改API-KEY配置")
    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('sys:llm-api-key-config:update')")
    @Log(module = LogModuleEnum.LLM_CONFIG, value = ActionTypeEnum.UPDATE)
    public Result<?> updateApiKeyConfig(@PathVariable Long id, @RequestBody LlmApiKeyConfigForm form) {
        boolean result = apiKeyConfigService.updateApiKeyConfig(id, form);
        return Result.judge(result);
    }

    @Operation(summary = "删除API-KEY配置")
    @DeleteMapping("/{ids}")
    @PreAuthorize("@ss.hasPerm('sys:llm-api-key-config:delete')")
    @Log(module = LogModuleEnum.LLM_CONFIG, value = ActionTypeEnum.DELETE)
    public Result<?> deleteApiKeyConfig(@Parameter(description = "ID，多个以英文逗号拼接") @PathVariable String ids) {
        apiKeyConfigService.deleteApiKeyConfigByIds(Arrays.stream(ids.split(",")).toList());
        return Result.success();
    }
}
