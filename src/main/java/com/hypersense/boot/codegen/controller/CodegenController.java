package com.hypersense.boot.codegen.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hypersense.boot.common.annotation.Log;
import com.hypersense.boot.common.enums.ActionTypeEnum;
import com.hypersense.boot.common.enums.LogModuleEnum;
import com.hypersense.boot.common.result.PageResult;
import com.hypersense.boot.common.result.Result;
import com.hypersense.boot.codegen.config.CodegenProperties;
import com.hypersense.boot.codegen.model.form.GenConfigForm;
import com.hypersense.boot.codegen.model.query.TableQuery;
import com.hypersense.boot.codegen.model.vo.CodegenPreviewVO;
import com.hypersense.boot.codegen.model.vo.TablePageVO;
import com.hypersense.boot.codegen.service.CodegenService;
import com.hypersense.boot.codegen.service.GenTableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 代码生成器控制层
 *
 * @author Ray
 * @since 2.10.0
 */
@Tag(name = "11.代码生成")
@RestController
@RequestMapping("/api/v1/codegen")
@RequiredArgsConstructor
@Slf4j
public class CodegenController {

    private final CodegenService codegenService;
    private final GenTableService genTableService;
    private final CodegenProperties codegenProperties;

    @Operation(summary = "获取数据表分页列表")
    @GetMapping("/table")
    @Log(module = LogModuleEnum.CODEGEN, value = ActionTypeEnum.LIST)
    public PageResult<TablePageVO> getTablePage(
            TableQuery queryParams
    ) {
        Page<TablePageVO> result = codegenService.getTablePage(queryParams);
        return PageResult.success(result);
    }

    @Operation(summary = "获取代码生成配置")
    @GetMapping("/{tableName}/config")
    @Log(module = LogModuleEnum.CODEGEN, value = ActionTypeEnum.OTHER)
    public Result<GenConfigForm> getGenTableFormData(
            @Parameter(description = "表名", example = "sys_user") @PathVariable String tableName
    ) {
        GenConfigForm formData = genTableService.getGenTableFormData(tableName);
        return Result.success(formData);
    }

    @Operation(summary = "保存代码生成配置")
    @PostMapping("/{tableName}/config")
    @Log(module = LogModuleEnum.CODEGEN, value = ActionTypeEnum.OTHER)
    public Result<?> saveGenConfig(@RequestBody GenConfigForm formData) {
        genTableService.saveGenConfig(formData);
        return Result.success();
    }

    @Operation(summary = "删除代码生成配置")
    @DeleteMapping("/{tableName}/config")
    @Log(module = LogModuleEnum.CODEGEN, value = ActionTypeEnum.DELETE)
    public Result<?> deleteGenConfig(
            @Parameter(description = "表名", example = "sys_user") @PathVariable String tableName
    ) {
        genTableService.deleteGenConfig(tableName);
        return Result.success();
    }

    @Operation(summary = "获取预览生成代码")
    @GetMapping("/{tableName}/preview")
    @Log(module = LogModuleEnum.CODEGEN, value = ActionTypeEnum.OTHER)
    public Result<List<CodegenPreviewVO>> getTablePreviewData(@PathVariable String tableName,
                                                              @RequestParam(value = "pageType", required = false, defaultValue = "classic") String pageType,
                                                              @RequestParam(value = "type", required = false, defaultValue = "ts") String type) {
        List<CodegenPreviewVO> list = codegenService.getCodegenPreviewData(tableName, pageType, type);
        return Result.success(list);
    }

    @Operation(summary = "下载代码")
    @GetMapping("/{tableName}/download")
    @Log(module = LogModuleEnum.CODEGEN, value = ActionTypeEnum.OTHER)
    public void downloadZip(HttpServletResponse response, @PathVariable String tableName,
                            @RequestParam(value = "pageType", required = false, defaultValue = "classic") String pageType,
                            @RequestParam(value = "type", required = false, defaultValue = "ts") String type) {
        String[] tableNames = tableName.split(",");
        byte[] data = codegenService.downloadCode(tableNames, pageType, type);

        response.reset();
        response.setHeader("Content-Disposition", "attachment; filename=" + URLEncoder.encode(codegenProperties.getDownloadFileName(), StandardCharsets.UTF_8));
        response.setContentType("application/octet-stream; charset=UTF-8");

        try (ServletOutputStream outputStream = response.getOutputStream()) {
            outputStream.write(data);
            outputStream.flush();
        } catch (IOException e) {
            log.error("Error while writing the zip file to response", e);
            throw new RuntimeException("Failed to write the zip file to response", e);
        }
    }
}
