package com.hypersense.boot.agents.converter;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hypersense.boot.agents.model.entity.Project;
import com.hypersense.boot.agents.model.form.ProjectForm;
import com.hypersense.boot.agents.model.vo.ProjectVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 项目对象转换器
 *
 * @author Claude
 * @since 2026/6/16
 */
@Mapper(componentModel = "spring")
public interface ProjectConverter {

    @Mapping(target = "ownerUserName", ignore = true)
    ProjectVO toVO(Project entity);

    Page<ProjectVO> toPageVO(Page<Project> page);

    Project toEntity(ProjectForm form);

    ProjectForm toForm(Project entity);

}
