package com.hypersense.boot.framework.agents;

import com.hypersense.boot.framework.agents.middleware.impl.LoggingMiddleware;
import com.hypersense.boot.framework.agents.model.DeepAgentState;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LoggingMiddleware 单元测试
 * <p>
 * 覆盖：name、before/after 日志记录、计时、ThreadLocal 清理、边界场景。
 *
 * @author test
 */
class LoggingMiddlewareTest {

    private LoggingMiddleware middleware;

    @BeforeEach
    void setUp() {
        middleware = new LoggingMiddleware();
    }

    private DeepAgentState buildTestState() {
        Map<String, Object> data = new HashMap<>();
        data.put(DeepAgentState.SESSION_ID, "test-session-12345678");
        data.put(DeepAgentState.INSTRUCTIONS, "测试");
        data.put(DeepAgentState.MESSAGES, new ArrayList<>());
        data.put(DeepAgentState.TODOS, new ArrayList<>());
        data.put(DeepAgentState.FILES, new HashMap<>());
        data.put(DeepAgentState.SUB_AGENT_RESULTS, new HashMap<>());
        data.put(DeepAgentState.ENABLED_TOOLS, new ArrayList<>());
        return new DeepAgentState(data);
    }

    // ======================== 基础属性测试 ========================

    @Nested
    @DisplayName("基础属性")
    class BasicTests {

        @Test
        @DisplayName("name - 返回 'logging'")
        void testName() {
            assertEquals("logging", middleware.name());
        }
    }

    // ======================== before 测试 ========================

    @Nested
    @DisplayName("before - 执行前日志")
    class BeforeTests {

        @Test
        @DisplayName("before 不抛异常（正常记录）")
        void testBefore_noException() {
            DeepAgentState state = buildTestState();
            assertDoesNotThrow(() -> middleware.before("plan", state));
        }

        @Test
        @DisplayName("before 多次调用不报错")
        void testBefore_multipleCalls() {
            DeepAgentState state = buildTestState();
            assertDoesNotThrow(() -> {
                middleware.before("plan", state);
                middleware.before("execute", state);
                middleware.before("tool", state);
            });
        }
    }

    // ======================== after 测试 ========================

    @Nested
    @DisplayName("after - 执行后日志")
    class AfterTests {

        @Test
        @DisplayName("after 返回原始 output")
        void testAfter_returnsOutput() {
            DeepAgentState state = buildTestState();
            Map<String, Object> output = Map.of("key", "value");

            Map<String, Object> result = middleware.after("plan", state, output);

            assertSame(output, result, "after 应返回原始 output");
        }

        @Test
        @DisplayName("after 处理 null output")
        void testAfter_nullOutput() {
            DeepAgentState state = buildTestState();
            // after 内部需要 startMs，必须先调用 before
            middleware.before("plan", state);

            Map<String, Object> result = middleware.after("plan", state, null);
            assertNull(result, "null output 应原样返回");
        }

        @Test
        @DisplayName("after 处理空 output")
        void testAfter_emptyOutput() {
            DeepAgentState state = buildTestState();
            middleware.before("plan", state);

            Map<String, Object> result = middleware.after("plan", state, Map.of());
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    // ======================== ThreadLocal 清理测试 ========================

    @Nested
    @DisplayName("ThreadLocal 清理")
    class ThreadLocalTests {

        @Test
        @DisplayName("after 后 ThreadLocal 被清理")
        void testAfter_cleansUpThreadLocal() {
            DeepAgentState state = buildTestState();
            middleware.before("plan", state);
            middleware.after("plan", state, Map.of());

            // 验证：再次调用 after 不会因残留 ThreadLocal 出错
            // （startMs 为 null → elapsed = -1）
            assertDoesNotThrow(() -> middleware.after("plan", state, Map.of()));
        }

        @Test
        @DisplayName("未调用 before 直接调用 after → 不抛异常")
        void testAfter_withoutBefore() {
            DeepAgentState state = buildTestState();
            // startMs 为 null → elapsed = -1，不应 NPE
            assertDoesNotThrow(() -> middleware.after("plan", state, Map.of()));
        }
    }

    // ======================== 边界场景测试 ========================

    @Nested
    @DisplayName("边界场景")
    class EdgeCaseTests {

        @Test
        @DisplayName("sessionId 为 null → 不抛异常")
        void testNullSessionId() {
            Map<String, Object> data = new HashMap<>();
            data.put(DeepAgentState.SESSION_ID, null);
            data.put(DeepAgentState.MESSAGES, new ArrayList<>());
            data.put(DeepAgentState.TODOS, new ArrayList<>());
            data.put(DeepAgentState.FILES, new HashMap<>());
            data.put(DeepAgentState.SUB_AGENT_RESULTS, new HashMap<>());
            data.put(DeepAgentState.ENABLED_TOOLS, new ArrayList<>());
            DeepAgentState state = new DeepAgentState(data);

            assertDoesNotThrow(() -> {
                middleware.before("plan", state);
                middleware.after("plan", state, Map.of());
            });
        }

        @Test
        @DisplayName("短 sessionId 不截断")
        void testShortSessionId() {
            Map<String, Object> data = new HashMap<>();
            data.put(DeepAgentState.SESSION_ID, "abc");
            data.put(DeepAgentState.MESSAGES, new ArrayList<>());
            data.put(DeepAgentState.TODOS, new ArrayList<>());
            data.put(DeepAgentState.FILES, new HashMap<>());
            data.put(DeepAgentState.SUB_AGENT_RESULTS, new HashMap<>());
            data.put(DeepAgentState.ENABLED_TOOLS, new ArrayList<>());
            DeepAgentState state = new DeepAgentState(data);

            assertDoesNotThrow(() -> {
                middleware.before("plan", state);
                middleware.after("plan", state, Map.of());
            });
        }

        @Test
        @DisplayName("完整生命周期 - before → 执行 → after")
        void testFullLifecycle() {
            DeepAgentState state = buildTestState();

            // before
            middleware.before("plan", state);

            // 模拟节点执行
            Map<String, Object> output = Map.of(
                    DeepAgentState.EXECUTE_STRATEGY, "tool",
                    DeepAgentState.ITERATION_COUNT, 1
            );

            // after
            Map<String, Object> result = middleware.after("plan", state, output);

            assertNotNull(result, "after 应返回非 null");
            assertEquals("tool", result.get(DeepAgentState.EXECUTE_STRATEGY));
        }
    }
}
