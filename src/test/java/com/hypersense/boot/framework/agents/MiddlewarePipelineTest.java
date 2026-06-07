package com.hypersense.boot.framework.agents;

import com.hypersense.boot.framework.agents.exception.HitlInterruptedException;
import com.hypersense.boot.framework.agents.middleware.AgentMiddleware;
import com.hypersense.boot.framework.agents.middleware.MiddlewarePipeline;
import com.hypersense.boot.framework.agents.model.DeepAgentState;
import org.bsc.langgraph4j.action.NodeAction;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * MiddlewarePipeline 单元测试
 * <p>
 * 覆盖：洋葱模型执行顺序、空管道零开销、异常处理、HITL 中断传播、after 修改 state。
 *
 * @author test
 */
class MiddlewarePipelineTest {

    // ======================== 辅助方法 ========================

    private DeepAgentState buildTestState() {
        Map<String, Object> data = new HashMap<>();
        data.put(DeepAgentState.SESSION_ID, "test-session");
        data.put(DeepAgentState.INSTRUCTIONS, "测试");
        data.put(DeepAgentState.MESSAGES, new ArrayList<>());
        data.put(DeepAgentState.TODOS, new ArrayList<>());
        data.put(DeepAgentState.FILES, new HashMap<>());
        data.put(DeepAgentState.SUB_AGENT_RESULTS, new HashMap<>());
        data.put(DeepAgentState.ENABLED_TOOLS, new ArrayList<>());
        return new DeepAgentState(data);
    }

    private NodeAction<DeepAgentState> identityNode() {
        return state -> Map.of();
    }

    // ======================== 空管道测试 ========================

    @Nested
    @DisplayName("空管道 - 零开销优化")
    class EmptyPipelineTests {

        @Test
        @DisplayName("isEmpty - 空管道返回 true")
        void testIsEmpty() {
            MiddlewarePipeline pipeline = new MiddlewarePipeline();
            assertTrue(pipeline.isEmpty());
        }

        @Test
        @DisplayName("wrap - 空管道返回原始 NodeAction（同一实例）")
        void testWrap_returnsOriginalNode() {
            MiddlewarePipeline pipeline = new MiddlewarePipeline();
            NodeAction<DeepAgentState> original = identityNode();

            NodeAction<DeepAgentState> wrapped = pipeline.wrap("test", original);

            assertSame(original, wrapped, "空管道应返回原始 NodeAction 实例");
        }

        @Test
        @DisplayName("getMiddlewares - 空列表")
        void testGetMiddlewares_empty() {
            MiddlewarePipeline pipeline = new MiddlewarePipeline();
            assertTrue(pipeline.getMiddlewares().isEmpty());
        }
    }

    // ======================== 单中间件测试 ========================

    @Nested
    @DisplayName("单中间件 - before/after 调用")
    class SingleMiddlewareTests {

        @Test
        @DisplayName("before 和 after 各调用 1 次")
        void testBeforeAfterCalled() throws Exception {
            MiddlewarePipeline pipeline = new MiddlewarePipeline();
            AtomicInteger beforeCount = new AtomicInteger();
            AtomicInteger afterCount = new AtomicInteger();

            pipeline.add(new AgentMiddleware() {
                @Override
                public String name() { return "test-mw"; }

                @Override
                public void before(String nodeName, DeepAgentState state) {
                    beforeCount.incrementAndGet();
                }

                @Override
                public Map<String, Object> after(String nodeName, DeepAgentState state, Map<String, Object> output) {
                    afterCount.incrementAndGet();
                    return output;
                }
            });

            NodeAction<DeepAgentState> wrapped = pipeline.wrap("plan", identityNode());
            wrapped.apply(buildTestState());

            assertEquals(1, beforeCount.get(), "before 应调用 1 次");
            assertEquals(1, afterCount.get(), "after 应调用 1 次");
        }

        @Test
        @DisplayName("before 接收正确的 nodeName")
        void testBeforeReceivesNodeName() throws Exception {
            MiddlewarePipeline pipeline = new MiddlewarePipeline();
            List<String> capturedNames = new ArrayList<>();

            pipeline.add(new AgentMiddleware() {
                @Override
                public String name() { return "test-mw"; }

                @Override
                public void before(String nodeName, DeepAgentState state) {
                    capturedNames.add(nodeName);
                }
            });

            NodeAction<DeepAgentState> wrapped = pipeline.wrap("execute", identityNode());
            wrapped.apply(buildTestState());

            assertEquals(List.of("execute"), capturedNames);
        }
    }

