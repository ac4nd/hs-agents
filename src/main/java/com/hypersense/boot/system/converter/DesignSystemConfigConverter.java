package com.hypersense.boot.system.converter;

import com.hypersense.boot.system.model.entity.DesignSystemConfig;
import com.hypersense.boot.system.model.entity.DesignSystemConfigTemplate;
import com.hypersense.boot.system.model.form.DesignSystemConfigForm;
import com.hypersense.boot.system.model.vo.DesignSystemConfigPageVO;
import com.hypersense.boot.system.model.vo.DesignSystemConfigTemplateVO;
import org.mapstruct.Mapper;

/**
 * 设计体系配置对象转换器
 *
 * @author Claude
 * @since 2026/6/24
 */
@Mapper(componentModel = "spring")
public interface DesignSystemConfigConverter {

    /**
     * 表单 -> 实体
     */
    DesignSystemConfig toEntity(DesignSystemConfigForm form);

    /**
     * 实体 -> 表单（用于编辑回显）
     */
    DesignSystemConfigForm toForm(DesignSystemConfig entity);

    /**
     * 实体 -> 分页视图
     */
    DesignSystemConfigPageVO toPageVO(DesignSystemConfig entity);

    /**
     * 模板实体 -> 模板视图
     */
    DesignSystemConfigTemplateVO toTemplateVO(DesignSystemConfigTemplate template);
}
