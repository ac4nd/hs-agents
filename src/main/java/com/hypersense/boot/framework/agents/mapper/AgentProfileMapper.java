package com.hypersense.boot.framework.agents.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hypersense.boot.framework.agents.model.AgentProfileEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AgentProfileMapper extends BaseMapper<AgentProfileEntity> {

    @Select("SELECT * FROM sys_agent_profile WHERE profile_id = #{profileId} AND enabled = TRUE LIMIT 1")
    AgentProfileEntity findEnabledByProfileId(@Param("profileId") String profileId);
}
