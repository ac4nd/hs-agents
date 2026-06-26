package com.hypersense.boot.agents.service;

import com.hypersense.boot.agents.mapper.AgentSessionMapper;
import com.hypersense.boot.agents.model.entity.AgentSessionEntity;
import com.hypersense.boot.agents.service.impl.AgentSessionServiceImpl;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AgentSessionServiceImpl 单元测试.
 *
 * <p>注意环境差异:
 * <ul>
 *   <li>ServiceImpl 父类的 {@code baseMapper} 字段为 protected 且无 setter,
 *       {@code @InjectMocks} 默认无法注入——改用反射在 setUp 中注入 mock。</li>
 *   <li>MyBatis-Plus 的 {@link LambdaQueryWrapper}/{@link LambdaUpdateWrapper}
 *       在首次使用时需要 entity 的 lambda 元数据缓存(TableInfo),
 *       生产环境由 MyBatis 启动扫描触发, 单测中需显式调用
 *       {@link TableInfoHelper#initTableInfo} 预热一次。</li>
 * </ul>
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class AgentSessionServiceImplTest {

    @Mock
    private AgentSessionMapper agentSessionMapper;

    private AgentSessionServiceImpl service;

    private AgentSessionEntity existingRow;

    @BeforeAll
    static void initTableInfo() {
        // 预热 MyBatis-Plus 的 entity lambda 缓存, 避免单测中 LambdaQueryWrapper 报
        // "can not find lambda cache for this entity"
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant mba = new MapperBuilderAssistant(cfg, "");
        TableInfoHelper.initTableInfo(mba, AgentSessionEntity.class);
    }

    @BeforeEach
    void setUp() throws Exception {
        service = new AgentSessionServiceImpl();
        // 反射注入 ServiceImpl 继承链中的 baseMapper 字段(从当前类向父类逐层查找)
        Field f = null;
        Class<?> c = service.getClass();
        while (c != null && f == null) {
            try {
                f = c.getDeclaredField("baseMapper");
            } catch (NoSuchFieldException ignored) {
                c = c.getSuperclass();
            }
        }
        assertNotNull(f, "未找到 baseMapper 字段");
        f.setAccessible(true);
        f.set(service, agentSessionMapper);

        existingRow = new AgentSessionEntity();
        existingRow.setId(1L);
        existingRow.setSessionId("sess-abc123");
        existingRow.setUserId(100L);
        existingRow.setTenantId(1L);
        existingRow.setTitle("旧标题");
        existingRow.setStatus("CREATED");
    }

    @Test
    void saveBinding_shouldInsertNewRow_whenSessionIdNotExists() {
        when(agentSessionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(agentSessionMapper.insert(any(AgentSessionEntity.class))).thenReturn(1);

        service.saveBinding("sess-new", 100L, 1L, "新会话", "CREATED");

        ArgumentCaptor<AgentSessionEntity> captor = ArgumentCaptor.forClass(AgentSessionEntity.class);
        verify(agentSessionMapper).insert(captor.capture());
        AgentSessionEntity saved = captor.getValue();
        assertEquals("sess-new", saved.getSessionId());
        assertEquals(100L, saved.getUserId());
        assertEquals(1L, saved.getTenantId());
        assertEquals("新会话", saved.getTitle());
        assertEquals("CREATED", saved.getStatus());
    }

    @Test
    void saveBinding_shouldUpdateRow_whenSessionIdAlreadyExists() {
        when(agentSessionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existingRow);
        when(agentSessionMapper.updateById(any(AgentSessionEntity.class))).thenReturn(1);

        service.saveBinding("sess-abc123", 100L, 1L, "新标题", "RUNNING");

        ArgumentCaptor<AgentSessionEntity> captor = ArgumentCaptor.forClass(AgentSessionEntity.class);
        verify(agentSessionMapper).updateById(captor.capture());
        AgentSessionEntity updated = captor.getValue();
        assertEquals(1L, updated.getId());
        assertEquals("新标题", updated.getTitle());
        assertEquals("RUNNING", updated.getStatus());
    }

    @Test
    void getBySessionIds_shouldReturnMatchedRows() {
        when(agentSessionMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(existingRow));

        List<AgentSessionEntity> result = service.getBySessionIds(List.of("sess-abc123"));

        assertEquals(1, result.size());
        assertEquals("sess-abc123", result.get(0).getSessionId());
    }

    @Test
    void getBySessionIds_shouldReturnEmpty_whenInputEmpty() {
        List<AgentSessionEntity> result = service.getBySessionIds(List.of());
        assertTrue(result.isEmpty());
        verify(agentSessionMapper, never()).selectList(any());
    }

    @Test
    void updateTitle_shouldCallUpdate() {
        when(agentSessionMapper.update(nullable(AgentSessionEntity.class), any(LambdaUpdateWrapper.class)))
                .thenReturn(1);

        service.updateTitle("sess-abc123", "新标题");

        verify(agentSessionMapper).update(nullable(AgentSessionEntity.class), any(LambdaUpdateWrapper.class));
    }

    @Test
    void deleteBySessionId_shouldCallDelete() {
        when(agentSessionMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(1);

        service.deleteBySessionId("sess-abc123");

        verify(agentSessionMapper).delete(any(LambdaQueryWrapper.class));
    }
}
