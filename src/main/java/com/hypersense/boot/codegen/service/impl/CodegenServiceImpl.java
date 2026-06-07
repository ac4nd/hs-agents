package com.hypersense.boot.codegen.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.file.FileNameUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.template.Template;
import cn.hutool.extra.template.TemplateConfig;
import cn.hutool.extra.template.TemplateEngine;
import cn.hutool.extra.template.TemplateUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hypersense.boot.common.exception.BusinessException;
import com.hypersense.boot.codegen.config.CodegenProperties;
import com.hypersense.boot.codegen.enums.JavaTypeEnum;
import com.hypersense.boot.codegen.mapper.DatabaseMapper;
import com.hypersense.boot.codegen.model.entity.GenTable;
import com.hypersense.boot.codegen.model.entity.GenTableColumn;
import com.hypersense.boot.codegen.model.query.TableQuery;
import com.hypersense.boot.codegen.model.vo.CodegenPreviewVO;
import com.hypersense.boot.codegen.model.vo.TablePageVO;
import com.hypersense.boot.codegen.service.CodegenService;
import com.hypersense.boot.codegen.service.GenTableColumnService;
import com.hypersense.boot.codegen.service.GenTableService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 代码生成服务实现类
 *
 * @author Ray
 * @since 2.10.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CodegenServiceImpl implements CodegenService {

    private final DatabaseMapper databaseMapper;
    private final CodegenProperties codegenProperties;
    private final GenTableService genTableService;
    private final GenTableColumnService genTableColumnService;

    @Override
    public Page<TablePageVO> getTablePage(TableQuery queryParams) {
        Page<TablePageVO> page = new Page<>(queryParams.getPageNum(), queryParams.getPageSize());
        List<String> excludeTables = codegenProperties.getExcludeTables();
        queryParams.setExcludeTables(excludeTables);
        return databaseMapper.getTablePage(page, queryParams);
    }

    @Override
    public List<CodegenPreviewVO> getCodegenPreviewData(String tableName, String pageType, String type) {
        List<CodegenPreviewVO> list = new ArrayList<>();

        GenTable genTable = genTableService.getOne(new LambdaQueryWrapper<GenTable>()
                .eq(GenTable::getTableName, tableName)
        );
        if (genTable == null) {
            throw new BusinessException("未找到表生成配置");
        }

        List<GenTableColumn> fieldConfigs = genTableColumnService.list(new LambdaQueryWrapper<GenTableColumn>()
                .eq(GenTableColumn::getTableId, genTable.getId())
                .orderByAsc(GenTableColumn::getFieldSort)
        );
        if (CollectionUtil.isEmpty(fieldConfigs)) {
            throw new BusinessException("未找到字段生成配置");
        }

        Map<String, CodegenProperties.TemplateConfig> templateConfigs = codegenProperties.getTemplateConfigs();
        String frontendType = StrUtil.blankToDefault(type, "ts").toLowerCase();
        for (Map.Entry<String, CodegenProperties.TemplateConfig> templateConfigEntry : templateConfigs.entrySet()) {
            CodegenPreviewVO previewVO = new CodegenPreviewVO();

            CodegenProperties.TemplateConfig templateConfig = templateConfigEntry.getValue();
            String templateName = templateConfigEntry.getKey();
            if ("js".equals(frontendType) && "API_TYPES".equals(templateName)) {
                continue;
            }

            String effectiveTemplatePath = resolveFrontendTemplatePath(templateName, templateConfig, frontendType);
            String extension = resolveFrontendExtension(templateName, templateConfig, frontendType);

            String entityName = genTable.getEntityName();
            String fileName = getFileName(entityName, templateName, extension);
            previewVO.setFileName(fileName);
            previewVO.setScope(resolveScope(templateName));
            previewVO.setLanguage(resolveLanguage(fileName));

            String packageName = genTable.getPackageName();
            String moduleName = genTable.getModuleName();
            String subpackageName = templateConfig.getSubpackageName();
            String filePath = getFilePath(templateName, moduleName, packageName, subpackageName, entityName);
            previewVO.setPath(filePath);

            String finalType = StrUtil.blankToDefault(genTable.getPageType(), pageType);
            String content = getCodeContent(effectiveTemplatePath, templateConfig.getSubpackageName(), genTable, fieldConfigs, finalType);
            previewVO.setContent(content);

            list.add(previewVO);
        }
        return list;
    }

    private String resolveFrontendTemplatePath(String templateName,
                                              CodegenProperties.TemplateConfig templateConfig,
                                              String frontendType) {
        if (!"js".equals(frontendType)) {
            return templateConfig.getTemplatePath();
        }
        if ("API".equals(templateName)) {
            return "codegen/frontend/js/api.js.vm";
        }
        if ("VIEW".equals(templateName)) {
            return "codegen/frontend/js/index.js.vue.vm";
        }
        return templateConfig.getTemplatePath();
    }

    private String resolveFrontendExtension(String templateName,
                                           CodegenProperties.TemplateConfig templateConfig,
                                           String frontendType) {
        if (!"js".equals(frontendType)) {
            return templateConfig.getExtension();
        }
        if ("API".equals(templateName) || "API_TYPES".equals(templateName)) {
            return ".js";
        }
        return templateConfig.getExtension();
    }

    private String resolveScope(String templateName) {
        return switch (templateName) {
            case "API", "API_TYPES", "VIEW" -> "frontend";
            default -> "backend";
        };
    }

    private String resolveLanguage(String fileName) {
        return FileNameUtil.extName(fileName).toLowerCase();
    }

    private String getFileName(String entityName, String templateName, String extension) {
        if ("Entity".equals(templateName)) {
            return entityName + extension;
        } else if ("MapperXml".equals(templateName)) {
            return entityName + "Mapper" + extension;
        } else if ("API".equals(templateName)) {
            return "index" + extension;
        } else if ("API_TYPES".equals(templateName)) {
            return "types" + extension;
        } else if ("VIEW".equals(templateName)) {
            return "index.vue";
        }
        return entityName + templateName + extension;
    }

    private String getFilePath(String templateName, String moduleName, String packageName, String subPackageName, String entityName) {
        String path;
        if ("MapperXml".equals(templateName)) {
            path = (codegenProperties.getBackendAppName()
                    + File.separator
                    + "src" + File.separator + "main" + File.separator + "resources"
                    + File.separator + subPackageName
                    + File.separator + moduleName
            );
        } else if ("API".equals(templateName)) {
            path = (codegenProperties.getFrontendAppName()
                    + File.separator + "src"
                    + File.separator + "api"
                    + File.separator + moduleName
                    + File.separator + StrUtil.toSymbolCase(entityName, '-')
            );
        } else if ("API_TYPES".equals(templateName)) {
            path = (codegenProperties.getFrontendAppName()
                    + File.separator + "src"
                    + File.separator + "api"
                    + File.separator + moduleName
                    + File.separator + StrUtil.toSymbolCase(entityName, '-')
            );
        } else if ("VIEW".equals(templateName)) {
            path = (codegenProperties.getFrontendAppName()
                    + File.separator + "src"
                    + File.separator + subPackageName
                    + File.separator + moduleName
                    + File.separator + StrUtil.toSymbolCase(entityName, '-')
            );
        } else {
            path = (codegenProperties.getBackendAppName()
                    + File.separator
                    + "src" + File.separator + "main" + File.separator + "java"
                    + File.separator + packageName
                    + File.separator + moduleName
                    + File.separator + subPackageName
            );
        }
        path = path.replace(".", File.separator);
        return path;
    }

    private String getCodeContent(String templatePath,
                                  String subpackageName,
                                  GenTable genTable,
                                  List<GenTableColumn> fieldConfigs,
                                  String pageType) {
        Map<String, Object> bindMap = new HashMap<>();

        String entityName = genTable.getEntityName();

        bindMap.put("packageName", genTable.getPackageName());
        bindMap.put("moduleName", genTable.getModuleName());
        bindMap.put("subpackageName", subpackageName);
        bindMap.put("date", DateUtil.format(new Date(), "yyyy-MM-dd HH:mm"));
        bindMap.put("entityName", entityName);
        bindMap.put("tableName", genTable.getTableName());
        bindMap.put("author", genTable.getAuthor());
        String entityLowerCamel = StrUtil.lowerFirst(entityName);
        String entityKebab = StrUtil.toSymbolCase(entityName, '-');
        String entityUpperSnake = StrUtil.toSymbolCase(entityName, '_').toUpperCase();
        bindMap.put("entityLowerCamel", entityLowerCamel);
        bindMap.put("entityKebab", entityKebab);
        bindMap.put("entityUpperSnake", entityUpperSnake);
        bindMap.put("businessName", genTable.getBusinessName());
        bindMap.put("entityComment", genTable.getBusinessName());
        bindMap.put("fieldConfigs", fieldConfigs);

        boolean hasLocalDateTime = false;
        boolean hasBigDecimal = false;
        boolean hasRequiredField = false;

        for (GenTableColumn fieldConfig : fieldConfigs) {
            if (StrUtil.isBlank(fieldConfig.getFieldType())) {
                fieldConfig.setFieldType(JavaTypeEnum.getJavaTypeByColumnType(fieldConfig.getColumnType()));
            }
            if ("LocalDateTime".equals(fieldConfig.getFieldType()) || "LocalDate".equals(fieldConfig.getFieldType())) {
                hasLocalDateTime = true;
            }
            if ("BigDecimal".equals(fieldConfig.getFieldType())) {
                hasBigDecimal = true;
            }
            if (ObjectUtil.equals(fieldConfig.getIsRequired(), 1)) {
                hasRequiredField = true;
            }
            fieldConfig.setTsType(JavaTypeEnum.getTsTypeByJavaType(fieldConfig.getFieldType()));
        }

        bindMap.put("hasLocalDateTime", hasLocalDateTime);
        bindMap.put("hasBigDecimal", hasBigDecimal);
        bindMap.put("hasRequiredField", hasRequiredField);

        TemplateEngine templateEngine = TemplateUtil.createEngine(new TemplateConfig("templates", TemplateConfig.ResourceMode.CLASSPATH));
        String path = templatePath;
        if ("curd".equalsIgnoreCase(pageType)) {
            if (path.endsWith("index.js.vue.vm")) {
                path = path.replace("index.js.vue.vm", "index.curd.js.vue.vm");
            } else if (path.endsWith("index.vue.vm")) {
                path = path.replace("index.vue.vm", "index.curd.vue.vm");
            }
        }
        Template template = templateEngine.getTemplate(path);
        return template.render(bindMap);
    }

    @Override
    public byte[] downloadCode(String[] tableNames, String ui, String type) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(outputStream)) {
            for (String tableName : tableNames) {
                generateAndZipCode(tableName, zip, ui, type);
            }
            zip.finish();
            return outputStream.toByteArray();
        } catch (IOException e) {
            log.error("Error while generating zip for code download", e);
            throw new RuntimeException("Failed to generate code zip file", e);
        }
    }

    private void generateAndZipCode(String tableName, ZipOutputStream zip, String ui, String type) {
        List<CodegenPreviewVO> codePreviewList = getCodegenPreviewData(tableName, ui, type);
        for (CodegenPreviewVO codePreview : codePreviewList) {
            String fileName = codePreview.getFileName();
            String content = codePreview.getContent();
            String path = codePreview.getPath();
            try {
                ZipEntry zipEntry = new ZipEntry(path + File.separator + fileName);
                zip.putNextEntry(zipEntry);
                zip.write(content.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            } catch (IOException e) {
                log.error("Error while adding file {} to zip", fileName, e);
            }
        }
    }

}
