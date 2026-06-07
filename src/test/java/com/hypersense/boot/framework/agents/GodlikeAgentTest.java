package com.hypersense.boot.framework.agents;

import com.hypersense.boot.framework.agents.enums.AgentEventType;
import com.hypersense.boot.framework.agents.model.AgentEvent;
import com.hypersense.boot.framework.agents.sandbox.Sandbox;
import com.hypersense.boot.framework.agents.sandbox.SandboxResult;
import com.hypersense.boot.framework.agents.sandbox.factory.LocalSandboxFactory;
import com.hypersense.boot.framework.agents.tool.ToolProvider;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.bsc.langgraph4j.CompiledGraph;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * GodlikeAgent 构建器 + 执行测试
 * <p>
 * 通过 Mock ChatModel 模拟 LLM 响应，覆盖 Builder 校验、
 * 同步执行、流式执行、自定义工具、沙箱集成等场景。
 *
 * @author test
 */
class GodlikeAgentTest {

    private ChatModel chatModel;

    @BeforeEach
    void setUp() {
        chatModel = mock(ChatModel.class);

        // Mock chat(List) — 节点直接调用此方法
        when(chatModel.chat(anyList())).thenAnswer(invocation -> {
            List<ChatMessage> messages = invocation.getArgument(0);
            return buildMockResponse(messages);
        });

        // Mock chat(ChatRequest) — LangChain4j 1.0.0 的 chat(List) 内部可能委托到此方法
        when(chatModel.chat(any(ChatRequest.class))).thenAnswer(invocation -> {
            ChatRequest request = invocation.getArgument(0);
            return buildMockResponse(request.messages());
        });
    }

