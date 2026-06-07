package com.hypersense.boot.codegen.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hypersense.boot.codegen.model.entity.GenTable;
import com.hypersense.boot.codegen.model.form.GenConfigForm;

/**
 * 代码生成配置接口
 *
 * @author Ray
 * @since 2.10.0
 */
public interface GenTableService extends IService<GenTable> {

    GenConfigForm getGenTableFormData(String tableName);

    void saveGenConfig(GenConfigForm formData);

    void deleteGenConfig(String tableName);

}
