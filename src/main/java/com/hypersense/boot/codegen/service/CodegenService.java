package com.hypersense.boot.codegen.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hypersense.boot.codegen.model.query.TableQuery;
import com.hypersense.boot.codegen.model.vo.CodegenPreviewVO;
import com.hypersense.boot.codegen.model.vo.TablePageVO;

import java.util.List;

/**
 * 代码生成配置接口
 *
 * @author Ray
 * @since 2.10.0
 */
public interface CodegenService {

    Page<TablePageVO> getTablePage(TableQuery queryParams);

    List<CodegenPreviewVO> getCodegenPreviewData(String tableName, String pageType, String type);

    byte[] downloadCode(String[] tableNames, String pageType, String type);
}
