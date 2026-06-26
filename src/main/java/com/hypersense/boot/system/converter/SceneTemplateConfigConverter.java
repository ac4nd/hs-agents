package com.hypersense.boot.system.converter;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hypersense.boot.system.model.entity.SceneTemplateConfig;
import com.hypersense.boot.system.model.form.SceneTemplateConfigForm;
import com.hypersense.boot.system.model.vo.SceneTemplateConfigVO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

/**
 * 场景模板配置对象转换器
 *
 * @author Claude
 * @since 2026/6/24
 */
@Mapper(componentModel = "spring")
public interface SceneTemplateConfigConverter {

    SceneTemplateConfigVO toVO(SceneTemplateConfig entity);

    Page<SceneTemplateConfigVO> toPageVO(Page<SceneTemplateConfig> page);

    SceneTemplateConfig toEntity(SceneTemplateConfigForm form);

    SceneTemplateConfigForm toForm(SceneTemplateConfig entity);

    /**
     * 将表单字段更新到已有实体（用于修改场景，避免覆盖 id/tenantId 等字段）
     */
    void updateEntity(SceneTemplateConfigForm form, @MappingTarget SceneTemplateConfig entity);

}
