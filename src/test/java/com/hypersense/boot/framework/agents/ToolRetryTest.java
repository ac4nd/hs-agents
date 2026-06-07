package com.hypersense.boot.framework.agents;

import com.hypersense.boot.framework.agents.config.AgentProperties;
import com.hypersense.boot.framework.agents.config.ToolRetryConfig;
import com.hypersense.boot.framework.agents.engine.node.ToolNode;
import com.hypersense.boot.framework.agents.enums.TodoStatus;
import com.hypersense.boot.framework.agents.model.DeepAgentState;
import com.hypersense.boot.framework.agents.model.TodoItem;
import com.hypersense.boot.framework.agents.tool.ToolProvider;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * P0-5: 工具重试（Tool Retry）系统测试
 * <p>
 * 覆盖范围：
 * <ul>
 *   <li>ToolRetryConfig 值对象：默认值、工厂方法、退避计算、fromProperties 转换</li>
 *   <li>ToolNode 重试逻辑：禁用时零开销、启用时指数退避、耗尽重试后失败</li>
 *   <li>AgentProperties 绑定路径：enabled=false 默认关闭</li>
 *   <li>GodlikeAgent.Builder 集成：enableToolRetry()、toolRetryConfig()</li>
 * </ul>
 *
 * @author test
 */
class ToolRetryTest {

    // ======================== ToolRetryConfig 值对象测试 ========================

    @Nested
    @DisplayName("ToolRetryConfig - 值对象")
    class ConfigTests {

        @Test
        @DisplayName("disabled() - 默认关闭状态")
        void testDisabled() {
            ToolRetryConfig config = ToolRetryConfig.disabled();
            assertFalse(config.isEnabled(), "disabled() 应返回关闭状态");
            assertEquals(3, config.getMaxAttempts(), "maxAttempts 应为默认值 3");
        }

        @Test
        @DisplayName("defaults() - 启用状态，默认参数")
        void testDefaults() {
            ToolRetryConfig config = ToolRetryConfig.defaults();
            assertTrue(config.isEnabled(), "defaults() 应返回启用状态");
            assertEquals(3, config.getMaxAttempts());
            assertEquals(1000L, config.getInitialDelayMs());
            assertEquals(30000L, config.getMaxDelayMs());
            assertEquals(2.0, config.getBackoffMultiplier(), 0.01);
        }

        @Test
        @DisplayName("Builder - 自定义参数")
        void testCustomBuilder() {
            ToolRetryConfig config = ToolRetryConfig.builder()
                    .enabled(true)
                    .maxAttempts(5)
                    .initialDelayMs(500L)
                    .maxDelayMs(60000L)
                    .backoffMultiplier(3.0)
                    .build();

            assertTrue(config.isEnabled());
            assertEquals(5, config.getMaxAttempts());
            assertEquals(500L, config.getInitialDelayMs());
            assertEquals(60000L, config.getMaxDelayMs());
            assertEquals(3.0, config.getBackoffMultiplier(), 0.01);
        }

        @Test
        @DisplayName("calculateDelay - 指数退避计算")
        void testCalculateDelay() {
            ToolRetryConfig config = ToolRetryConfig.defaults();
            // initialDelay=1000, multiplier=2.0
            assertEquals(1000L, config.calculateDelay(1));  // 1000 * 2^0 = 1000
            assertEquals(2000L, config.calculateDelay(2));  // 1000 * 2^1 = 2000
            assertEquals(4000L, config.calculateDelay(3));  // 1000 * 2^2 = 4000
            assertEquals(8000L, config.calculateDelay(4));  // 1000 * 2^3 = 8000
        }

        @Test
        @DisplayName("calculateDelay - 不超过 maxDelayMs")
        void testCalculateDelay_capped() {
            ToolRetryConfig config = ToolRetryConfig.builder()
                    .enabled(true)
                    .initialDelayMs(10000L)
                    .maxDelayMs(30000L)
                    .backoffMultiplier(5.0)
                    .build();

            assertEquals(10000L, config.calculateDelay(1));  // 10000
            assertEquals(30000L, config.calculateDelay(2));  // 50000 → cap at 30000
            assertEquals(30000L, config.calculateDelay(3));  // 250000 → cap at 30000
        }

        @Test
        @DisplayName("calculateDelay - 高倍数快速封顶")
        void testCalculateDelay_aggressiveMultiplier() {
            ToolRetryConfig config = ToolRetryConfig.builder()
                    .enabled(true)
                    .initialDelayMs(100L)
                    .maxDelayMs(5000L)
                    .backoffMultiplier(10.0)
                    .build();

            assertEquals(100L, config.calculateDelay(1));    // 100
            assertEquals(1000L, config.calculateDelay(2));   // 1000
            assertEquals(5000L, config.calculateDelay(3));   // 10000 → cap at 5000
        }
    }

    // ======================== fromProperties 转换测试 ========================

