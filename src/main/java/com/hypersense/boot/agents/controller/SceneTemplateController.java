package com.hypersense.boot.agents.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hypersense.boot.common.annotation.Log;
import com.hypersense.boot.common.enums.ActionTypeEnum;
import com.hypersense.boot.common.enums.LogModuleEnum;
import com.hypersense.boot.common.result.PageResult;
import com.hypersense.boot.common.result.Result;
import com.hypersense.boot.system.model.form.SceneTemplateConfigForm;
import com.hypersense.boot.system.model.query.SceneTemplateConfigQuery;
import com.hypersense.boot.system.model.vo.SceneTemplateConfigVO;
import com.hypersense.boot.system.service.SceneTemplateConfigService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 场景模板控制器（业务端）
 *
 * <p>路径 {@code /api/v1/scene-templates} 对接前端已有调用；
 * 内部不写业务逻辑，全部委托给 {@link SceneTemplateConfigService}，
 * 与管理端 {@code /api/v1/scene-templates-configs} 共用同一份实现。</p>
 *
 * @author Claude
 * @since 2026/6/24
 */
@Tag(name = "场景模板（业务端）")
@RestController
@RequestMapping("/api/v1/scene-templates")
@RequiredArgsConstructor
@Validated
public class SceneTemplateController {

    private final SceneTemplateConfigService sceneTemplateConfigService;

    @Operation(summary = "场景模板分页列表")
    @GetMapping
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.LIST)
    public PageResult<SceneTemplateConfigVO> getSceneTemplatePage(SceneTemplateConfigQuery query) {
        IPage<SceneTemplateConfigVO> result = sceneTemplateConfigService.getSceneTemplateConfigPage(query);
        return PageResult.success(result);
    }

    @Operation(summary = "获取所有 UI 分类（去重，仅已发布）")
    @GetMapping("/ui-categories")
    public Result<List<String>> getUiCategories() {
        return Result.success(sceneTemplateConfigService.getDistinctUiCategories());
    }

    @Operation(summary = "获取场景模板详情")
    @GetMapping("/{id}")
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.OTHER)
    public Result<SceneTemplateConfigVO> getSceneTemplateDetail(
            @Parameter(description = "模板ID") @PathVariable Long id) {
        SceneTemplateConfigVO vo = sceneTemplateConfigService.getSceneTemplateConfigDetail(id);
        return Result.success(vo);
    }

    @Operation(summary = "获取场景模板表单数据")
    @GetMapping("/{id}/form")
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.OTHER)
    public Result<SceneTemplateConfigForm> getSceneTemplateForm(
            @Parameter(description = "模板ID") @PathVariable Long id) {
        SceneTemplateConfigForm form = sceneTemplateConfigService.getSceneTemplateConfigForm(id);
        return Result.success(form);
    }

    @Operation(summary = "新增场景模板")
    @PostMapping
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.INSERT)
    public Result<?> addSceneTemplate(@Valid @RequestBody SceneTemplateConfigForm form) {
        boolean result = sceneTemplateConfigService.saveSceneTemplateConfig(form);
        return Result.judge(result);
    }

    @Operation(summary = "修改场景模板")
    @PutMapping("/{id}")
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.UPDATE)
    public Result<?> updateSceneTemplate(
            @Parameter(description = "模板ID") @PathVariable Long id,
            @Valid @RequestBody SceneTemplateConfigForm form) {
        boolean result = sceneTemplateConfigService.updateSceneTemplateConfig(id, form);
        return Result.judge(result);
    }

    @Operation(summary = "删除场景模板")
    @DeleteMapping("/{ids}")
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.DELETE)
    public Result<Void> deleteSceneTemplates(
            @Parameter(description = "模板ID，多个以英文逗号(,)拼接") @PathVariable String ids) {
        sceneTemplateConfigService.deleteSceneTemplateConfigs(ids);
        return Result.success();
    }

    @Operation(summary = "从本地目录批量导入官方场景模板")
    @PostMapping("/import-dir")
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.IMPORT)
    public Result<Integer> importFromDir(
            @Parameter(description = "本地模板根目录绝对路径") @RequestParam String dir) {
        int count = sceneTemplateConfigService.importFromDir(dir);
        return Result.success(count);
    }

}