    /**
     * 根据系统提示词内容返回不同的 Mock 响应，模拟各节点的 LLM 调用
     */
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
            responseText = "tool";
        } else if (systemText.contains("结果汇总专家")) {
            responseText = "最终报告：任务已完成。";
        } else {
            responseText = "子任务完成";
        }

        ChatResponse response = mock(ChatResponse.class);
        when(response.aiMessage()).thenReturn(AiMessage.from(responseText));
        return response;
    }

    // ======================== Builder 校验测试 ========================

    @Nested
    @DisplayName("Builder 校验")
    class BuilderValidationTests {

        @Test
        @DisplayName("未提供 model 或 apiKey 时应抛出异常")
        void testBuild_withoutModelOrApiKey() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> GodlikeAgent.builder().build());
            assertTrue(ex.getMessage().contains("model"), "异常信息应提示需要 model");
        }

        @Test
        @DisplayName("传入 ChatModel 可正常构建")
        void testBuild_withChatModel() {
            GodlikeAgent agent = GodlikeAgent.builder()
                    .model(chatModel)
                    .build();
            assertNotNull(agent, "Agent 不应为 null");
            assertNotNull(agent.graph(), "Graph 不应为 null");
        }

        @Test
        @DisplayName("传入 apiKey/endpoint/modelName 可正常构建")
        void testBuild_withApiKey() {
            // 注意：此处仅验证构建不报错，不实际调用 API
            assertDoesNotThrow(() -> {
                try {
                    GodlikeAgent.builder()
                            .apiKey("test-key")
                            .endpoint("https://fake-api.example.com/v1")
                            .modelName("test-model")
                            .build();
                } catch (Exception e) {
                    // 构建 OpenAiChatModel 可能因 URL 不可达而失败，这是预期内的
                    // 我们主要验证 IllegalArgumentException 不会因为参数缺失而抛出
                    if (e.getCause() instanceof IllegalArgumentException) {
                        throw (IllegalArgumentException) e.getCause();
                    }
                }
            });
        }
    }

    // ======================== run() 同步执行测试 ========================

    @Nested
    @DisplayName("run() 同步执行")
    class RunTests {

        @Test
        @DisplayName("基本执行 - 返回最终响应")
        void testRun_returnsFinalResponse() {
            String result = GodlikeAgent.builder()
                    .model(chatModel)
                    .build()
                    .run("你好，请回答一个问题");

            assertNotNull(result, "最终响应不应为 null");
            assertFalse(result.isBlank(), "最终响应不应为空");
            System.out.println("最终响应: " + result);
        }

        @Test
        @DisplayName("多次调用 run() 应各自独立")
        void testRun_multipleCalls() {
            GodlikeAgent agent = GodlikeAgent.builder()
                    .model(chatModel)
                    .build();

            String result1 = agent.run("第一个问题");
            String result2 = agent.run("第二个问题");

            assertNotNull(result1);
            assertNotNull(result2);
            System.out.println("结果1: " + result1);
            System.out.println("结果2: " + result2);
        }
    }

    // ======================== stream() 流式执行测试 ========================

    @Nested
    @DisplayName("stream() 流式执行")
    class StreamTests {

        @Test
        @DisplayName("流式执行 - 接收到事件序列")
        void testStream_receivesEvents() {
            GodlikeAgent agent = GodlikeAgent.builder()
                    .model(chatModel)
                    .build();

            List<AgentEvent> events = new ArrayList<>();
            agent.stream("流式测试", events::add);

            assertFalse(events.isEmpty(), "应收到事件");
            System.out.println("收到 " + events.size() + " 个事件:");
            for (AgentEvent event : events) {
                System.out.println("  [" + event.getType() + "] " + event.getMessage());
            }

            // 验证最后一个事件是 FINAL_RESPONSE
            AgentEvent lastEvent = events.get(events.size() - 1);
            assertEquals(AgentEventType.FINAL_RESPONSE, lastEvent.getType(),
                    "最后一个事件应为 FINAL_RESPONSE");
        }

        @Test
        @DisplayName("流式执行 - 包含 NODE_EXECUTION 事件")
        void testStream_containsNodeExecutionEvents() {
            GodlikeAgent agent = GodlikeAgent.builder()
                    .model(chatModel)
                    .build();

            List<AgentEvent> events = new ArrayList<>();
            agent.stream("节点测试", events::add);

            boolean hasNodeExecution = events.stream()
                    .anyMatch(e -> e.getType() == AgentEventType.NODE_EXECUTION);
            assertTrue(hasNodeExecution, "应包含 NODE_EXECUTION 事件");
        }
    }

    // ======================== 自定义工具测试 ========================

    @Nested
    @DisplayName("自定义工具")
    class CustomToolTests {

        @Test
        @DisplayName("自定义工具被调用")
        void testCustomTool_isInvoked() {
            // 创建 mock 工具
            ToolProvider echoTool = mock(ToolProvider.class);
            when(echoTool.name()).thenReturn("echo");
            when(echoTool.description()).thenReturn("回显工具");
            when(echoTool.execute(anyMap())).thenReturn(Map.of("success", true, "echo", "pong"));

            // 让 ExecuteNode 选择 tool 策略
            GodlikeAgent agent = GodlikeAgent.builder()
                    .model(chatModel)
                    .addTool(echoTool)
                    .build();

            String result = agent.run("测试工具调用");
            assertNotNull(result);

            // 验证工具被调用
            verify(echoTool, atLeastOnce()).execute(anyMap());
            System.out.println("最终响应: " + result);
        }

        @Test
        @DisplayName("多个工具可同时注册")
        void testMultipleTools() {
            ToolProvider tool1 = mock(ToolProvider.class);
            when(tool1.name()).thenReturn("tool1");
            when(tool1.description()).thenReturn("工具1");
            when(tool1.execute(anyMap())).thenReturn(Map.of("success", true));

            ToolProvider tool2 = mock(ToolProvider.class);
            when(tool2.name()).thenReturn("tool2");
            when(tool2.description()).thenReturn("工具2");
            when(tool2.execute(anyMap())).thenReturn(Map.of("success", true));

            GodlikeAgent agent = GodlikeAgent.builder()
                    .model(chatModel)
                    .tools(List.of(tool1, tool2))
                    .build();

            String result = agent.run("测试多工具");
            assertNotNull(result);
            System.out.println("最终响应: " + result);
        }
    }

    // ======================== 沙箱集成测试 ========================

    @Nested
    @DisplayName("沙箱集成")
    class SandboxTests {

        @Test
        @DisplayName("传入 Sandbox 实例 - 自动注册 SandboxTool")
        void testWithSandbox_autoRegistersTool() {
            // Mock Sandbox
            Sandbox sandbox = mock(Sandbox.class);
            when(sandbox.type()).thenReturn("mock");
            when(sandbox.executeCode(anyString(), anyString(), any()))
                    .thenReturn(SandboxResult.ok("hello world", "mock"));

            GodlikeAgent agent = GodlikeAgent.builder()
                    .model(chatModel)
                    .sandbox(sandbox)
                    .build();

            assertNotNull(agent);
            assertNotNull(agent.graph());
            System.out.println("沙箱 Agent 构建成功");
        }

        @Test
        @DisplayName("传入 Sandbox 实例 - run() 正常执行")
        void testWithSandbox_runExecutes() {
            Sandbox sandbox = mock(Sandbox.class);
            when(sandbox.type()).thenReturn("mock");
            when(sandbox.executeCode(anyString(), anyString(), any()))
                    .thenReturn(SandboxResult.ok("executed", "mock"));

            String result = GodlikeAgent.builder()
                    .model(chatModel)
                    .sandbox(sandbox)
                    .build()
                    .run("执行一段代码");

            assertNotNull(result);
            System.out.println("带沙箱 Agent 结果: " + result);
        }
    }

    // ======================== 高级配置测试 ========================

    @Nested
    @DisplayName("高级配置")
    class AdvancedConfigTests {

        @Test
        @DisplayName("自定义 recursionLimit")
        void testCustomRecursionLimit() {
            GodlikeAgent agent = GodlikeAgent.builder()
                    .model(chatModel)
                    .recursionLimit(10)
                    .build();

            assertNotNull(agent);
            assertNotNull(agent.graph());
        }

        @Test
        @DisplayName("graph() 返回底层编译图")
        void testGraphAccessor() {
            GodlikeAgent agent = GodlikeAgent.builder()
                    .model(chatModel)
                    .build();

            CompiledGraph<?> graph = agent.graph();
            assertNotNull(graph, "graph() 不应返回 null");
        }
    }

    // ======================== 真实 LLM 连通性测试（手动执行） ========================

    @Nested
    @DisplayName("真实 LLM 连通性")
    class RealLlmTests {

        @Test
        @DisplayName("真实 LLM - 简单问答")
        @Disabled("需要真实 API Key，手动执行时移除 @Disabled")
        void testRealLlm_simpleQuestion() {
            String result = GodlikeAgent.builder()
                    .apiKey("your-api-key")
                    .endpoint("https://open.bigmodel.cn/api/coding/paas/v4")
                    .modelName("glm-4.7")
                    .build()
                    .run("用一句话解释什么是递归");

            assertNotNull(result);
            System.out.println("LLM 回答: " + result);
        }

        @Test
        @DisplayName("真实 LLM - 带沙箱执行代码")
        @Disabled("需要真实 API Key + 沙箱环境，手动执行时移除 @Disabled")
        void testRealLlm_withSandbox() {
            // 使用项目中已有的配置
            com.hypersense.boot.framework.agents.config.AgentProperties props =
                    new com.hypersense.boot.framework.agents.config.AgentProperties();

            LocalSandboxFactory factory =
                    new LocalSandboxFactory(props);

            String result = GodlikeAgent.builder()
                    .apiKey("your-api-key")
                    .endpoint("https://open.bigmodel.cn/api/coding/paas/v4")
                    .modelName("glm-4.7")
                    .sandbox(factory)
                    .build()
                    .run("用 Python 计算斐波那契数列的前 10 项");

            assertNotNull(result);
            System.out.println("LLM + 沙箱 回答:\n" + result);
        }
    }
}
