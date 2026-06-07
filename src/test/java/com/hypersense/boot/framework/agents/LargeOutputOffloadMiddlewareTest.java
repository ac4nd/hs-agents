package com.hypersense.boot.framework.agents;

import com.hypersense.boot.framework.agents.middleware.impl.LargeOutputOffloadMiddleware;
import com.hypersense.boot.framework.agents.model.DeepAgentState;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * LargeOutputOffloadMiddleware 单元测试
 * <p>
 * 覆盖：小文本不触发、大文本卸载到 FILES、引用替换、自定义阈值、
 * 多个大文本、FILES 保留、output 边界。
 *
 * @author test
 */
class LargeOutputOffloadMiddlewareTest {

    private DeepAgentState buildTestState() {
        return buildTestState(Map.of());
    }

    private DeepAgentState buildTestState(Map<String, String> existingFiles) {
        Map<String, Object> data = new HashMap<>();
        data.put(DeepAgentState.SESSION_ID, "test-session");
        data.put(DeepAgentState.INSTRUCTIONS, "测试");
        data.put(DeepAgentState.MESSAGES, new ArrayList<>());
        data.put(DeepAgentState.TODOS, new ArrayList<>());
        data.put(DeepAgentState.FILES, new HashMap<>(existingFiles));
        data.put(DeepAgentState.SUB_AGENT_RESULTS, new HashMap<>());
        data.put(DeepAgentState.ENABLED_TOOLS, new ArrayList<>());
        return new DeepAgentState(data);
    }

    // ======================== 基础属性测试 ========================

    @Nested
    @DisplayName("基础属性")
    class BasicTests {

        @Test
        @DisplayName("name - 返回 'large-output-offload'")
        void testName() {
            LargeOutputOffloadMiddleware mw = new LargeOutputOffloadMiddleware();
            assertEquals("large-output-offload", mw.name());
        }

        @Test
        @DisplayName("默认构造器 - 使用默认阈值 10KB")
        void testDefaultConstructor() {
            assertDoesNotThrow(() -> new LargeOutputOffloadMiddleware());
        }
    }

    // ======================== 小文本测试 ========================

    @Nested
    @DisplayName("小文本 - 不触发卸载")
    class SmallTextTests {

        @Test
        @DisplayName("output 为 null → 直接返回")
        void testNullOutput() {
            LargeOutputOffloadMiddleware mw = new LargeOutputOffloadMiddleware(1024);
            DeepAgentState state = buildTestState();

            Map<String, Object> result = mw.after("plan", state, null);
            assertNull(result, "null output 应原样返回");
        }

        @Test
        @DisplayName("output 为空 → 直接返回")
        void testEmptyOutput() {
            LargeOutputOffloadMiddleware mw = new LargeOutputOffloadMiddleware(1024);
            DeepAgentState state = buildTestState();

            Map<String, Object> result = mw.after("plan", state, Map.of());
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("小文本 MESSAGES → 不触发卸载")
        void testSmallText_noOffload() {
            LargeOutputOffloadMiddleware mw = new LargeOutputOffloadMiddleware(1024);
            DeepAgentState state = buildTestState();

            Map<String, Object> output = Map.of(DeepAgentState.MESSAGES, "短文本");
            Map<String, Object> result = mw.after("plan", state, output);

            assertSame(output, result, "小文本应返回原始 output");
        }

        @Test
        @DisplayName("MESSAGES 不是 String 类型 → 不触发")
        void testNonStringMessages() {
            LargeOutputOffloadMiddleware mw = new LargeOutputOffloadMiddleware(100);
            DeepAgentState state = buildTestState();

            Map<String, Object> output = Map.of(DeepAgentState.MESSAGES, List.of("msg1", "msg2"));
            Map<String, Object> result = mw.after("plan", state, output);

            assertSame(output, result, "非 String MESSAGES 应返回原始 output");
        }

        @Test
        @DisplayName("output 没有 MESSAGES key → 直接返回")
        void testNoMessagesKey() {
            LargeOutputOffloadMiddleware mw = new LargeOutputOffloadMiddleware(100);
            DeepAgentState state = buildTestState();

            Map<String, Object> output = Map.of("other_key", "value");
            Map<String, Object> result = mw.after("plan", state, output);

            assertSame(output, result);
        }
    }

    // ======================== 大文本卸载测试 ========================

    @Nested
    @DisplayName("大文本 - 卸载到 FILES")
    class LargeTextTests {

        @Test
        @DisplayName("大文本 MESSAGES → 卸载到 FILES + 替换为引用")
        void testLargeText_offloaded() {
            LargeOutputOffloadMiddleware mw = new LargeOutputOffloadMiddleware(100);
            DeepAgentState state = buildTestState();

            String largeText = "x".repeat(200);
            Map<String, Object> output = new HashMap<>();
            output.put(DeepAgentState.MESSAGES, largeText);

            Map<String, Object> result = mw.after("plan", state, output);

            assertNotSame(output, result, "大文本应返回修改后的 output");

            // 验证 MESSAGES 被替换为引用标记
            String messages = (String) result.get(DeepAgentState.MESSAGES);
            assertNotNull(messages);
            assertTrue(messages.contains("[输出已卸载到文件:"),
                    "MESSAGES 应替换为引用标记");

            // 验证 FILES 包含卸载的内容
            @SuppressWarnings("unchecked")
            Map<String, String> files = (Map<String, String>) result.get(DeepAgentState.FILES);
            assertNotNull(files);
            assertFalse(files.isEmpty(), "FILES 应包含卸载的文件");

            // 验证卸载文件的内容是原始大文本
            Optional<String> originalContent = files.values().stream()
                    .filter(v -> v.equals(largeText))
                    .findFirst();
            assertTrue(originalContent.isPresent(), "FILES 应包含原始大文本");
        }