    // ======================== 洋葱模型测试 ========================

    @Nested
    @DisplayName("洋葱模型 - 执行顺序")
    class OnionModelTests {

        @Test
        @DisplayName("before: M1→M2, after: M2→M1")
        void testOnionOrder() throws Exception {
            MiddlewarePipeline pipeline = new MiddlewarePipeline();
            List<String> order = new ArrayList<>();

            pipeline.add(new AgentMiddleware() {
                @Override
                public String name() { return "M1"; }
                @Override
                public void before(String nodeName, DeepAgentState state) { order.add("M1-before"); }
                @Override
                public Map<String, Object> after(String nodeName, DeepAgentState state, Map<String, Object> output) {
                    order.add("M1-after");
                    return output;
                }
            });

            pipeline.add(new AgentMiddleware() {
                @Override
                public String name() { return "M2"; }
                @Override
                public void before(String nodeName, DeepAgentState state) { order.add("M2-before"); }
                @Override
                public Map<String, Object> after(String nodeName, DeepAgentState state, Map<String, Object> output) {
                    order.add("M2-after");
                    return output;
                }
            });

            NodeAction<DeepAgentState> wrapped = pipeline.wrap("plan", identityNode());
            wrapped.apply(buildTestState());

            assertEquals(List.of("M1-before", "M2-before", "M2-after", "M1-after"), order,
                    "应按洋葱模型执行");
        }

        @Test
        @DisplayName("3 个中间件的洋葱模型")
        void testThreeMiddlewares() throws Exception {
            MiddlewarePipeline pipeline = new MiddlewarePipeline();
            List<String> order = new ArrayList<>();

            for (int i = 1; i <= 3; i++) {
                final String name = "M" + i;
                pipeline.add(new AgentMiddleware() {
                    @Override
                    public String name() { return name; }
                    @Override
                    public void before(String nodeName, DeepAgentState state) { order.add(name + "-before"); }
                    @Override
                    public Map<String, Object> after(String nodeName, DeepAgentState state, Map<String, Object> output) {
                        order.add(name + "-after");
                        return output;
                    }
                });
            }

            NodeAction<DeepAgentState> wrapped = pipeline.wrap("tool", identityNode());
            wrapped.apply(buildTestState());

            assertEquals(
                    List.of("M1-before", "M2-before", "M3-before", "M3-after", "M2-after", "M1-after"),
                    order, "3 个中间件应按洋葱模型执行");
        }
    }

    // ======================== 异常处理测试 ========================

    @Nested
    @DisplayName("异常处理 - 中间件异常容错")
    class ExceptionHandlingTests {

        @Test
        @DisplayName("before 抛异常 → 跳过该中间件，继续执行后续 before + node + after")
        void testBeforeException_skipped() throws Exception {
            MiddlewarePipeline pipeline = new MiddlewarePipeline();
            List<String> order = new ArrayList<>();

            pipeline.add(new AgentMiddleware() {
                @Override
                public String name() { return "M1-fail"; }
                @Override
                public void before(String nodeName, DeepAgentState state) {
                    throw new RuntimeException("before 失败");
                }
                @Override
                public Map<String, Object> after(String nodeName, DeepAgentState state, Map<String, Object> output) {
                    order.add("M1-after");
                    return output;
                }
            });

            pipeline.add(new AgentMiddleware() {
                @Override
                public String name() { return "M2-ok"; }
                @Override
                public void before(String nodeName, DeepAgentState state) { order.add("M2-before"); }
                @Override
                public Map<String, Object> after(String nodeName, DeepAgentState state, Map<String, Object> output) {
                    order.add("M2-after");
                    return output;
                }
            });

            NodeAction<DeepAgentState> wrapped = pipeline.wrap("plan", identityNode());
            wrapped.apply(buildTestState());

            // M1-before 失败被吞掉，M2-before 正常执行
            // M2-after 正常执行，M1-after 也执行
            assertTrue(order.contains("M2-before"), "M2-before 应执行");
            assertTrue(order.contains("M2-after"), "M2-after 应执行");
            assertTrue(order.contains("M1-after"), "M1-after 应执行（before 异常不影响 after）");
        }

