package com.hypersense.boot.agents.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hypersense.boot.agents.model.form.DesignSystemForm;
import com.hypersense.boot.agents.model.query.DesignSystemQuery;
import com.hypersense.boot.agents.model.vo.DesignSystemVO;
import com.hypersense.boot.agents.service.DesignSystemService;
import com.hypersense.boot.common.enums.ActionTypeEnum;
import com.hypersense.boot.common.enums.LogModuleEnum;
import com.hypersense.boot.common.annotation.Log;
import com.hypersense.boot.common.result.PageResult;
import com.hypersense.boot.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 设计系统控制器
 *
 * @author Claude
 * @since 2026/6/16
 */
@Tag(name = "设计系统管理")
@RestController
@RequestMapping("/api/v1/design-systems")
@RequiredArgsConstructor
public class DesignSystemController {

    private final DesignSystemService designSystemService;

    @Operation(summary = "设计系统分页列表")
    @GetMapping
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.LIST)
    public PageResult<DesignSystemVO> getDesignSystemPage(DesignSystemQuery queryParams) {
        IPage<DesignSystemVO> result = designSystemService.getDesignSystemPage(queryParams);
        return PageResult.success(result);
    }

    @Operation(summary = "设计系统模板列表", description = "获取已发布的官方设计系统，用于下拉选择")
    @GetMapping("/templates")
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.LIST)
    public Result<List<DesignSystemVO>> listTemplates() {
        List<DesignSystemVO> list = designSystemService.listTemplates();
        return Result.success(list);
    }

    @Operation(summary = "新增设计系统")
    @PostMapping
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.INSERT)
    public Result<?> addDesignSystem(@Valid @RequestBody DesignSystemForm designSystemForm) {
        boolean result = designSystemService.saveDesignSystem(designSystemForm);
        return Result.judge(result);
    }

    @Operation(summary = "获取设计系统详情")
    @GetMapping("/{id}")
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.OTHER)
    public Result<DesignSystemVO> getDesignSystem(@Parameter(description = "设计系统ID") @PathVariable Long id) {
        DesignSystemVO designSystemVO = designSystemService.getDesignSystemPage(
                new DesignSystemQuery()).getRecords().stream()
                .filter(vo -> vo.getId().equals(id))
                .findFirst()
                .orElse(null);
        return Result.success(designSystemVO);
    }

    @Operation(summary = "获取设计系统表单数据")
    @GetMapping("/{id}/form")
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.OTHER)
    public Result<DesignSystemForm> getDesignSystemForm(@Parameter(description = "设计系统ID") @PathVariable Long id) {
        DesignSystemForm designSystemForm = designSystemService.getDesignSystemForm(id);
        return Result.success(designSystemForm);
    }

    @Operation(summary = "修改设计系统")
    @PutMapping("/{id}")
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.UPDATE)
    public Result<?> updateDesignSystem(@Parameter(description = "设计系统ID") @PathVariable Long id,
                                         @Valid @RequestBody DesignSystemForm designSystemForm) {
        boolean result = designSystemService.updateDesignSystem(id, designSystemForm);
        return Result.judge(result);
    }

    @Operation(summary = "删除设计系统")
    @DeleteMapping("/{ids}")
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.DELETE)
    public Result<Void> deleteDesignSystems(
            @Parameter(description = "删除设计系统，多个以英文逗号(,)拼接") @PathVariable String ids) {
        designSystemService.deleteDesignSystems(ids);
        return Result.success();
    }

    @Operation(summary = "发布设计系统")
    @PostMapping("/{id}/publish")
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.UPDATE)
    public Result<Void> publishDesignSystem(@Parameter(description = "设计系统ID") @PathVariable Long id) {
        boolean result = designSystemService.publishDesignSystem(id);
        return Result.judge(result);
    }

}
