package com.hypersense.boot.agents.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hypersense.boot.agents.model.form.ProjectForm;
import com.hypersense.boot.agents.model.query.ProjectQuery;
import com.hypersense.boot.agents.model.vo.ProjectVO;
import com.hypersense.boot.agents.service.ProjectService;
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

/**
 * 项目控制器
 *
 * @author Claude
 * @since 2026/6/16
 */
@Tag(name = "项目管理")
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @Operation(summary = "项目分页列表")
    @GetMapping
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.LIST)
    public PageResult<ProjectVO> getProjectPage(ProjectQuery queryParams) {
        IPage<ProjectVO> result = projectService.getProjectPage(queryParams);
        return PageResult.success(result);
    }

    @Operation(summary = "新增项目")
    @PostMapping
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.INSERT)
    public Result<?> addProject(@Valid @RequestBody ProjectForm projectForm) {
        boolean result = projectService.saveProject(projectForm);
        return Result.judge(result);
    }

    @Operation(summary = "获取项目详情")
    @GetMapping("/{id}")
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.OTHER)
    public Result<ProjectVO> getProject(@Parameter(description = "项目ID") @PathVariable Long id) {
        ProjectVO projectVO = projectService.getProjectPage(
                new ProjectQuery()).getRecords().stream()
                .filter(vo -> vo.getId().equals(id))
                .findFirst()
                .orElse(null);
        return Result.success(projectVO);
    }

    @Operation(summary = "获取项目表单数据")
    @GetMapping("/{id}/form")
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.OTHER)
    public Result<ProjectForm> getProjectForm(@Parameter(description = "项目ID") @PathVariable Long id) {
        ProjectForm projectForm = projectService.getProjectForm(id);
        return Result.success(projectForm);
    }

    @Operation(summary = "修改项目")
    @PutMapping("/{id}")
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.UPDATE)
    public Result<?> updateProject(@Parameter(description = "项目ID") @PathVariable Long id,
                                    @Valid @RequestBody ProjectForm projectForm) {
        boolean result = projectService.updateProject(id, projectForm);
        return Result.judge(result);
    }

    @Operation(summary = "删除项目")
    @DeleteMapping("/{ids}")
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.DELETE)
    public Result<Void> deleteProjects(
            @Parameter(description = "删除项目，多个以英文逗号(,)拼接") @PathVariable String ids) {
        projectService.deleteProjects(ids);
        return Result.success();
    }

}
