package com.hypersense.boot.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hypersense.boot.common.annotation.Log;
import com.hypersense.boot.common.annotation.RepeatSubmit;
import com.hypersense.boot.common.enums.ActionTypeEnum;
import com.hypersense.boot.common.enums.LogModuleEnum;
import com.hypersense.boot.common.result.PageResult;
import com.hypersense.boot.common.result.Result;
import com.hypersense.boot.system.model.form.LlmVendorConfigForm;
import com.hypersense.boot.system.model.query.LlmVendorConfigQuery;
import com.hypersense.boot.system.model.vo.LlmVendorConfigVO;
import com.hypersense.boot.system.service.LlmVendorConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Arrays;

@Tag(name = "LLM厂商配置")
@RestController
@RequestMapping("/api/v1/llm/vendor-configs")
@RequiredArgsConstructor
public class LlmVendorConfigController {

    private final LlmVendorConfigService vendorConfigService;

    @Operation(summary = "厂商配置分页列表")
    @GetMapping
    @PreAuthorize("@ss.hasPerm('sys:llm-vendor-config:list')")
    @Log(module = LogModuleEnum.LLM_CONFIG, value = ActionTypeEnum.LIST)
    public PageResult<LlmVendorConfigVO> getVendorConfigPage(LlmVendorConfigQuery queryParams) {
        Page<LlmVendorConfigVO> result = vendorConfigService.getVendorConfigPage(queryParams);
        return PageResult.success(result);
    }

    @Operation(summary = "获取厂商配置表单")
    @PreAuthorize("@ss.hasPerm('sys:llm-vendor-config:update')")
    @GetMapping("/{id}/form")
    public Result<LlmVendorConfigForm> getVendorConfigForm(@PathVariable Long id) {
        LlmVendorConfigForm form = vendorConfigService.getVendorConfigForm(id);
        return Result.success(form);
    }

    @Operation(summary = "新增厂商配置")
    @PostMapping
    @PreAuthorize("@ss.hasPerm('sys:llm-vendor-config:create')")
    @RepeatSubmit
    @Log(module = LogModuleEnum.LLM_CONFIG, value = ActionTypeEnum.INSERT)
    public Result<?> saveVendorConfig(@Valid @RequestBody LlmVendorConfigForm form) {
        boolean result = vendorConfigService.saveVendorConfig(form);
        return Result.judge(result);
    }

    @Operation(summary = "修改厂商配置")
    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('sys:llm-vendor-config:update')")
    @Log(module = LogModuleEnum.LLM_CONFIG, value = ActionTypeEnum.UPDATE)
    public Result<?> updateVendorConfig(@PathVariable Long id, @RequestBody LlmVendorConfigForm form) {
        boolean result = vendorConfigService.updateVendorConfig(id, form);
        return Result.judge(result);
    }

    @Operation(summary = "删除厂商配置")
    @DeleteMapping("/{ids}")
    @PreAuthorize("@ss.hasPerm('sys:llm-vendor-config:delete')")
    @Log(module = LogModuleEnum.LLM_CONFIG, value = ActionTypeEnum.DELETE)
    public Result<?> deleteVendorConfig(@Parameter(description = "ID，多个以英文逗号拼接") @PathVariable String ids) {
        vendorConfigService.deleteVendorConfigByIds(Arrays.stream(ids.split(",")).toList());
        return Result.success();
    }
}
