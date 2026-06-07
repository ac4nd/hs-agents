package com.hypersense.boot.framework.agents.memory;

import com.hypersense.boot.framework.agents.config.AgentProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 长期记忆存储与检索（JdbcTemplate + pgvector）
 *
 * @author Claude
 * @since 2026/5/27
 */
@Slf4j
@RequiredArgsConstructor
public class MemoryRepository {

    private final JdbcTemplate jdbcTemplate;
    private final AgentProperties.MemoryConfig config;

    private static final String VECTOR_CAST = "?::vector";

    public void initialize() {
        int dim = config.getEmbeddingDimensions();
        log.info("MemoryRepository: 初始化 agent_memory 表（embedding 维度={}）", dim);
        jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");

        // 如果表已存在但 embedding 维度不匹配，删除旧索引和表后重建
        if (isTableExists()) {
            int currentDim = queryEmbeddingDimension();
            if (currentDim > 0 && currentDim != dim) {
                log.warn("MemoryRepository: agent_memory.embedding 维度不匹配（现有={}, 目标={}），重建表", currentDim, dim);
                jdbcTemplate.execute("DROP INDEX IF EXISTS idx_memory_embedding");
                jdbcTemplate.execute("DROP TABLE agent_memory");
            }
        }

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS agent_memory (
                    id              BIGSERIAL PRIMARY KEY,
                    tenant_id       BIGINT NOT NULL,
                    user_id         BIGINT NOT NULL,
                    content         TEXT NOT NULL,
                    category        VARCHAR(32) DEFAULT 'fact',
                    embedding       vector(%d),
                    session_id      VARCHAR(64),
                    access_count    INT DEFAULT 0,
                    created_at      TIMESTAMP DEFAULT NOW(),
                    last_accessed_at TIMESTAMP DEFAULT NOW()
                )
                """.formatted(dim));
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_memory_user ON agent_memory(user_id)");
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_memory_embedding
                ON agent_memory USING hnsw (embedding vector_cosine_ops)
                """);
        log.info("MemoryRepository: agent_memory 表初始化完成");
    }

    private boolean isTableExists() {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'agent_memory')",
                Boolean.class));
    }

    private int queryEmbeddingDimension() {
        try {
            return Integer.parseInt(jdbcTemplate.queryForObject("""
                    SELECT regexp_replace(
                        (SELECT a.atttypmod FROM pg_attribute a
                         JOIN pg_class c ON a.attrelid = c.oid
                         WHERE c.relname = 'agent_memory' AND a.attname = 'embedding'),
                        '^(\\d+)$', '\\1')
                    """, String.class));
        } catch (Exception e) {
            // pgvector dimension 从 udt_name 获取更可靠
            try {
                String udtName = jdbcTemplate.queryForObject("""
                        SELECT t.typname FROM pg_attribute a
                        JOIN pg_class c ON a.attrelid = c.oid
                        JOIN pg_type t ON a.atttypid = t.oid
                        WHERE c.relname = 'agent_memory' AND a.attname = 'embedding'
                        """, String.class);
                // udtName 格式: vector2048, vector1536 等
                if (udtName != null && udtName.startsWith("vector")) {
                    return Integer.parseInt(udtName.substring("vector".length()));
                }
            } catch (Exception ignored) {}
            return 0;
        }
    }

    public void store(AgentMemory memory) {
        jdbcTemplate.update("""
                INSERT INTO agent_memory (tenant_id, user_id, content, category, embedding, session_id)
                VALUES (?, ?, ?, ?, %s, ?)
                """.formatted(VECTOR_CAST),
                memory.getTenantId(), memory.getUserId(),
                memory.getContent(), memory.getCategory(),
                toVectorLiteral(memory.getEmbedding()),
                memory.getSessionId());
    }

    /**
     * 向量相似度搜索（带 tenant_id 隔离 + threshold 过滤）
     */
    public List<AgentMemory> search(Long tenantId, Long userId, float[] queryEmbedding,
                                    int limit, double threshold) {
        String vectorLiteral = toVectorLiteral(queryEmbedding);
        return jdbcTemplate.query("""
                SELECT id, tenant_id, user_id, content, category, session_id,
                       access_count, created_at, last_accessed_at
                FROM agent_memory
                WHERE tenant_id = ?
                  AND user_id = ?
                  AND embedding IS NOT NULL
                  AND 1 - (embedding <=> %s) >= ?
                ORDER BY embedding <=> %s
                LIMIT ?
                """.formatted(VECTOR_CAST, VECTOR_CAST),
                memoryRowMapper(),
                tenantId, userId, vectorLiteral, threshold, vectorLiteral, limit);
    }

    public List<AgentMemory> searchByKeyword(Long tenantId, Long userId, String keyword, int limit) {
        return jdbcTemplate.query("""
                SELECT id, tenant_id, user_id, content, category, session_id,
                       access_count, created_at, last_accessed_at
                FROM agent_memory
                WHERE tenant_id = ?
                  AND user_id = ?
                  AND content ILIKE ?
                ORDER BY access_count DESC, created_at DESC
                LIMIT ?
                """, memoryRowMapper(), tenantId, userId, "%" + keyword + "%", limit);
    }

    /**
     * 批量递增访问计数（避免 N+1）
     */
    public void batchIncrementAccessCount(List<Long> memoryIds) {
        if (memoryIds == null || memoryIds.isEmpty()) {
            return;
        }
        String placeholders = String.join(",", memoryIds.stream().map(id -> "?").toList());
        jdbcTemplate.update("""
                UPDATE agent_memory
                SET access_count = access_count + 1, last_accessed_at = NOW()
                WHERE id IN (%s)
                """.formatted(placeholders), memoryIds.toArray());
    }

    public int deleteOlderThan(int days) {
        return jdbcTemplate.update("""
                DELETE FROM agent_memory
                WHERE created_at < NOW() - INTERVAL '1 day' * ?
                """, days);
    }

    // ========== 内部方法 ==========

    private RowMapper<AgentMemory> memoryRowMapper() {
        return (rs, rowNum) -> AgentMemory.builder()
                .id(rs.getLong("id"))
                .tenantId(rs.getLong("tenant_id"))
                .userId(rs.getLong("user_id"))
                .content(rs.getString("content"))
                .category(rs.getString("category"))
                .sessionId(rs.getString("session_id"))
                .accessCount(rs.getInt("access_count"))
                .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                .lastAccessedAt(rs.getTimestamp("last_accessed_at").toLocalDateTime())
                .build();
    }

    private String toVectorLiteral(float[] embedding) {
        if (embedding == null || embedding.length == 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