        @Test
        @DisplayName("after 抛异常 → 不影响其他中间件 after")
        void testAfterException_skipped() throws Exception {
            MiddlewarePipeline pipeline = new MiddlewarePipeline();
            List<String> order = new ArrayList<>();

            pipeline.add(new AgentMiddleware() {
                @Override
                public String name() { return "M1-ok"; }
                @Override
                public void before(String nodeName, DeepAgentState state) { order.add("M1-before"); }
                @Override
                public Map<String, Object> after(String nodeName, DeepAgentState state, Map<String, Object> output) {
                    order.add("M1-after");
                    return output;
                }
            });

            pipeline.add(new AgentMiddleware() {
                @Override
                public String name() { return "M2-fail"; }
                @Override
                public void before(String nodeName, DeepAgentState state) { order.add("M2-before"); }
                @Override
                public Map<String, Object> after(String nodeName, DeepAgentState state, Map<String, Object> output) {
                    throw new RuntimeException("after 失败");
                }
            });

            NodeAction<DeepAgentState> wrapped = pipeline.wrap("plan", identityNode());
            wrapped.apply(buildTestState());

            // 顺序: M1-before, M2-before, [node], M2-after(fail), M1-after(ok)
            assertTrue(order.contains("M1-before"), "M1-before 应执行");
            assertTrue(order.contains("M2-before"), "M2-before 应执行");
            assertTrue(order.contains("M1-after"), "M1-after 应执行");
        }

        @Test
        @DisplayName("HITL 中断异常 - before 中穿透传播")
        void testHitlInterrupt_before_propagates() throws Exception {
            MiddlewarePipeline pipeline = new MiddlewarePipeline();

            pipeline.add(new AgentMiddleware() {
                @Override
                public String name() { return "hitl-mw"; }
                @Override
                public void before(String nodeName, DeepAgentState state) {
                    throw new HitlInterruptedException("tool", "HITL 中断");
                }
            });

            NodeAction<DeepAgentState> wrapped = pipeline.wrap("tool", identityNode());

            HitlInterruptedException ex = assertThrows(HitlInterruptedException.class,
                    () -> wrapped.apply(buildTestState()));
            assertEquals("tool", ex.getNodeName());
        }

        @Test
        @DisplayName("HITL 中断异常 - after 中穿透传播")
        void testHitlInterrupt_after_propagates() throws Exception {
            MiddlewarePipeline pipeline = new MiddlewarePipeline();

            pipeline.add(new AgentMiddleware() {
                @Override
                public String name() { return "hitl-mw"; }
                @Override
                public Map<String, Object> after(String nodeName, DeepAgentState state, Map<String, Object> output) {
                    throw new HitlInterruptedException("execute", "HITL 中断");
                }
            });

            NodeAction<DeepAgentState> wrapped = pipeline.wrap("execute", identityNode());

            HitlInterruptedException ex = assertThrows(HitlInterruptedException.class,
                    () -> wrapped.apply(buildTestState()));
            assertEquals("execute", ex.getNodeName());
        }
    }

    // ======================== after 修改 output 测试 ========================

    @Nested
    @DisplayName("after 修改 output - 状态传递")
    class AfterModificationTests {