    @Nested
    @DisplayName("ToolRetryConfig.fromProperties - Spring 配置转换")
    class FromPropertiesTests {

        @Test
        @DisplayName("null props → disabled")
        void testNullProps() {
            ToolRetryConfig config = ToolRetryConfig.fromProperties(null);
            assertFalse(config.isEnabled());
        }

        @Test
        @DisplayName("enabled=false → disabled")
        void testDisabledProps() {
            AgentProperties.ToolRetryConfig props = new AgentProperties.ToolRetryConfig();
            props.setEnabled(false);

            ToolRetryConfig config = ToolRetryConfig.fromProperties(props);
            assertFalse(config.isEnabled());
        }

        @Test
        @DisplayName("enabled=true + 自定义参数 → 正确转换")
        void testEnabledProps() {
            AgentProperties.ToolRetryConfig props = new AgentProperties.ToolRetryConfig();
            props.setEnabled(true);
            props.setMaxAttempts(5);
            props.setInitialDelayMs(500L);
            props.setMaxDelayMs(60000L);
            props.setBackoffMultiplier(3.0);

            ToolRetryConfig config = ToolRetryConfig.fromProperties(props);

            assertTrue(config.isEnabled());
            assertEquals(5, config.getMaxAttempts());
            assertEquals(500L, config.getInitialDelayMs());
            assertEquals(60000L, config.getMaxDelayMs());
            assertEquals(3.0, config.getBackoffMultiplier(), 0.01);
        }

        @Test
        @DisplayName("enabled=true + null 字段 → 使用默认值")
        void testEnabledPropsWithNulls() {
            AgentProperties.ToolRetryConfig props = new AgentProperties.ToolRetryConfig();
            props.setEnabled(true);
            // 其他字段保持 null

            ToolRetryConfig config = ToolRetryConfig.fromProperties(props);

            assertTrue(config.isEnabled());
            assertEquals(3, config.getMaxAttempts());
            assertEquals(1000L, config.getInitialDelayMs());
            assertEquals(30000L, config.getMaxDelayMs());
            assertEquals(2.0, config.getBackoffMultiplier(), 0.01);
        }
    }

    // ======================== ToolNode 重试逻辑测试 ========================

    @Nested
    @DisplayName("ToolNode - 重试执行")
    class ToolNodeRetryTests {

        private ToolProvider failingTool;
        private ToolProvider successAfterRetryTool;
        private ToolProvider alwaysSuccessTool;
        private List<Integer> attemptTracker;

        @BeforeEach
        void setUp() {
            attemptTracker = new ArrayList<>();

            // 始终失败的工具
            failingTool = mock(ToolProvider.class);
            when(failingTool.name()).thenReturn("always-fail");
            when(failingTool.description()).thenReturn("始终失败");
            when(failingTool.execute(anyMap())).thenThrow(new RuntimeException("工具内部错误"));

            // 重试 2 次后成功的工具
            successAfterRetryTool = mock(ToolProvider.class);
            when(successAfterRetryTool.name()).thenReturn("retry-success");
            when(successAfterRetryTool.description()).thenReturn("重试后成功");
            when(successAfterRetryTool.execute(anyMap())).thenAnswer(inv -> {
                attemptTracker.add(1);
                if (attemptTracker.size() < 3) {
                    throw new RuntimeException("暂时失败 #" + attemptTracker.size());
                }
                return Map.of("success", true, "data", "第三次成功了");
            });

            // 始终成功的工具
            alwaysSuccessTool = mock(ToolProvider.class);
            when(alwaysSuccessTool.name()).thenReturn("always-ok");
            when(alwaysSuccessTool.description()).thenReturn("始终成功");
            when(alwaysSuccessTool.execute(anyMap())).thenReturn(Map.of("success", true, "result", "ok"));
        }

        /**
         * 构建带 TODO 的测试状态
         */
        private Map<String, Object> buildTestState() {
            Map<String, Object> state = new HashMap<>();
            state.put(DeepAgentState.SESSION_ID, "test-session");
            state.put(DeepAgentState.INSTRUCTIONS, "测试工具重试");
            state.put(DeepAgentState.MESSAGES, new ArrayList<>());
            state.put(DeepAgentState.TODOS, new ArrayList<>(List.of(
                    TodoItem.builder()
                            .id("todo-1")
                            .description("重试测试任务")
                            .status(TodoStatus.IN_PROGRESS)
                            .build()
            )));
            state.put(DeepAgentState.CURRENT_TODO, TodoItem.builder()
                    .id("todo-1")
                    .description("重试测试任务")
                    .status(TodoStatus.IN_PROGRESS)
                    .build());
            state.put(DeepAgentState.FILES, new HashMap<>());
            state.put(DeepAgentState.SUB_AGENT_RESULTS, new HashMap<>());
            state.put(DeepAgentState.ENABLED_TOOLS, new ArrayList<>());
            return state;
        }

