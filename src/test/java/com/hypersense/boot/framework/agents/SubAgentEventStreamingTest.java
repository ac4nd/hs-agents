package com.hypersense.boot.framework.agents;

import com.hypersense.boot.framework.agents.engine.SubAgentEventBus;
import com.hypersense.boot.framework.agents.engine.node.SubAgentExecutor;
import com.hypersense.boot.framework.agents.engine.node.SubAgentExecutor.SubAgentResult;
import com.hypersense.boot.framework.agents.enums.AgentEventType;
import com.hypersense.boot.framework.agents.model.AgentEvent;
import com.hypersense.boot.framework.agents.model.SubAgentContext;
import com.hypersense.boot.framework.agents.model.SubAgentDefinition;
import com.hypersense.boot.framework.agents.tool.ToolProvider;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * P0-6: 流式子 Agent 事件（Streaming Sub-Agent Events）系统测试
 * <p>
 * 覆盖范围：
 * <ul>
 *   <li>SubAgentEventBus：ThreadLocal 设置/获取/清理/跨线程传播</li>
 *   <li>AgentEventType：子 Agent 事件类型完整性</li>
 *   <li>SubAgentExecutor：无消费者走 run()、有消费者走 streamAndReturn()</li>
 *   <li>GodlikeAgent.streamAndReturn()：事件推送 + 返回最终响应</li>
 *   <li>事件包装：类型映射、消息前缀、FINAL_RESPONSE/ERROR 抑制</li>
 *   <li>嵌套子 Agent：双层闭包捕获传播</li>
 * </ul>
 *
 * @author test
 */
class SubAgentEventStreamingTest {

    private ChatModel chatModel;

    @BeforeEach
    void setUp() {
        chatModel = mock(ChatModel.class);

        // Mock LLM 响应：根据系统提示词返回不同内容
        when(chatModel.chat(anyList())).thenAnswer(invocation -> {
            List<ChatMessage> messages = invocation.getArgument(0);
            return buildMockResponse(messages);
        });
        when(chatModel.chat(any(ChatRequest.class))).thenAnswer(invocation -> {
            ChatRequest request = invocation.getArgument(0);
            return buildMockResponse(request.messages());
        });
    }

    @AfterEach
    void tearDown() {
        SubAgentEventBus.remove();
    }

    private ChatResponse buildMockResponse(List<ChatMessage> messages) {
        String systemText = messages.stream()
                .filter(m -> m instanceof SystemMessage)
                .map(m -> ((SystemMessage) m).text())
                .findFirst()
                .orElse("");

        String responseText;
        if (systemText.contains("任务规划专家")) {
            responseText = "TODO: 回答用户问题";
        } else if (systemText.contains("任务执行决策器")) {
            // "tool" 策略 → 进入 ToolNode → TODO 标记 COMPLETED → 图终止
            responseText = "tool";
        } else if (systemText.contains("结果汇总专家")) {
            responseText = "最终报告：子任务已完成。";
        } else {
            responseText = "子任务完成";
        }

        ChatResponse response = mock(ChatResponse.class);
        when(response.aiMessage()).thenReturn(AiMessage.from(responseText));
        return response;
    }

    // ======================== SubAgentEventBus 测试 ========================

    @Nested
    @DisplayName("SubAgentEventBus - ThreadLocal 事件总线")
    class EventBusTests {

        @Test
        @DisplayName("初始状态 - 无消费者")
        void testInitialState() {
            assertNull(SubAgentEventBus.get(), "初始状态应为 null");
            assertFalse(SubAgentEventBus.hasConsumer(), "初始状态应无消费者");
        }

        @Test
        @DisplayName("set + get - 正确存取")
        void testSetAndGet() {
            @SuppressWarnings("unchecked")
            Consumer<AgentEvent> consumer = mock(Consumer.class);
            SubAgentEventBus.set(consumer);

            assertSame(consumer, SubAgentEventBus.get(), "get 应返回设置的消费者");
            assertTrue(SubAgentEventBus.hasConsumer(), "应报告有消费者");
        }

