package com.hypersense.boot.framework.agents.checkpoint;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.AbstractCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * PostgreSQL 检查点持久化实现
 * <p>
 * 参考 Python langgraph.checkpoint.postgres.PostgresSaver 设计，
 * 使用 PostgreSQL + JSONB 存储 Agent 检查点状态。
 * </p>
 *
 * <p>表结构：</p>
 * <pre>
 * agent_checkpoints
 *   - thread_id       TEXT        线程/会话标识
 *   - checkpoint_id   TEXT        检查点唯一 ID
 *   - state           JSONB       状态数据
 *   - node_id         TEXT        当前节点 ID
 *   - next_node_id    TEXT        下一节点 ID
 *   - created_at      TIMESTAMP   创建时间
 *   - idx             INTEGER     线程内顺序索引
 * </pre>
 *
 * @author Claude
 * @since 2026/5/15
 */
@Slf4j
public class PostgresqlSaver extends AbstractCheckpointSaver {

    private static final String THREAD_ID_DEFAULT = "__default__";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    /** 建表 DDL */
    private static final String CREATE_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS agent_checkpoints (
                thread_id       TEXT        NOT NULL,
                checkpoint_id   TEXT        NOT NULL,
                state           JSONB       NOT NULL DEFAULT '{}',
                node_id         TEXT        DEFAULT NULL,
                next_node_id    TEXT        DEFAULT NULL,
                created_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
                idx             INTEGER     NOT NULL DEFAULT 0,
                PRIMARY KEY (thread_id, checkpoint_id)
            )
            """;

    /** 索引 DDL */
    private static final String CREATE_INDEX_SQL = """
            CREATE INDEX IF NOT EXISTS idx_agent_checkpoints_thread_idx
                ON agent_checkpoints (thread_id, idx)
            """;

    /** 查询某线程全部检查点（按 idx 排序） */
    private static final String SELECT_SQL = """
            SELECT checkpoint_id, state, node_id, next_node_id, idx
              FROM agent_checkpoints
             WHERE thread_id = ?
             ORDER BY idx ASC
            """;

    /** 插入新检查点 */
    private static final String INSERT_SQL = """
            INSERT INTO agent_checkpoints (thread_id, checkpoint_id, state, node_id, next_node_id, idx)
            VALUES (?, ?, ?::jsonb, ?, ?, ?)
            """;

    /** 更新已有检查点 */
    private static final String UPDATE_SQL = """
            UPDATE agent_checkpoints
               SET state = ?::jsonb, node_id = ?, next_node_id = ?
             WHERE thread_id = ? AND checkpoint_id = ?
            """;

    /** 删除某线程全部检查点 */
    private static final String DELETE_SQL = """
            DELETE FROM agent_checkpoints WHERE thread_id = ?
            """;

    /** 查询某线程最大 idx */
    private static final String MAX_IDX_SQL = """
            SELECT COALESCE(MAX(idx), -1) FROM agent_checkpoints WHERE thread_id = ?
            """;

    /**
     * 构造方法
     *
     * @param jdbcTemplate  Spring JDBC 模板
     * @param objectMapper  JSON 序列化器
     */
    public PostgresqlSaver(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 初始化数据库表结构（建议在应用启动时调用）
     */
    public void setup() {
        log.info("PostgresqlSaver: 初始化检查点表结构");
        jdbcTemplate.execute(CREATE_TABLE_SQL);
        jdbcTemplate.execute(CREATE_INDEX_SQL);
        log.info("PostgresqlSaver: 检查点表结构初始化完成");
    }

    /**
     * 删除指定线程的全部检查点
     *
     * @param threadId 线程 ID
     */
    public void deleteThread(String threadId) {
        log.info("PostgresqlSaver.deleteThread: threadId={}", threadId);
        jdbcTemplate.update(DELETE_SQL, threadId);
    }

    // ========== 抽象方法实现 ==========

    @Override
    protected LinkedList<Checkpoint> loadCheckpoints(RunnableConfig config) throws Exception {
        String threadId = resolveThreadId(config);
        log.debug("PostgresqlSaver.loadCheckpoints: threadId={}", threadId);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(SELECT_SQL, threadId);

        LinkedList<Checkpoint> checkpoints = new LinkedList<>();
        for (Map<String, Object> row : rows) {
            Checkpoint checkpoint = mapRowToCheckpoint(row);
            checkpoints.add(checkpoint);
        }

        log.debug("PostgresqlSaver.loadCheckpoints: 加载 {} 个检查点", checkpoints.size());
        return checkpoints;
    }

    @Override
    protected void insertedCheckpoint(RunnableConfig config, LinkedList<Checkpoint> checkpoints, Checkpoint checkpoint) throws Exception {
        String threadId = resolveThreadId(config);
        log.debug("PostgresqlSaver.insertedCheckpoint: threadId={}, checkpointId={}", threadId, checkpoint.getId());

        String stateJson = serializeState(checkpoint.getState());

        // 获取当前最大 idx，新检查点追加到末尾
        Integer maxIdx = jdbcTemplate.queryForObject(MAX_IDX_SQL, Integer.class, threadId);
        int nextIdx = (maxIdx != null ? maxIdx : -1) + 1;

        jdbcTemplate.update(INSERT_SQL,
                threadId,
                checkpoint.getId(),
                stateJson,
                checkpoint.getNodeId(),
                checkpoint.getNextNodeId(),
                nextIdx
        );

        log.debug("PostgresqlSaver.insertedCheckpoint: 插入成功, idx={}", nextIdx);
    }

    @Override
    protected void updatedCheckpoint(RunnableConfig config, LinkedList<Checkpoint> checkpoints, Checkpoint checkpoint) throws Exception {
        String threadId = resolveThreadId(config);
        log.debug("PostgresqlSaver.updatedCheckpoint: threadId={}, checkpointId={}", threadId, checkpoint.getId());

        String stateJson = serializeState(checkpoint.getState());

        int rows = jdbcTemplate.update(UPDATE_SQL,
                stateJson,
                checkpoint.getNodeId(),
                checkpoint.getNextNodeId(),
                threadId,
                checkpoint.getId()
        );

        if (rows == 0) {
            log.warn("PostgresqlSaver.updatedCheckpoint: 未找到匹配记录，回退为插入");
            insertedCheckpoint(config, checkpoints, checkpoint);
        }
    }

    @Override
    protected BaseCheckpointSaver.Tag releaseCheckpoints(RunnableConfig config, LinkedList<Checkpoint> checkpoints) throws Exception {
        String threadId = resolveThreadId(config);
        log.debug("PostgresqlSaver.releaseCheckpoints: threadId={}", threadId);

        // 将内存中的检查点同步回数据库后释放
        // 这里保留数据在数据库中，仅返回 Tag 作为快照
        return new BaseCheckpointSaver.Tag(threadId, checkpoints);
    }

    // ========== 私有方法 ==========

    /**
     * 从 RunnableConfig 解析线程 ID
     */
    private String resolveThreadId(RunnableConfig config) {
        return config.threadId().orElse(THREAD_ID_DEFAULT);
    }

    /**
     * 将数据库行映射为 Checkpoint 对象
     */
    private Checkpoint mapRowToCheckpoint(Map<String, Object> row) throws JsonProcessingException {
        String checkpointId = (String) row.get("checkpoint_id");
        String stateJson = (String) row.get("state");
        String nodeId = (String) row.get("node_id");
        String nextNodeId = (String) row.get("next_node_id");

        Map<String, Object> state = deserializeState(stateJson);

        return Checkpoint.builder()
                .id(checkpointId)
                .state(state)
                .nodeId(nodeId)
                .nextNodeId(nextNodeId)
                .build();
    }

    /**
     * 序列化 state Map 为 JSON 字符串
     */
    private String serializeState(Map<String, Object> state) throws JsonProcessingException {
        if (state == null || state.isEmpty()) {
            return "{}";
        }
        return objectMapper.writeValueAsString(state);
    }

    /**
     * 反序列化 JSON 字符串为 state Map
     */
    private Map<String, Object> deserializeState(String json) throws JsonProcessingException {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        return objectMapper.readValue(json, new TypeReference<>() {});
    }
}
