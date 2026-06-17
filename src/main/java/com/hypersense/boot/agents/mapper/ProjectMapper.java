package com.hypersense.boot.agents.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hypersense.boot.agents.model.entity.Project;
import org.apache.ibatis.annotations.Mapper;

/**
 * 项目持久层接口
 *
 * @author Claude
 * @since 2026/6/16
 */
@Mapper
public interface ProjectMapper extends BaseMapper<Project> {

}
