package com.hypersense.boot.agents.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hypersense.boot.agents.mapper.AgentSessionMapper;
import com.hypersense.boot.agents.model.entity.AgentSessionEntity;
import com.hypersense.boot.agents.service.AgentSessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Agent 会话 DB 层服务实现.
 * <p>
 * 继承 ServiceImpl 仅为复用 baseMapper 字段; 不直接暴露其 list/save 等方法。
 * </p>
 * <p>
 * 时间字段: Entity 继承 BaseEntity, insert/updateById 路径下 createTime/updateTime
 * 由 MyMetaObjectHandler 自动填充; 但 update(null, LambdaUpdateWrapper) 路径不触发自动填充,
 * 因此在该路径下显式 set updateTime。
 * </p>
 *
 * @author Claude
 * @since 2026/6/24
 */
@Slf4j
@Service
public class AgentSessionServiceImpl
        extends ServiceImpl<AgentSessionMapper, AgentSessionEntity>
        implements AgentSessionService {

    @Override
    public void saveBinding(String sessionId, Long userId, Long tenantId,
                            String title, String status) {
        if (sessionId == null || sessionId.isBlank()) {
            log.warn("saveBinding 跳过: sessionId 为空");
            return;
        }

        LambdaQueryWrapper<AgentSessionEntity> qw = new LambdaQueryWrapper<>();
        qw.eq(AgentSessionEntity::getSessionId, sessionId);
        AgentSessionEntity existing = baseMapper.selectOne(qw);

        if (existing == null) {
            AgentSessionEntity row = new AgentSessionEntity();
            row.setSessionId(sessionId);
            row.setUserId(userId);
            row.setTenantId(tenantId);
            row.setTitle(title);
            row.setStatus(status);
            baseMapper.insert(row);
            log.info("saveBinding 新增: sessionId={}", sessionId);
        } else {
            existing.setTitle(title);
            existing.setStatus(status);
            baseMapper.updateById(existing);
            log.info("saveBinding 更新: sessionId={}", sessionId);
        }
    }

    @Override
    public List<AgentSessionEntity> getBySessionIds(Collection<String> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<AgentSessionEntity> qw = new LambdaQueryWrapper<>();
        qw.in(AgentSessionEntity::getSessionId, sessionIds);
        return baseMapper.selectList(qw);
    }

    @Override
    public AgentSessionEntity getBySessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        LambdaQueryWrapper<AgentSessionEntity> qw = new LambdaQueryWrapper<>();
        qw.eq(AgentSessionEntity::getSessionId, sessionId);
        // 使用 selectOne 且第二参数 throwEx=false, 多条命中时返回首条而非抛异常,
        // 保证 fallback 路径在脏数据场景下仍可用
        return baseMapper.selectOne(qw, false);
    }

    @Override
    public void updateTitle(String sessionId, String title) {
        if (sessionId == null || sessionId.isBlank()) return;
        LambdaUpdateWrapper<AgentSessionEntity> uw = new LambdaUpdateWrapper<>();
        uw.eq(AgentSessionEntity::getSessionId, sessionId)
                .set(AgentSessionEntity::getTitle, title)
                .set(AgentSessionEntity::getUpdateTime, LocalDateTime.now());
        baseMapper.update(null, uw);
        log.info("updateTitle: sessionId={}, title={}", sessionId, title);
    }

    @Override
    public void deleteBySessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return;
        LambdaQueryWrapper<AgentSessionEntity> qw = new LambdaQueryWrapper<>();
        qw.eq(AgentSessionEntity::getSessionId, sessionId);
        int rows = baseMapper.delete(qw);
        log.info("deleteBySessionId: sessionId={}, affectedRows={}", sessionId, rows);
    }
}