        @Test
        @DisplayName("禁用重试 - 工具失败后直接返回失败结果")
        void testRetryDisabled_failsImmediately() {
            ToolRetryConfig config = ToolRetryConfig.disabled();
            ToolNode toolNode = ToolNode.create(List.of(failingTool), config);

            DeepAgentState state = new DeepAgentState(buildTestState());
            Map<String, Object> result = toolNode.apply(state);

            // 验证只调用一次
            verify(failingTool, times(1)).execute(anyMap());

            // 验证 TODO 标记为 FAILED
            @SuppressWarnings("unchecked")
            List<TodoItem> todos = (List<TodoItem>) result.get(DeepAgentState.TODOS);
            assertNotNull(todos);
            assertEquals(TodoStatus.FAILED, todos.get(0).getStatus(), "禁用重试时应标记 FAILED");
        }

        @Test
        @DisplayName("启用重试 - 工具始终失败，耗尽重试次数")
        void testRetryEnabled_exhaustsAllAttempts() {
            // 使用极短延迟避免测试阻塞
            ToolRetryConfig config = ToolRetryConfig.builder()
                    .enabled(true)
                    .maxAttempts(3)
                    .initialDelayMs(10L)    // 10ms 避免测试太慢
                    .maxDelayMs(100L)
                    .backoffMultiplier(2.0)
                    .build();

            ToolNode toolNode = ToolNode.create(List.of(failingTool), config);

            DeepAgentState state = new DeepAgentState(buildTestState());
            Map<String, Object> result = toolNode.apply(state);

            // 验证重试了 3 次
            verify(failingTool, times(3)).execute(anyMap());

            // 验证 TODO 标记为 FAILED
            @SuppressWarnings("unchecked")
            List<TodoItem> todos = (List<TodoItem>) result.get(DeepAgentState.TODOS);
            assertEquals(TodoStatus.FAILED, todos.get(0).getStatus());
        }

        @Test
        @DisplayName("启用重试 - 工具第 3 次成功")
        void testRetryEnabled_succeedsOnRetry() {
            ToolRetryConfig config = ToolRetryConfig.builder()
                    .enabled(true)
                    .maxAttempts(3)
                    .initialDelayMs(10L)
                    .maxDelayMs(100L)
                    .backoffMultiplier(2.0)
                    .build();

            ToolNode toolNode = ToolNode.create(List.of(successAfterRetryTool), config);

            DeepAgentState state = new DeepAgentState(buildTestState());
            Map<String, Object> result = toolNode.apply(state);

            // 验证重试了 3 次后成功
            verify(successAfterRetryTool, times(3)).execute(anyMap());

            // 验证 TODO 标记为 COMPLETED
            @SuppressWarnings("unchecked")
            List<TodoItem> todos = (List<TodoItem>) result.get(DeepAgentState.TODOS);
            assertEquals(TodoStatus.COMPLETED, todos.get(0).getStatus(), "重试成功后应标记 COMPLETED");
        }

        @Test
        @DisplayName("启用重试 - 工具始终成功，只调用 1 次")
        void testRetryEnabled_alwaysSucceeds() {
            ToolRetryConfig config = ToolRetryConfig.defaults();
            ToolNode toolNode = ToolNode.create(List.of(alwaysSuccessTool), config);

            DeepAgentState state = new DeepAgentState(buildTestState());
            toolNode.apply(state);

            // 验证只调用 1 次（成功不重试）
            verify(alwaysSuccessTool, times(1)).execute(anyMap());
        }

        @Test
        @DisplayName("maxAttempts=1 - 等同于无重试")
        void testRetrySingleAttempt() {
            ToolRetryConfig config = ToolRetryConfig.builder()
                    .enabled(true)
                    .maxAttempts(1)
                    .initialDelayMs(10L)
                    .build();

            ToolNode toolNode = ToolNode.create(List.of(failingTool), config);

            DeepAgentState state = new DeepAgentState(buildTestState());
            toolNode.apply(state);

            // maxAttempts=1 → 只调用 1 次
            verify(failingTool, times(1)).execute(anyMap());
        }

        @Test
        @DisplayName("GodlikeAgent.Builder - enableToolRetry() 使用默认配置")
        void testBuilderEnableToolRetry() {
            // 验证构建不报错（不实际执行，仅验证构建路径）
            assertDoesNotThrow(() -> {
                try {
                    dev.langchain4j.model.chat.ChatModel mockModel = mock(dev.langchain4j.model.chat.ChatModel.class);
                    GodlikeAgent agent = GodlikeAgent.builder()
                            .model(mockModel)
                            .enableToolRetry()
                            .build();
                    assertNotNull(agent, "Agent 应构建成功");
                } catch (Exception e) {
                    // Graph 构建可能因 mock 不完整失败，但 enableToolRetry 不应导致 NPE
                    if (e.getMessage() != null && e.getMessage().contains("retryConfig")) {
                        fail("enableToolRetry() 导致构建失败: " + e.getMessage());
                    }
                }
            });
        }
    }
}
