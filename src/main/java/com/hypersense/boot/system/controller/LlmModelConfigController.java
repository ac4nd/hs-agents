package com.hypersense.boot.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hypersense.boot.common.annotation.Log;
import com.hypersense.boot.common.annotation.RepeatSubmit;
import com.hypersense.boot.common.enums.ActionTypeEnum;
import com.hypersense.boot.common.enums.LogModuleEnum;
import com.hypersense.boot.common.result.PageResult;
import com.hypersense.boot.common.result.Result;
import com.hypersense.boot.system.model.form.LlmModelConfigForm;
import com.hypersense.boot.system.model.query.LlmModelConfigQuery;
import com.hypersense.boot.system.model.vo.LlmModelConfigVO;
import com.hypersense.boot.system.service.LlmModelConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Arrays;

@Tag(name = "LLM模型配置")
@RestController
@RequestMapping("/api/v1/llm/model-configs")
@RequiredArgsConstructor
public class LlmModelConfigController {

    private final LlmModelConfigService modelConfigService;

    @Operation(summary = "模型配置分页列表")
    @GetMapping
    @PreAuthorize("@ss.hasPerm('sys:llm-model-config:list')")
    @Log(module = LogModuleEnum.LLM_CONFIG, value = ActionTypeEnum.LIST)
    public PageResult<LlmModelConfigVO> getModelConfigPage(LlmModelConfigQuery queryParams) {
        Page<LlmModelConfigVO> result = modelConfigService.getModelConfigPage(queryParams);
        return PageResult.success(result);
    }

    @Operation(summary = "获取模型配置表单")
    @PreAuthorize("@ss.hasPerm('sys:llm-model-config:update')")
    @GetMapping("/{id}/form")
    public Result<LlmModelConfigForm> getModelConfigForm(@PathVariable Long id) {
        LlmModelConfigForm form = modelConfigService.getModelConfigForm(id);
        return Result.success(form);
    }

    @Operation(summary = "新增模型配置")
    @PostMapping
    @PreAuthorize("@ss.hasPerm('sys:llm-model-config:create')")
    @RepeatSubmit
    @Log(module = LogModuleEnum.LLM_CONFIG, value = ActionTypeEnum.INSERT)
    public Result<?> saveModelConfig(@Valid @RequestBody LlmModelConfigForm form) {
        boolean result = modelConfigService.saveModelConfig(form);
        return Result.judge(result);
    }

    @Operation(summary = "修改模型配置")
    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('sys:llm-model-config:update')")
    @Log(module = LogModuleEnum.LLM_CONFIG, value = ActionTypeEnum.UPDATE)
    public Result<?> updateModelConfig(@PathVariable Long id, @RequestBody LlmModelConfigForm form) {
        boolean result = modelConfigService.updateModelConfig(id, form);
        return Result.judge(result);
    }

    @Operation(summary = "删除模型配置")
    @DeleteMapping("/{ids}")
    @PreAuthorize("@ss.hasPerm('sys:llm-model-config:delete')")
    @Log(module = LogModuleEnum.LLM_CONFIG, value = ActionTypeEnum.DELETE)
    public Result<?> deleteModelConfig(@Parameter(description = "ID，多个以英文逗号拼接") @PathVariable String ids) {
        modelConfigService.deleteModelConfigByIds(Arrays.stream(ids.split(",")).toList());
        return Result.success();
    }
}