        @Test
        @DisplayName("remove - 清除消费者")
        void testRemove() {
            SubAgentEventBus.set(event -> {});
            assertTrue(SubAgentEventBus.hasConsumer());

            SubAgentEventBus.remove();
            assertNull(SubAgentEventBus.get(), "remove 后应为 null");
            assertFalse(SubAgentEventBus.hasConsumer());
        }

        @Test
        @DisplayName("set(null) - 等同于 remove")
        void testSetNull() {
            SubAgentEventBus.set(event -> {});
            assertTrue(SubAgentEventBus.hasConsumer());

            SubAgentEventBus.set(null);
            assertNull(SubAgentEventBus.get(), "set(null) 应清除消费者");
        }

        @Test
        @DisplayName("跨线程传播 - ThreadLocal 不自动传播")
        void testCrossThread_noAutoPropagation() throws Exception {
            SubAgentEventBus.set(event -> {});

            // 在另一个线程上读取
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<Consumer<AgentEvent>> otherThreadConsumer = new AtomicReference<>();

            Thread t = new Thread(() -> {
                otherThreadConsumer.set(SubAgentEventBus.get());
                latch.countDown();
            });
            t.start();
            latch.await(5, TimeUnit.SECONDS);

            assertNull(otherThreadConsumer.get(), "ThreadLocal 不应自动传播到新线程");
        }

        @Test
        @DisplayName("闭包捕获 - 通过闭包手动传播")
        void testClosureCapture() throws Exception {
            Consumer<AgentEvent> originalConsumer = event -> {};
            SubAgentEventBus.set(originalConsumer);

            // 闭包捕获（模拟 SubAgentExecutor 的做法）
            Consumer<AgentEvent> captured = SubAgentEventBus.get();

            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<Consumer<AgentEvent>> capturedInOtherThread = new AtomicReference<>();

            Thread t = new Thread(() -> {
                // 在新线程上设置捕获的消费者
                SubAgentEventBus.set(captured);
                capturedInOtherThread.set(SubAgentEventBus.get());
                latch.countDown();
            });
            t.start();
            latch.await(5, TimeUnit.SECONDS);

            assertSame(originalConsumer, capturedInOtherThread.get(),
                    "通过闭包捕获应能传播到新线程");
        }
    }

    // ======================== AgentEventType 子 Agent 事件测试 ========================

    @Nested
    @DisplayName("AgentEventType - 子 Agent 事件类型")
    class SubAgentEventTypeTests {

        @Test
        @DisplayName("新增 4 个子 Agent 事件类型")
        void testSubAgentEventTypes() {
            assertNotNull(AgentEventType.SUB_AGENT_STARTED);
            assertNotNull(AgentEventType.SUB_AGENT_NODE_EXECUTION);
            assertNotNull(AgentEventType.SUB_AGENT_COMPLETED);
            assertNotNull(AgentEventType.SUB_AGENT_FAILED);
        }

        @Test
        @DisplayName("事件值符合命名规范")
        void testEventValues() {
            assertEquals("sub_agent_started", AgentEventType.SUB_AGENT_STARTED.getValue());
            assertEquals("sub_agent_node_execution", AgentEventType.SUB_AGENT_NODE_EXECUTION.getValue());
            assertEquals("sub_agent_completed", AgentEventType.SUB_AGENT_COMPLETED.getValue());
            assertEquals("sub_agent_failed", AgentEventType.SUB_AGENT_FAILED.getValue());
        }

        @Test
        @DisplayName("SUB_AGENT_DELEGATED 事件存在")
        void testDelegatedEvent() {
            assertNotNull(AgentEventType.SUB_AGENT_DELEGATED);
            assertEquals("sub_agent_delegated", AgentEventType.SUB_AGENT_DELEGATED.getValue());
        }
    }

