package com.hypersense.boot.agents.service;

import com.hypersense.boot.agents.model.entity.AgentSessionEntity;

import java.util.Collection;
import java.util.List;

/**
 * Agent 会话 DB 层服务(管理 agent_session 表的会话元信息索引).
 * <p>
 * 不碰 Redis 主逻辑——Redis 中的会话详情仍由 {@code AgentServiceImpl} 管理。
 * 本接口职责: 在创建会话时落 DB 索引, 批量查询/重命名/删除时同步 DB。
 * </p>
 *
 * @author Claude
 * @since 2026/6/24
 */
public interface AgentSessionService {

    void saveBinding(String sessionId, Long userId, Long tenantId,
                     String title, String status);

    List<AgentSessionEntity> getBySessionIds(Collection<String> sessionIds);

    void updateTitle(String sessionId, String title);

    void deleteBySessionId(String sessionId);
}
