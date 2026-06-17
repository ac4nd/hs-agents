package com.hypersense.boot.agents.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hypersense.boot.agents.model.entity.AgentTemplate;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent 模板持久层接口
 *
 * @author Claude
 * @since 2026/6/16
 */
@Mapper
public interface AgentTemplateMapper extends BaseMapper<AgentTemplate> {

}