    // ======================== GodlikeAgent.streamAndReturn 测试 ========================

    @Nested
    @DisplayName("GodlikeAgent.streamAndReturn - 流式执行并返回结果")
    class StreamAndReturnTests {

        @Test
        @DisplayName("返回最终响应文本")
        void testReturnsFinalResponse() {
            GodlikeAgent agent = GodlikeAgent.builder()
                    .model(chatModel)
                    .build();

            List<AgentEvent> events = new ArrayList<>();
            String result = agent.streamAndReturn("测试问题", 0, events::add);

            assertNotNull(result, "最终响应不应为 null");
            assertFalse(result.isBlank(), "最终响应不应为空");
        }

        @Test
        @DisplayName("推送 NODE_EXECUTION 事件")
        void testPushesNodeExecutionEvents() {
            GodlikeAgent agent = GodlikeAgent.builder()
                    .model(chatModel)
                    .build();

            List<AgentEvent> events = new ArrayList<>();
            agent.streamAndReturn("测试", 0, events::add);

            boolean hasNodeExecution = events.stream()
                    .anyMatch(e -> e.getType() == AgentEventType.NODE_EXECUTION);
            assertTrue(hasNodeExecution, "应包含 NODE_EXECUTION 事件");
        }

        @Test
        @DisplayName("最终事件是 FINAL_RESPONSE")
        void testFinalEventIsFinalResponse() {
            GodlikeAgent agent = GodlikeAgent.builder()
                    .model(chatModel)
                    .build();

            List<AgentEvent> events = new ArrayList<>();
            agent.streamAndReturn("测试", 0, events::add);

            // FINAL_RESPONSE 在 suppressed 前仍然会发送（由 streamAndReturn 内部发送）
            // 检查最后一个事件
            AgentEvent lastEvent = events.get(events.size() - 1);
            assertEquals(AgentEventType.FINAL_RESPONSE, lastEvent.getType(),
                    "最后一个事件应为 FINAL_RESPONSE");
        }

        @Test
        @DisplayName("带 delegationDepth 执行")
        void testWithDelegationDepth() {
            GodlikeAgent agent = GodlikeAgent.builder()
                    .model(chatModel)
                    .build();

            List<AgentEvent> events = new ArrayList<>();
            String result = agent.streamAndReturn("测试", 1, events::add);

            assertNotNull(result);
            assertFalse(events.isEmpty(), "应推送事件");
        }

        @Test
        @DisplayName("FINAL_RESPONSE 包含 data 字段")
        void testFinalResponseContainsData() {
            GodlikeAgent agent = GodlikeAgent.builder()
                    .model(chatModel)
                    .build();

            List<AgentEvent> events = new ArrayList<>();
            agent.streamAndReturn("测试", 0, events::add);

            AgentEvent finalEvent = events.stream()
                    .filter(e -> e.getType() == AgentEventType.FINAL_RESPONSE)
                    .findFirst()
                    .orElse(null);

            assertNotNull(finalEvent, "应有 FINAL_RESPONSE 事件");
            assertNotNull(finalEvent.getData(), "FINAL_RESPONSE 应包含 data");
        }
    }

    // ======================== SubAgentExecutor 事件流测试 ========================

    @Nested
    @DisplayName("SubAgentExecutor - 事件流式传播")
    class SubAgentExecutorStreamingTests {

        private SubAgentDefinition testDefinition;
        private ToolProvider mockTool;

