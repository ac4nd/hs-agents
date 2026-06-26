package com.hypersense.boot.system.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hypersense.boot.common.annotation.Log;
import com.hypersense.boot.common.enums.ActionTypeEnum;
import com.hypersense.boot.common.enums.LogModuleEnum;
import com.hypersense.boot.common.result.PageResult;
import com.hypersense.boot.common.result.Result;
import com.hypersense.boot.system.model.form.DesignSystemConfigForm;
import com.hypersense.boot.system.model.query.DesignSystemConfigQuery;
import com.hypersense.boot.system.model.vo.DesignSystemConfigPageVO;
import com.hypersense.boot.system.model.vo.DesignSystemConfigTemplateVO;
import com.hypersense.boot.system.service.DesignSystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 设计体系配置管理端控制层
 *
 * <p>管理基础服务，URL 前缀使用 /api/v1/design-systems-configs，与 agents 包的业务接口
 * （/api/v1/design-systems）隔离，避免 bean 名与路径冲突。</p>
 *
 * @author Claude
 * @since 2026/6/24
 */
@Tag(name = "设计体系配置-管理端")
@RestController
@RequestMapping("/api/v1/design-systems-configs")
@RequiredArgsConstructor
public class DesignSystemConfigController {

    private final DesignSystemConfigService designSystemConfigService;

    @Operation(summary = "设计体系配置分页列表")
    @GetMapping
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.LIST)
    public PageResult<DesignSystemConfigPageVO> getDesignSystemPage(DesignSystemConfigQuery query) {
        IPage<DesignSystemConfigPageVO> result = designSystemConfigService.getDesignSystemPage(query);
        return PageResult.success(result);
    }

    @Operation(summary = "获取设计体系配置表单数据")
    @GetMapping("/{id}/form")
    public Result<DesignSystemConfigForm> getDesignSystemConfigForm(
            @Parameter(description = "设计体系配置ID") @PathVariable Long id) {
        DesignSystemConfigForm form = designSystemConfigService.getFormById(id);
        return Result.success(form);
    }

    @Operation(summary = "新建设计体系配置")
    @PostMapping
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.INSERT)
    public Result<?> saveDesignSystem(@RequestBody @Valid DesignSystemConfigForm form) {
        boolean result = designSystemConfigService.saveDesignSystem(form);
        return Result.judge(result);
    }

    @Operation(summary = "更新设计体系配置")
    @PutMapping("/{id}")
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.UPDATE)
    public Result<Void> updateDesignSystem(
            @Parameter(description = "设计体系配置ID") @PathVariable Long id,
            @RequestBody @Validated DesignSystemConfigForm form
    ) {
        boolean result = designSystemConfigService.updateDesignSystem(id, form);
        return Result.judge(result);
    }

    @Operation(summary = "批量删除设计体系配置")
    @DeleteMapping("/{ids}")
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.DELETE)
    public Result<Void> deleteDesignSystems(
            @Parameter(description = "设计体系配置ID，多个以英文逗号(,)分割") @PathVariable String ids
    ) {
        boolean result = designSystemConfigService.deleteDesignSystems(ids);
        return Result.judge(result);
    }

    @Operation(summary = "官方模板列表")
    @GetMapping("/templates")
    public Result<List<DesignSystemConfigTemplateVO>> getTemplates() {
        List<DesignSystemConfigTemplateVO> list = designSystemConfigService.getTemplates();
        return Result.success(list);
    }

    @Operation(summary = "发布设计体系配置")
    @PostMapping("/{id}/publish")
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.UPDATE)
    public Result<Void> publishDesignSystem(
            @Parameter(description = "设计体系配置ID") @PathVariable Long id
    ) {
        boolean result = designSystemConfigService.publishDesignSystem(id);
        return Result.judge(result);
    }
}
