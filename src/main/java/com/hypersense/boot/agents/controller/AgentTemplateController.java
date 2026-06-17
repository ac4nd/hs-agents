package com.hypersense.boot.agents.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hypersense.boot.agents.model.form.AgentTemplateForm;
import com.hypersense.boot.agents.model.query.AgentTemplateQuery;
import com.hypersense.boot.agents.model.vo.AgentTemplateVO;
import com.hypersense.boot.agents.service.AgentTemplateService;
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
 * Agent 模板控制器
 *
 * @author Claude
 * @since 2026/6/16
 */
@Tag(name = "Agent模板管理")
@RestController
@RequestMapping("/api/v1/agent-templates")
@RequiredArgsConstructor
public class AgentTemplateController {

    private final AgentTemplateService agentTemplateService;

    @Operation(summary = "Agent模板分页列表")
    @GetMapping
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.LIST)
    public PageResult<AgentTemplateVO> getAgentTemplatePage(AgentTemplateQuery queryParams) {
        IPage<AgentTemplateVO> result = agentTemplateService.getAgentTemplatePage(queryParams);
        return PageResult.success(result);
    }

    @Operation(summary = "新增Agent模板")
    @PostMapping
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.INSERT)
    public Result<?> addAgentTemplate(@Valid @RequestBody AgentTemplateForm agentTemplateForm) {
        boolean result = agentTemplateService.saveAgentTemplate(agentTemplateForm);
        return Result.judge(result);
    }

    @Operation(summary = "获取Agent模板详情")
    @GetMapping("/{id}")
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.OTHER)
    public Result<AgentTemplateVO> getAgentTemplate(@Parameter(description = "模板ID") @PathVariable Long id) {
        AgentTemplateVO agentTemplateVO = agentTemplateService.getAgentTemplatePage(
                new AgentTemplateQuery()).getRecords().stream()
                .filter(vo -> vo.getId().equals(id))
                .findFirst()
                .orElse(null);
        return Result.success(agentTemplateVO);
    }

    @Operation(summary = "获取Agent模板表单数据")
    @GetMapping("/{id}/form")
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.OTHER)
    public Result<AgentTemplateForm> getAgentTemplateForm(@Parameter(description = "模板ID") @PathVariable Long id) {
        AgentTemplateForm agentTemplateForm = agentTemplateService.getAgentTemplateForm(id);
        return Result.success(agentTemplateForm);
    }

    @Operation(summary = "修改Agent模板")
    @PutMapping("/{id}")
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.UPDATE)
    public Result<?> updateAgentTemplate(@Parameter(description = "模板ID") @PathVariable Long id,
                                          @Valid @RequestBody AgentTemplateForm agentTemplateForm) {
        boolean result = agentTemplateService.updateAgentTemplate(id, agentTemplateForm);
        return Result.judge(result);
    }

    @Operation(summary = "删除Agent模板")
    @DeleteMapping("/{ids}")
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.DELETE)
    public Result<Void> deleteAgentTemplates(
            @Parameter(description = "删除模板，多个以英文逗号(,)拼接") @PathVariable String ids) {
        agentTemplateService.deleteAgentTemplates(ids);
        return Result.success();
    }

}
