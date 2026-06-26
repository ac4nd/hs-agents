package com.hypersense.boot.agents.controller;

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
 * 设计系统业务控制器
 *
 * <p>URL 前缀 /api/v1/design-systems，复用 system 包的 {@link DesignSystemConfigService}
 * 作为统一领域服务，避免 agents 包内重复实现。</p>
 *
 * @author Claude
 * @since 2026/6/16
 */
@Tag(name = "设计系统管理")
@RestController
@RequestMapping("/api/v1/design-systems")
@RequiredArgsConstructor
public class DesignSystemController {

    private final DesignSystemConfigService designSystemConfigService;

    @Operation(summary = "设计系统分页列表")
    @GetMapping
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.LIST)
    public PageResult<DesignSystemConfigPageVO> getDesignSystemPage(DesignSystemConfigQuery queryParams) {
        IPage<DesignSystemConfigPageVO> result = designSystemConfigService.getDesignSystemPage(queryParams);
        return PageResult.success(result);
    }

    @Operation(summary = "设计系统模板列表", description = "获取已发布的官方设计系统，用于下拉选择")
    @GetMapping("/templates")
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.LIST)
    public Result<List<DesignSystemConfigTemplateVO>> listTemplates() {
        List<DesignSystemConfigTemplateVO> list = designSystemConfigService.getTemplates();
        return Result.success(list);
    }

    @Operation(summary = "获取官方模板详情", description = "按模板主键查询 sys_design_system_config_template，区别于 /{id} 的个人体系查询")
    @GetMapping("/templates/{id}")
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.OTHER)
    public Result<DesignSystemConfigTemplateVO> getTemplateDetail(
            @Parameter(description = "模板ID") @PathVariable Long id) {
        DesignSystemConfigTemplateVO vo = designSystemConfigService.getTemplateDetailById(id);
        return Result.success(vo);
    }

    @Operation(summary = "当前用户的设计系统列表", description = "返回当前登录用户的个人设计系统（已发布）")
    @GetMapping("/current-user")
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.LIST)
    public Result<List<DesignSystemConfigPageVO>> listForCurrentUser() {
        return Result.success(designSystemConfigService.listForCurrentUser());
    }

    @Operation(summary = "新增设计系统")
    @PostMapping
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.INSERT)
    public Result<?> addDesignSystem(@Valid @RequestBody DesignSystemConfigForm designSystemForm) {
        boolean result = designSystemConfigService.saveDesignSystem(designSystemForm);
        return Result.judge(result);
    }

    @Operation(summary = "获取设计系统详情")
    @GetMapping("/{id}")
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.OTHER)
    public Result<DesignSystemConfigPageVO> getDesignSystem(
            @Parameter(description = "设计系统ID") @PathVariable Long id) {
        DesignSystemConfigPageVO vo = designSystemConfigService.getDetailById(id);
        return Result.success(vo);
    }

    @Operation(summary = "获取设计系统表单数据")
    @GetMapping("/{id}/form")
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.OTHER)
    public Result<DesignSystemConfigForm> getDesignSystemForm(
            @Parameter(description = "设计系统ID") @PathVariable Long id) {
        DesignSystemConfigForm designSystemForm = designSystemConfigService.getFormById(id);
        return Result.success(designSystemForm);
    }

    @Operation(summary = "修改设计系统")
    @PutMapping("/{id}")
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.UPDATE)
    public Result<?> updateDesignSystem(@Parameter(description = "设计系统ID") @PathVariable Long id,
                                        @Valid @RequestBody DesignSystemConfigForm designSystemForm) {
        boolean result = designSystemConfigService.updateDesignSystem(id, designSystemForm);
        return Result.judge(result);
    }

    @Operation(summary = "删除设计系统")
    @DeleteMapping("/{ids}")
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.DELETE)
    public Result<Void> deleteDesignSystems(
            @Parameter(description = "删除设计系统，多个以英文逗号(,)拼接") @PathVariable String ids) {
        designSystemConfigService.deleteDesignSystems(ids);
        return Result.success();
    }

    @Operation(summary = "发布设计系统")
    @PostMapping("/{id}/publish")
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.UPDATE)
    public Result<Void> publishDesignSystem(@Parameter(description = "设计系统ID") @PathVariable Long id) {
        boolean result = designSystemConfigService.publishDesignSystem(id);
        return Result.judge(result);
    }

}
