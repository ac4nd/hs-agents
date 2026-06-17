package com.hypersense.boot.agents.converter;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hypersense.boot.agents.model.entity.AgentTemplate;
import com.hypersense.boot.agents.model.form.AgentTemplateForm;
import com.hypersense.boot.agents.model.vo.AgentTemplateVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Agent 模板对象转换器
 *
 * @author Claude
 * @since 2026/6/16
 */
@Mapper(componentModel = "spring")
public interface AgentTemplateConverter {

    @Mapping(target = "ownerUserName", ignore = true)
    AgentTemplateVO toVO(AgentTemplate entity);

    Page<AgentTemplateVO> toPageVO(Page<AgentTemplate> page);

    AgentTemplate toEntity(AgentTemplateForm form);

    AgentTemplateForm toForm(AgentTemplate entity);

}