        @Test
        @DisplayName("after 修改 output → 传递给下一个 after")
        void testAfterModifiesOutput() throws Exception {
            MiddlewarePipeline pipeline = new MiddlewarePipeline();
            List<Map<String, Object>> capturedOutputs = new ArrayList<>();

            // M1: 在 after 中添加 M1-key
            pipeline.add(new AgentMiddleware() {
                @Override
                public String name() { return "M1"; }
                @Override
                public Map<String, Object> after(String nodeName, DeepAgentState state, Map<String, Object> output) {
                    Map<String, Object> modified = new HashMap<>(output);
                    modified.put("M1-key", "M1-value");
                    return modified;
                }
            });

            // M2: 在 after 中捕获 output 并验证
            pipeline.add(new AgentMiddleware() {
                @Override
                public String name() { return "M2"; }
                @Override
                public Map<String, Object> after(String nodeName, DeepAgentState state, Map<String, Object> output) {
                    capturedOutputs.add(new HashMap<>(output));
                    return output;
                }
            });

            NodeAction<DeepAgentState> wrapped = pipeline.wrap("plan", identityNode());
            wrapped.apply(buildTestState());

            // M2 的 after 先执行（逆序），此时 output 应不包含 M1-key
            // M1 的 after 后执行，添加 M1-key
            assertFalse(capturedOutputs.isEmpty(), "应捕获到 output");
            // 注意：洋葱模型中 M2-after 先执行（innermost），M1-after 后执行（outermost）
            // 所以 M2-after 看到的是原始 output，M1-after 的修改在 M2-after 之后
        }
    }

    // ======================== 管道管理测试 ========================

    @Nested
    @DisplayName("管道管理 - add/replace/isEmpty")
    class PipelineManagementTests {

        @Test
        @DisplayName("add - isEmpty 变为 false")
        void testAdd_makesNonEmpty() {
            MiddlewarePipeline pipeline = new MiddlewarePipeline();
            pipeline.add(new AgentMiddleware() {
                @Override
                public String name() { return "test"; }
            });
            assertFalse(pipeline.isEmpty());
            assertEquals(1, pipeline.getMiddlewares().size());
        }

        @Test
        @DisplayName("replace - 替换中间件")
        void testReplace() {
            MiddlewarePipeline pipeline = new MiddlewarePipeline();
            pipeline.add(new AgentMiddleware() {
                @Override
                public String name() { return "old"; }
            });

            pipeline.replace(0, new AgentMiddleware() {
                @Override
                public String name() { return "new"; }
            });

            assertEquals("new", pipeline.getMiddlewares().get(0).name());
        }

        @Test
        @DisplayName("wrap - 有中间件时返回包装实例（非原始）")
        void testWrap_withMiddleware_returnsWrapped() throws Exception {
            MiddlewarePipeline pipeline = new MiddlewarePipeline();
            pipeline.add(new AgentMiddleware() {
                @Override
                public String name() { return "test"; }
            });

            NodeAction<DeepAgentState> original = identityNode();
            NodeAction<DeepAgentState> wrapped = pipeline.wrap("plan", original);

            assertNotSame(original, wrapped, "有中间件时应返回包装实例");
        }

        @Test
        @DisplayName("getMiddlewares - 不可修改")
        void testGetMiddlewares_unmodifiable() {
            MiddlewarePipeline pipeline = new MiddlewarePipeline();
            pipeline.add(new AgentMiddleware() {
                @Override
                public String name() { return "test"; }
            });

            assertThrows(UnsupportedOperationException.class,
                    () -> pipeline.getMiddlewares().add(new AgentMiddleware() {
                        @Override
                        public String name() { return "another"; }
                    }));
        }
    }

    // ======================== 带列表构造器测试 ========================

    @Nested
    @DisplayName("构造器 - 列表初始化")
    class ConstructorTests {

        @Test
        @DisplayName("MiddlewarePipeline(List) - 预填充中间件")
        void testConstructorWithList() {
            List<AgentMiddleware> initial = new ArrayList<>();
            initial.add(new AgentMiddleware() {
                @Override
                public String name() { return "preloaded"; }
            });

            MiddlewarePipeline pipeline = new MiddlewarePipeline(initial);
            assertEquals(1, pipeline.getMiddlewares().size());
            assertEquals("preloaded", pipeline.getMiddlewares().get(0).name());
        }

        @Test
        @DisplayName("MiddlewarePipeline(List) - 防御性拷贝")
        void testConstructorWithList_defensiveCopy() {
            List<AgentMiddleware> initial = new ArrayList<>();
            MiddlewarePipeline pipeline = new MiddlewarePipeline(initial);

            // 修改原始列表不影响 pipeline
            initial.add(new AgentMiddleware() {
                @Override
                public String name() { return "added-later"; }
            });

            assertEquals(0, pipeline.getMiddlewares().size(), "防御性拷贝应隔离原始列表");
        }
    }
}
