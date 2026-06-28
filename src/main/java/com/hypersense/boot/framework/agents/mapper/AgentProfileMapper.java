package com.hypersense.boot.framework.agents.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.hypersense.boot.framework.agents.model.AgentProfileEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.type.JdbcType;

@Mapper
public interface AgentProfileMapper extends BaseMapper<AgentProfileEntity> {

    /**
     * 自定义查询：按 profile_id 取启用行。
     * <p>注意：@TableName(autoResultMap=true) 的 typeHandler 仅对 BaseMapper 内置方法（selectById 等）生效；
     * 对自定义 @Select 方法，必须显式声明 @Results 才能把 JSONB 列映射为 JsonNode，否则字段为 null。</p>
     */
    @Select("SELECT * FROM sys_agent_profile WHERE profile_id = #{profileId} AND enabled = TRUE LIMIT 1")
    @Results(id = "agentProfileResult", value = {
            @Result(column = "id", property = "id"),
            @Result(column = "profile_id", property = "profileId"),
            @Result(column = "name", property = "name"),
            @Result(column = "description", property = "description"),
            @Result(column = "system_prompt", property = "systemPrompt"),
            @Result(column = "allowed_tools", property = "allowedTools",
                    typeHandler = JacksonTypeHandler.class, jdbcType = JdbcType.OTHER),
            @Result(column = "plan_strategy", property = "planStrategy"),
            @Result(column = "output_format", property = "outputFormat",
                    typeHandler = JacksonTypeHandler.class, jdbcType = JdbcType.OTHER),
            @Result(column = "lint_rules", property = "lintRules",
                    typeHandler = JacksonTypeHandler.class, jdbcType = JdbcType.OTHER),
            @Result(column = "hitl_policy", property = "hitlPolicy",
                    typeHandler = JacksonTypeHandler.class, jdbcType = JdbcType.OTHER),
            @Result(column = "enabled", property = "enabled"),
            @Result(column = "sort_order", property = "sortOrder"),
            @Result(column = "created_at", property = "createdAt"),
            @Result(column = "updated_at", property = "updatedAt")
    })
    AgentProfileEntity findEnabledByProfileId(@Param("profileId") String profileId);
}