        @BeforeEach
        void setUpDefinition() {
            // 添加 mock 工具，让子 Agent 图能快速终止（ToolNode 调用后标记 TODO COMPLETED）
            mockTool = mock(ToolProvider.class);
            when(mockTool.name()).thenReturn("mock-tool");
            when(mockTool.description()).thenReturn("模拟工具");
            when(mockTool.execute(anyMap())).thenReturn(Map.of("success", true, "result", "mock"));

            testDefinition = SubAgentDefinition.builder()
                    .name("test-agent")
                    .description("测试子 Agent")
                    .systemPrompt("你是一个测试子 Agent")
                    .availableTools(List.of("mock-tool"))  // 白名单包含 mock 工具
                    .timeoutSeconds(60L)
                    .recursionLimit(15)
                    .maxDepth(2)
                    .build();
        }

        private SubAgentContext buildContext() {
            return SubAgentContext.builder()
                    .definition(testDefinition)
                    .taskDescription("测试任务")
                    .parentSessionId("parent-session")
                    .parentInstructions("父 Agent 指令")
                    .currentDepth(0)
                    .previousSubAgentResults(new HashMap<>())
                    .build();
        }

        @Test
        @DisplayName("无 EventBus 消费者 - 走同步 run() 路径")
        void testNoConsumer_usesRunPath() {
            assertNull(SubAgentEventBus.get(), "初始应无消费者");

            SubAgentExecutor executor = new SubAgentExecutor(chatModel, List.of(mockTool), null);
            SubAgentResult result = executor.execute(buildContext());

            assertTrue(result.isSuccess(), "无消费者时应正常执行");
            assertNotNull(result.getOutput(), "应有输出结果");
        }

        @Test
        @DisplayName("有 EventBus 消费者 - 推送子 Agent 事件")
        void testWithConsumer_pushesSubAgentEvents() throws Exception {
            List<AgentEvent> capturedEvents = new ArrayList<>();
            CountDownLatch eventLatch = new CountDownLatch(1);

            // 设置 EventBus（模拟 AgentServiceImpl 在图执行线程上的设置）
            SubAgentEventBus.set(event -> {
                capturedEvents.add(event);
            });

            SubAgentExecutor executor = new SubAgentExecutor(chatModel, List.of(mockTool), null);
            SubAgentResult result = executor.execute(buildContext());

            assertTrue(result.isSuccess(), "执行应成功");

            // 等待异步事件完成
            Thread.sleep(500);

            // 验证收到了子 Agent 事件
            assertFalse(capturedEvents.isEmpty(), "应捕获到子 Agent 事件");

            // 验证事件类型
            boolean hasStarted = capturedEvents.stream()
                    .anyMatch(e -> e.getType() == AgentEventType.SUB_AGENT_STARTED);
            assertTrue(hasStarted, "应包含 SUB_AGENT_STARTED 事件");

            boolean hasNodeExecution = capturedEvents.stream()
                    .anyMatch(e -> e.getType() == AgentEventType.SUB_AGENT_NODE_EXECUTION);
            assertTrue(hasNodeExecution, "应包含 SUB_AGENT_NODE_EXECUTION 事件");

            boolean hasCompleted = capturedEvents.stream()
                    .anyMatch(e -> e.getType() == AgentEventType.SUB_AGENT_COMPLETED);
            assertTrue(hasCompleted, "应包含 SUB_AGENT_COMPLETED 事件");

            // 打印事件序列
            System.out.println("捕获的子 Agent 事件 (" + capturedEvents.size() + " 个):");
            for (AgentEvent event : capturedEvents) {
                System.out.println("  [" + event.getType().getValue() + "] " + event.getMessage());
            }
        }

        @Test
        @DisplayName("SUB_AGENT_STARTED 包含元数据")
        void testStartedEventContainsMetadata() throws Exception {
            List<AgentEvent> capturedEvents = new ArrayList<>();
            SubAgentEventBus.set(capturedEvents::add);

            SubAgentExecutor executor = new SubAgentExecutor(chatModel, List.of(mockTool), null);
            executor.execute(buildContext());

            Thread.sleep(500);

            AgentEvent startedEvent = capturedEvents.stream()
                    .filter(e -> e.getType() == AgentEventType.SUB_AGENT_STARTED)
                    .findFirst()
                    .orElse(null);

            assertNotNull(startedEvent, "应有 SUB_AGENT_STARTED 事件");
            assertNotNull(startedEvent.getData(), "SUB_AGENT_STARTED 应包含 data");

            // 验证 data 包含 agentName
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) startedEvent.getData();
            assertEquals("test-agent", data.get("agentName"));
            assertEquals(1, data.get("depth"));
        }

