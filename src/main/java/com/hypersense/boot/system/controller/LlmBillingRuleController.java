package com.hypersense.boot.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hypersense.boot.common.annotation.Log;
import com.hypersense.boot.common.annotation.RepeatSubmit;
import com.hypersense.boot.common.enums.ActionTypeEnum;
import com.hypersense.boot.common.enums.LogModuleEnum;
import com.hypersense.boot.common.result.PageResult;
import com.hypersense.boot.common.result.Result;
import com.hypersense.boot.system.model.form.LlmBillingRuleForm;
import com.hypersense.boot.system.model.query.LlmBillingRuleQuery;
import com.hypersense.boot.system.model.vo.LlmBillingRuleVO;
import com.hypersense.boot.system.service.LlmBillingRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Arrays;

@Tag(name = "LLM计费规则")
@RestController
@RequestMapping("/api/v1/llm/billing-rules")
@RequiredArgsConstructor
public class LlmBillingRuleController {

    private final LlmBillingRuleService billingRuleService;

    @Operation(summary = "计费规则分页列表")
    @GetMapping
    @PreAuthorize("@ss.hasPerm('sys:llm-billing-rule:list')")
    @Log(module = LogModuleEnum.LLM_CONFIG, value = ActionTypeEnum.LIST)
    public PageResult<LlmBillingRuleVO> getBillingRulePage(LlmBillingRuleQuery queryParams) {
        Page<LlmBillingRuleVO> result = billingRuleService.getBillingRulePage(queryParams);
        return PageResult.success(result);
    }

    @Operation(summary = "获取计费规则表单")
    @PreAuthorize("@ss.hasPerm('sys:llm-billing-rule:update')")
    @GetMapping("/{id}/form")
    public Result<LlmBillingRuleForm> getBillingRuleForm(@PathVariable Long id) {
        LlmBillingRuleForm form = billingRuleService.getBillingRuleForm(id);
        return Result.success(form);
    }

    @Operation(summary = "新增计费规则")
    @PostMapping
    @PreAuthorize("@ss.hasPerm('sys:llm-billing-rule:create')")
    @RepeatSubmit
    @Log(module = LogModuleEnum.LLM_CONFIG, value = ActionTypeEnum.INSERT)
    public Result<?> saveBillingRule(@Valid @RequestBody LlmBillingRuleForm form) {
        boolean result = billingRuleService.saveBillingRule(form);
        return Result.judge(result);
    }

    @Operation(summary = "修改计费规则")
    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('sys:llm-billing-rule:update')")
    @Log(module = LogModuleEnum.LLM_CONFIG, value = ActionTypeEnum.UPDATE)
    public Result<?> updateBillingRule(@PathVariable Long id, @RequestBody LlmBillingRuleForm form) {
        boolean result = billingRuleService.updateBillingRule(id, form);
        return Result.judge(result);
    }

    @Operation(summary = "删除计费规则")
    @DeleteMapping("/{ids}")
    @PreAuthorize("@ss.hasPerm('sys:llm-billing-rule:delete')")
    @Log(module = LogModuleEnum.LLM_CONFIG, value = ActionTypeEnum.DELETE)
    public Result<?> deleteBillingRule(@Parameter(description = "ID，多个以英文逗号拼接") @PathVariable String ids) {
        billingRuleService.deleteBillingRuleByIds(Arrays.stream(ids.split(",")).toList());
        return Result.success();
    }
}
