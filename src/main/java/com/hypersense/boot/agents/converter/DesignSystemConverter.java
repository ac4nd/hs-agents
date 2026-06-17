package com.hypersense.boot.agents.converter;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hypersense.boot.agents.model.entity.DesignSystem;
import com.hypersense.boot.agents.model.form.DesignSystemForm;
import com.hypersense.boot.agents.model.vo.DesignSystemVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 设计系统对象转换器
 *
 * @author Claude
 * @since 2026/6/16
 */
@Mapper(componentModel = "spring")
public interface DesignSystemConverter {

    @Mapping(target = "ownerUserName", ignore = true)
    @Mapping(target = "categoryLabel", expression = "java(entity.getCategory() != null ? (\"personal\".equals(entity.getCategory()) ? \"个人\" : \"官方\") : null)")
    @Mapping(target = "typeLabel", expression = "java(entity.getType() != null ? (\"web\".equals(entity.getType()) ? \"网页\" : \"移动应用\") : null)")
    @Mapping(target = "publishStatusLabel", expression = "java(entity.getPublishStatus() != null ? (entity.getPublishStatus() == 1 ? \"已发布\" : \"草稿\") : null)")
    DesignSystemVO toVO(DesignSystem entity);

    Page<DesignSystemVO> toPageVO(Page<DesignSystem> page);

    DesignSystem toEntity(DesignSystemForm form);

    DesignSystemForm toForm(DesignSystem entity);

}