        @Test
        @DisplayName("SUB_AGENT_COMPLETED 不重复（不与 FINAL_RESPONSE 重复）")
        void testNoDuplicateCompletedEvents() throws Exception {
            List<AgentEvent> capturedEvents = new ArrayList<>();
            SubAgentEventBus.set(capturedEvents::add);

            SubAgentExecutor executor = new SubAgentExecutor(chatModel, List.of(mockTool), null);
            executor.execute(buildContext());

            Thread.sleep(500);

            // SUB_AGENT_COMPLETED 应该只有一个（由 executeWithStreaming 发送）
            long completedCount = capturedEvents.stream()
                    .filter(e -> e.getType() == AgentEventType.SUB_AGENT_COMPLETED)
                    .count();
            assertEquals(1, completedCount, "SUB_AGENT_COMPLETED 应只出现 1 次");
        }

        @Test
        @DisplayName("SUB_AGENT_NODE_EXECUTION 消息包含 agent 名称前缀")
        void testNodeEventContainsAgentNamePrefix() throws Exception {
            List<AgentEvent> capturedEvents = new ArrayList<>();
            SubAgentEventBus.set(capturedEvents::add);

            SubAgentExecutor executor = new SubAgentExecutor(chatModel, List.of(mockTool), null);
            executor.execute(buildContext());

            Thread.sleep(500);

            capturedEvents.stream()
                    .filter(e -> e.getType() == AgentEventType.SUB_AGENT_NODE_EXECUTION)
                    .forEach(e -> assertTrue(e.getMessage().startsWith("[test-agent]"),
                            "NODE_EXECUTION 消息应以 [test-agent] 开头: " + e.getMessage()));
        }

        @Test
        @DisplayName("FINAL_RESPONSE 和 ERROR 事件被抑制（不冒泡到父级）")
        void testFinalResponseAndErrorSuppressed() throws Exception {
            List<AgentEvent> capturedEvents = new ArrayList<>();
            SubAgentEventBus.set(capturedEvents::add);

            SubAgentExecutor executor = new SubAgentExecutor(chatModel, List.of(mockTool), null);
            executor.execute(buildContext());

            Thread.sleep(500);

            // 内部的 FINAL_RESPONSE 不应直接冒泡（由 SUB_AGENT_COMPLETED 替代）
            boolean hasInternalFinalResponse = capturedEvents.stream()
                    .anyMatch(e -> e.getType() == AgentEventType.FINAL_RESPONSE);
            assertFalse(hasInternalFinalResponse, "子 Agent 内部 FINAL_RESPONSE 不应冒泡");

            // 内部的 ERROR 不应直接冒泡（由 SUB_AGENT_FAILED 替代）
            boolean hasInternalError = capturedEvents.stream()
                    .anyMatch(e -> e.getType() == AgentEventType.ERROR);
            assertFalse(hasInternalError, "成功场景下不应有 ERROR 事件");
        }