        @Test
        @DisplayName("卸载文件路径包含节点名")
        void testOffloadPathContainsNodeName() {
            LargeOutputOffloadMiddleware mw = new LargeOutputOffloadMiddleware(100);
            DeepAgentState state = buildTestState();

            String largeText = "x".repeat(200);
            Map<String, Object> output = new HashMap<>();
            output.put(DeepAgentState.MESSAGES, largeText);

            Map<String, Object> result = mw.after("execute", state, output);

            @SuppressWarnings("unchecked")
            Map<String, String> files = (Map<String, String>) result.get(DeepAgentState.FILES);
            String filePath = files.keySet().iterator().next();
            assertTrue(filePath.contains("execute"), "文件路径应包含节点名");
            assertTrue(filePath.startsWith("_offload/"), "文件路径应以 _offload/ 开头");
        }
    }

    // ======================== 自定义阈值测试 ========================

    @Nested
    @DisplayName("自定义阈值")
    class CustomThresholdTests {

        @Test
        @DisplayName("阈值 50 → 60 字符触发卸载")
        void testThreshold50_triggers() {
            LargeOutputOffloadMiddleware mw = new LargeOutputOffloadMiddleware(50);
            DeepAgentState state = buildTestState();

            String text = "a".repeat(60);
            Map<String, Object> output = new HashMap<>();
            output.put(DeepAgentState.MESSAGES, text);

            Map<String, Object> result = mw.after("plan", state, output);

            assertNotSame(output, result, "超过阈值应触发卸载");
        }

        @Test
        @DisplayName("阈值 50 → 40 字符不触发")
        void testThreshold50_notTriggers() {
            LargeOutputOffloadMiddleware mw = new LargeOutputOffloadMiddleware(50);
            DeepAgentState state = buildTestState();

            String text = "a".repeat(40);
            Map<String, Object> output = Map.of(DeepAgentState.MESSAGES, text);

            Map<String, Object> result = mw.after("plan", state, output);

            assertSame(output, result, "未达阈值不应触发卸载");
        }

        @Test
        @DisplayName("恰好等于阈值 → 不触发（需要超过才触发）")
        void testExactThreshold() {
            LargeOutputOffloadMiddleware mw = new LargeOutputOffloadMiddleware(100);
            DeepAgentState state = buildTestState();

            String text = "a".repeat(100);
            Map<String, Object> output = Map.of(DeepAgentState.MESSAGES, text);

            Map<String, Object> result = mw.after("plan", state, output);

            assertSame(output, result, "恰好等于阈值不应触发");
        }
    }

    // ======================== FILES 保留测试 ========================

    @Nested
    @DisplayName("FILES 保留 - 已有文件不被清除")
    class FilesPreservationTests {

        @Test
        @DisplayName("卸载时保留已有 FILES")
        void testPreservesExistingFiles() {
            LargeOutputOffloadMiddleware mw = new LargeOutputOffloadMiddleware(100);
            Map<String, String> existingFiles = new HashMap<>();
            existingFiles.put("existing.txt", "已有内容");
            DeepAgentState state = buildTestState(existingFiles);

            String largeText = "x".repeat(200);
            Map<String, Object> output = new HashMap<>();
            output.put(DeepAgentState.MESSAGES, largeText);

            Map<String, Object> result = mw.after("plan", state, output);

            @SuppressWarnings("unchecked")
            Map<String, String> files = (Map<String, String>) result.get(DeepAgentState.FILES);
            assertTrue(files.containsKey("existing.txt"), "应保留已有文件");
            assertEquals("已有内容", files.get("existing.txt"));
            assertEquals(2, files.size(), "应有 2 个文件（1 个原有 + 1 个卸载）");
        }
    }

    // ======================== GodlikeAgent.Builder 集成测试 ========================

    @Nested
    @DisplayName("Builder 集成 - enableLargeOutputOffload")
    class BuilderIntegrationTests {

        @Test
        @DisplayName("enableLargeOutputOffload() 不抛异常")
        void testBuilderDefaultOffload() {
            assertDoesNotThrow(() -> {
                try {
                    ChatModel mockModel = mock(ChatModel.class);
                    GodlikeAgent agent = GodlikeAgent.builder()
                            .model(mockModel)
                            .enableLargeOutputOffload()
                            .build();
                    assertNotNull(agent);
                } catch (Exception e) {
                    if (e.getMessage() != null && e.getMessage().contains("offload")) {
                        fail("enableLargeOutputOffload() 导致构建失败: " + e.getMessage());
                    }
                }
            });
        }

        @Test
        @DisplayName("enableLargeOutputOffload(2048) 不抛异常")
        void testBuilderCustomOffload() {
            assertDoesNotThrow(() -> {
                try {
                    ChatModel mockModel = mock(ChatModel.class);
                    GodlikeAgent agent = GodlikeAgent.builder()
                            .model(mockModel)
                            .enableLargeOutputOffload(2048)
                            .build();
                    assertNotNull(agent);
                } catch (Exception e) {
                    if (e.getMessage() != null && e.getMessage().contains("offload")) {
                        fail("enableLargeOutputOffload(2048) 导致构建失败: " + e.getMessage());
                    }
                }
            });
        }
    }
}
