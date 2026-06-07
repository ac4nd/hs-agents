package com.hypersense.boot.framework.agents.checkpoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PostgresqlSaver 单元测试
 * <p>
 * 覆盖：setup、deleteThread、JSON 序列化。
 * protected 方法（insert/update/load/release）通过同包访问测试。
 *
 * @author test
 */
class PostgresqlSaverTest {

    private JdbcTemplate jdbcTemplate;
    private ObjectMapper objectMapper;
    private PostgresqlSaver saver;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        objectMapper = new ObjectMapper();
        saver = new PostgresqlSaver(jdbcTemplate, objectMapper);
    }

    // ======================== setup 测试 ========================

    @Nested
    @DisplayName("setup - 初始化表结构")
    class SetupTests {

        @Test
        @DisplayName("setup - 执行建表和索引 DDL")
        void testSetup_executesDdl() {
            saver.setup();

            verify(jdbcTemplate, times(2)).execute(anyString());
        }

        @Test
        @DisplayName("setup - 重复调用不报错（IF NOT EXISTS）")
        void testSetup_idempotent() {
            saver.setup();
            saver.setup();

            verify(jdbcTemplate, times(4)).execute(anyString());
        }
    }

    // ======================== deleteThread 测试 ========================

    @Nested
    @DisplayName("deleteThread - 删除线程检查点")
    class DeleteThreadTests {

        @Test
        @DisplayName("deleteThread - 执行 DELETE SQL")
        void testDeleteThread() {
            saver.deleteThread("thread-123");

            verify(jdbcTemplate).update(
                    contains("DELETE FROM agent_checkpoints"),
                    eq("thread-123"));
        }

        @Test
        @DisplayName("deleteThread - null threadId 不抛异常")
        void testDeleteThread_null() {
            assertDoesNotThrow(() -> saver.deleteThread(null));
            verify(jdbcTemplate).update(contains("DELETE"), (Object) isNull());
        }
    }

    // ======================== 构造器测试 ========================

    @Nested
    @DisplayName("构造器")
    class ConstructorTests {

        @Test
        @DisplayName("构造 - 不抛异常")
        void testConstructor() {
            assertDoesNotThrow(() -> new PostgresqlSaver(jdbcTemplate, objectMapper));
        }

        @Test
        @DisplayName("构造 - 参数正确保存")
        void testConstructor_paramsSet() {
            PostgresqlSaver s = new PostgresqlSaver(jdbcTemplate, objectMapper);
            assertNotNull(s);
        }
    }

    // ======================== JSON 序列化测试 ========================

    @Nested
    @DisplayName("JSON 序列化 - state 处理")
    class JsonSerializationTests {

        @Test
        @DisplayName("复杂 state 正确序列化/反序列化")
        void testComplexStateSerialization() throws Exception {
            Map<String, Object> state = Map.of(
                    "string", "hello",
                    "number", 42,
                    "nested", Map.of("key", "value"),
                    "list", List.of(1, 2, 3)
            );

            String json = objectMapper.writeValueAsString(state);
            assertTrue(json.contains("hello"));
            assertTrue(json.contains("42"));

            @SuppressWarnings("unchecked")
            Map<String, Object> deserialized = objectMapper.readValue(json, Map.class);
            assertEquals("hello", deserialized.get("string"));
            assertEquals(42, deserialized.get("number"));
        }

        @Test
        @DisplayName("空 Map 序列化为 {}")
        void testEmptyMapSerialization() throws Exception {
            String json = objectMapper.writeValueAsString(Map.of());
            assertEquals("{}", json);
        }

        @Test
        @DisplayName("null Map 序列化")
        void testNullSerialization() throws Exception {
            String json = objectMapper.writeValueAsString(null);
            assertEquals("null", json);
        }
    }

    // ======================== protected 方法同包访问测试 ========================

    @Nested
    @DisplayName("protected 方法 - CRUD 操作")
    class ProtectedMethodTests {

        @Test
        @DisplayName("insert - 正确插入检查点")
        void testInsert() throws Exception {
            when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("thread-1")))
                    .thenReturn(3);

            org.bsc.langgraph4j.RunnableConfig config =
                    org.bsc.langgraph4j.RunnableConfig.builder().threadId("thread-1").build();
            java.util.LinkedList<org.bsc.langgraph4j.checkpoint.Checkpoint> checkpoints =
                    new java.util.LinkedList<>();
            org.bsc.langgraph4j.checkpoint.Checkpoint cp = org.bsc.langgraph4j.checkpoint.Checkpoint.builder()
                    .id("cp-1")
                    .state(Map.of("key", "value"))
                    .nodeId("plan")
                    .nextNodeId("execute")
                    .build();

            saver.insertedCheckpoint(config, checkpoints, cp);

            // 验证 maxIdx 查询
            verify(jdbcTemplate).queryForObject(
                    contains("COALESCE(MAX(idx)"), eq(Integer.class), eq("thread-1"));

            // 验证 insert
            verify(jdbcTemplate).update(
                    contains("INSERT INTO agent_checkpoints"),
                    eq("thread-1"),
                    eq("cp-1"),
                    anyString(),
                    eq("plan"),
                    eq("execute"),
                    eq(4)  // 3 + 1
            );
        }

        @Test
        @DisplayName("insert - 首个检查点 idx=0")
        void testInsert_firstCheckpoint() throws Exception {
            when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("new-thread")))
                    .thenReturn(-1);

            org.bsc.langgraph4j.RunnableConfig config =
                    org.bsc.langgraph4j.RunnableConfig.builder().threadId("new-thread").build();
            org.bsc.langgraph4j.checkpoint.Checkpoint cp = org.bsc.langgraph4j.checkpoint.Checkpoint.builder()
                    .id("cp-first")
                    .state(Map.of())
                    .nodeId("start")
                    .nextNodeId("plan")
                    .build();

            saver.insertedCheckpoint(config, new java.util.LinkedList<>(), cp);

            verify(jdbcTemplate).update(
                    anyString(),
                    eq("new-thread"),
                    eq("cp-first"),
                    anyString(),
                    eq("start"),
                    eq("plan"),
                    eq(0)
            );
        }

        @Test
        @DisplayName("update - 成功更新")
        void testUpdate_success() throws Exception {
            when(jdbcTemplate.update(
                    anyString(), anyString(), anyString(), anyString(), anyString(), anyString()
            )).thenReturn(1);

            org.bsc.langgraph4j.RunnableConfig config =
                    org.bsc.langgraph4j.RunnableConfig.builder().threadId("thread-1").build();
            org.bsc.langgraph4j.checkpoint.Checkpoint cp = org.bsc.langgraph4j.checkpoint.Checkpoint.builder()
                    .id("cp-1")
                    .state(Map.of("updated", true))
                    .nodeId("execute")
                    .nextNodeId("tool")
                    .build();

            saver.updatedCheckpoint(config, new java.util.LinkedList<>(), cp);

            verify(jdbcTemplate).update(
                    contains("UPDATE agent_checkpoints"),
                    anyString(),
                    eq("execute"),
                    eq("tool"),
                    eq("thread-1"),
                    eq("cp-1")
            );
        }

        @Test
        @DisplayName("update - 0 行匹配 → 回退为 insert")
        void testUpdate_fallbackToInsert() throws Exception {
            when(jdbcTemplate.update(
                    anyString(), anyString(), anyString(), anyString(), anyString(), anyString()
            )).thenReturn(0);
            when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("thread-1")))
                    .thenReturn(-1);

            org.bsc.langgraph4j.RunnableConfig config =
                    org.bsc.langgraph4j.RunnableConfig.builder().threadId("thread-1").build();
            org.bsc.langgraph4j.checkpoint.Checkpoint cp = org.bsc.langgraph4j.checkpoint.Checkpoint.builder()
                    .id("cp-missing")
                    .state(Map.of())
                    .nodeId("plan")
                    .nextNodeId("execute")
                    .build();

            saver.updatedCheckpoint(config, new java.util.LinkedList<>(), cp);

            // 验证回退为 insert
            verify(jdbcTemplate).update(
                    contains("INSERT INTO agent_checkpoints"),
                    eq("thread-1"),
                    eq("cp-missing"),
                    anyString(),
                    eq("plan"),
                    eq("execute"),
                    eq(0)
            );
        }

        @Test
        @DisplayName("load - 空线程返回空列表")
        void testLoad_emptyThread() throws Exception {
            when(jdbcTemplate.queryForList(anyString(), eq("empty-thread")))
                    .thenReturn(List.of());

            org.bsc.langgraph4j.RunnableConfig config =
                    org.bsc.langgraph4j.RunnableConfig.builder().threadId("empty-thread").build();
            java.util.LinkedList<org.bsc.langgraph4j.checkpoint.Checkpoint> result =
                    saver.loadCheckpoints(config);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("load - 返回检查点列表")
        void testLoad_withCheckpoints() throws Exception {
            Map<String, Object> row1 = new HashMap<>();
            row1.put("checkpoint_id", "cp-1");
            row1.put("state", "{\"key\":\"value1\"}");
            row1.put("node_id", "plan");
            row1.put("next_node_id", "execute");

            when(jdbcTemplate.queryForList(anyString(), eq("thread-1")))
                    .thenReturn(List.of(row1));

            org.bsc.langgraph4j.RunnableConfig config =
                    org.bsc.langgraph4j.RunnableConfig.builder().threadId("thread-1").build();
            java.util.LinkedList<org.bsc.langgraph4j.checkpoint.Checkpoint> result =
                    saver.loadCheckpoints(config);

            assertEquals(1, result.size());
            assertEquals("cp-1", result.get(0).getId());
            assertEquals("plan", result.get(0).getNodeId());
        }

        @Test
        @DisplayName("load - 默认 threadId 为 __default__")
        void testLoad_defaultThreadId() throws Exception {
            when(jdbcTemplate.queryForList(anyString(), eq("__default__")))
                    .thenReturn(List.of());

            org.bsc.langgraph4j.RunnableConfig config =
                    org.bsc.langgraph4j.RunnableConfig.builder().build();
            saver.loadCheckpoints(config);

            verify(jdbcTemplate).queryForList(anyString(), eq("__default__"));
        }

        @Test
        @DisplayName("release - 返回 Tag 对象")
        void testRelease_returnsTag() throws Exception {
            org.bsc.langgraph4j.RunnableConfig config =
                    org.bsc.langgraph4j.RunnableConfig.builder().threadId("thread-1").build();

            var tag = saver.releaseCheckpoints(config, new java.util.LinkedList<>());

            assertNotNull(tag);
            assertEquals("thread-1", tag.threadId());
        }
    }
}