        @Test
        @DisplayName("递归深度超限 - 返回失败结果")
        void testMaxDepthExceeded() {
            SubAgentDefinition deepDef = SubAgentDefinition.builder()
                    .name("deep-agent")
                    .description("深层子 Agent")
                    .maxDepth(1)
                    .build();

            SubAgentContext deepContext = SubAgentContext.builder()
                    .definition(deepDef)
                    .taskDescription("深层任务")
                    .parentSessionId("parent")
                    .parentInstructions("指令")
                    .currentDepth(2)  // 超过 maxDepth=1
                    .previousSubAgentResults(new HashMap<>())
                    .build();

            SubAgentExecutor executor = new SubAgentExecutor(chatModel, List.of(mockTool), null);
            SubAgentResult result = executor.execute(deepContext);

            assertFalse(result.isSuccess(), "超限应返回失败");
            assertTrue(result.getOutput().contains("上限"), "失败信息应包含 '上限'");
        }
    }

    // ======================== 事件完整序列验证 ========================

    @Nested
    @DisplayName("事件序列完整性")
    class EventSequenceTests {

        @Test
        @DisplayName("GodlikeAgent.stream() 事件序列：NODE_EXECUTION* → FINAL_RESPONSE")
        void testStreamEventSequence() {
            GodlikeAgent agent = GodlikeAgent.builder()
                    .model(chatModel)
                    .build();

            List<AgentEvent> events = new ArrayList<>();
            agent.stream("序列测试", events::add);

            assertFalse(events.isEmpty());

            // 最后一个必须是 FINAL_RESPONSE
            assertEquals(AgentEventType.FINAL_RESPONSE, events.get(events.size() - 1).getType());

            // 前面应有 NODE_EXECUTION
            long nodeExecCount = events.stream()
                    .filter(e -> e.getType() == AgentEventType.NODE_EXECUTION)
                    .count();
            assertTrue(nodeExecCount >= 1, "应有至少 1 个 NODE_EXECUTION");
        }

        @Test
        @DisplayName("子 Agent 事件序列：SUB_AGENT_STARTED → NODE_EXECUTION* → SUB_AGENT_COMPLETED")
        void testSubAgentEventSequence() throws Exception {
            List<AgentEvent> capturedEvents = new ArrayList<>();
            SubAgentEventBus.set(capturedEvents::add);

            // 本地 mock 工具
            ToolProvider localMockTool = mock(ToolProvider.class);
            when(localMockTool.name()).thenReturn("mock-tool");
            when(localMockTool.description()).thenReturn("模拟工具");
            when(localMockTool.execute(anyMap())).thenReturn(Map.of("success", true, "result", "mock"));

            SubAgentDefinition definition = SubAgentDefinition.builder()
                    .name("seq-agent")
                    .description("序列测试 Agent")
                    .systemPrompt("测试")
                    .availableTools(List.of("mock-tool"))
                    .timeoutSeconds(60L)
                    .build();

            SubAgentContext context = SubAgentContext.builder()
                    .definition(definition)
                    .taskDescription("序列测试")
                    .parentSessionId("parent")
                    .parentInstructions("指令")
                    .currentDepth(0)
                    .previousSubAgentResults(new HashMap<>())
                    .build();

            SubAgentExecutor executor = new SubAgentExecutor(chatModel, List.of(localMockTool), null);
            executor.execute(context);

            Thread.sleep(500);

            // 提取事件类型序列（去重连续相同类型）
            List<AgentEventType> typeSequence = capturedEvents.stream()
                    .map(AgentEvent::getType)
                    .reduce(new ArrayList<>(), (list, type) -> {
                        if (list.isEmpty() || list.get(list.size() - 1) != type) {
                            list.add(type);
                        }
                        return list;
                    }, (a, b) -> a);

            System.out.println("事件类型序列: " + typeSequence);

            // 验证顺序：STARTED 在前，COMPLETED 在后
            int startedIdx = typeSequence.indexOf(AgentEventType.SUB_AGENT_STARTED);
            int completedIdx = typeSequence.indexOf(AgentEventType.SUB_AGENT_COMPLETED);
            assertTrue(startedIdx >= 0, "应有 SUB_AGENT_STARTED");
            assertTrue(completedIdx >= 0, "应有 SUB_AGENT_COMPLETED");
            assertTrue(startedIdx < completedIdx, "STARTED 应在 COMPLETED 之前");
        }
    }
}
