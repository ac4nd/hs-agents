package com.hypersense.boot.system.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hypersense.boot.common.enums.ActionTypeEnum;
import com.hypersense.boot.common.enums.LogModuleEnum;
import com.hypersense.boot.common.result.PageResult;
import com.hypersense.boot.common.result.Result;
import com.hypersense.boot.common.annotation.Log;
import com.hypersense.boot.system.model.form.ConfigForm;
import com.hypersense.boot.system.model.query.ConfigQuery;
import com.hypersense.boot.system.model.vo.ConfigVO;
import com.hypersense.boot.system.service.ConfigService;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * 系统配置前端控制层
 *
 * @author Theo
 * @since 2024-07-30 11:25
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "07.系统配置")
@RequestMapping("/api/v1/configs")
public class ConfigController {

    private final ConfigService configService;

    @Operation(summary = "系统配置分页列表")
    @GetMapping
    @PreAuthorize("@ss.hasPerm('sys:config:list')")
    @Log(module = LogModuleEnum.CONFIG, value = ActionTypeEnum.LIST)
    public PageResult<ConfigVO> page(@ParameterObject ConfigQuery queryParams) {
        IPage<ConfigVO> result = configService.page(queryParams);
        return PageResult.success(result);
    }

    @Operation(summary = "新增系统配置")
    @PostMapping
    @PreAuthorize("@ss.hasPerm('sys:config:create')")
    @Log(module = LogModuleEnum.CONFIG, value = ActionTypeEnum.INSERT)
    public Result<?> save(@RequestBody @Valid ConfigForm configForm) {
        return Result.judge(configService.save(configForm));
    }

    @Operation(summary = "获取系统配置表单数据")
    @GetMapping("/{id}/form")
    @Log(module = LogModuleEnum.CONFIG, value = ActionTypeEnum.OTHER)
    public Result<ConfigForm> getConfigForm(
            @Parameter(description = "系统配置ID") @PathVariable Long id
    ) {
        ConfigForm formData = configService.getConfigFormData(id);
        return Result.success(formData);
    }

    @Operation(summary = "刷新系统配置缓存")
    @PutMapping("/refresh")
    @PreAuthorize("@ss.hasPerm('sys:config:refresh')")
    @Log(module = LogModuleEnum.CONFIG, value = ActionTypeEnum.OTHER)
    public Result<ConfigForm> refreshCache() {
        return Result.judge(configService.refreshCache());
    }

    @Operation(summary = "修改系统配置")
    @PutMapping(value = "/{id}")
    @PreAuthorize("@ss.hasPerm('sys:config:update')")
    @Log(module = LogModuleEnum.CONFIG, value = ActionTypeEnum.UPDATE)
    public Result<?> update(@Valid @PathVariable Long id, @RequestBody ConfigForm configForm) {
        return Result.judge(configService.edit(id, configForm));
    }

    @Operation(summary = "删除系统配置")
    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('sys:config:delete')")
    @Log(module = LogModuleEnum.CONFIG, value = ActionTypeEnum.DELETE)
    public Result<?> delete(@PathVariable Long id) {
        return Result.judge(configService.delete(id));
    }

}
