package com.hypersense.boot.agents.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hypersense.boot.agents.model.entity.AgentSessionEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent 会话持久层接口（DB 层，仅用于项目-会话绑定索引）。
 * <p>
 * 注意：本 Mapper 与 Redis 主存储无关，会话详情仍走 {@code AgentServiceImpl} 的 Redis 路径。
 * </p>
 *
 * @author Claude
 * @since 2026/6/24
 */
@Mapper
public interface AgentSessionMapper extends BaseMapper<AgentSessionEntity> {
}
