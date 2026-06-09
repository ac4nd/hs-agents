package com.hypersense.boot;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LLM ChatModel 连通性与功能测试
 * <p>
 * 测试 LangChain4J OpenAiChatModel 与远程 LLM API 的集成。
 * 配置来源于 application-local.yml 中的 agent.llm 配置项。
 * <p>
 * 运行前确保：
 * 1. application-local.yml 中 agent.llm.openai.api-key 已配置有效的 API Key
 * 2. 网络可访问对应的 LLM API 端点
 *
 * @author test
 */
class LLMChatModelTest {

    // ========== 配置常量（与 application-local.yml 保持一致） ==========
    private static final String ENDPOINT = "https://open.bigmodel.cn/api/coding/paas/v4";
    private static final String API_KEY = "40a1cff4ec6c45a09704ec79550211a3.eLcaJYrFS2unG829";
    private static final String MODEL_NAME = "glm-4.7";
    private static final double TEMPERATURE = 0.7;
    private static final int MAX_TOKENS = 4096;

    private ChatModel chatModel;

    @BeforeEach
    void setUp() {
        chatModel = OpenAiChatModel.builder()
                .baseUrl(ENDPOINT)
                .apiKey(API_KEY)
                .modelName(MODEL_NAME)
                .temperature(TEMPERATURE)
                .maxTokens(MAX_TOKENS)
                .timeout(Duration.ofSeconds(120))
                .build();
    }

    // ======================== 基础连通性测试 ========================

    @Test
    @DisplayName("基础连通 - 单轮对话，验证 LLM 响应非空")
    void testBasicChat() {
        String prompt = "请用一句话回答：1+1等于几？";

        ChatResponse response = chatModel.chat(UserMessage.from(prompt));

        assertNotNull(response, "响应不应为 null");
        assertNotNull(response.aiMessage(), "AI 消息不应为 null");

        String text = response.aiMessage().text();
        assertNotNull(text, "响应文本不应为 null");
        assertFalse(text.isBlank(), "响应文本不应为空");

        System.out.println("提问: " + prompt);
        System.out.println("回答: " + text);
    }

    @Test
    @DisplayName("基础连通 - 验证响应包含 Token 使用信息")
    void testTokenUsage() {
        ChatResponse response = chatModel.chat(UserMessage.from("Hello"));

        assertNotNull(response, "响应不应为 null");
        assertNotNull(response.tokenUsage(), "Token 使用信息不应为 null");
        assertTrue(response.tokenUsage().inputTokenCount() > 0, "输入 Token 数应大于 0");
        assertTrue(response.tokenUsage().outputTokenCount() > 0, "输出 Token 数应大于 0");

        System.out.println("输入 Token: " + response.tokenUsage().inputTokenCount());
        System.out.println("输出 Token: " + response.tokenUsage().outputTokenCount());
    }

    // ======================== SystemMessage 测试 ========================

    @Test
    @DisplayName("角色设定 - 验证 SystemMessage 对回答风格的控制")
    void testSystemMessage() {
        var messages = List.of(
                SystemMessage.from("你是一个只能用中文回答的助手，回答不超过20个字。"),
                UserMessage.from("What is Java?")
        );

        ChatResponse response = chatModel.chat(messages);

        assertNotNull(response, "响应不应为 null");
        String text = response.aiMessage().text();
        assertNotNull(text, "响应文本不应为 null");
        assertFalse(text.isBlank(), "响应文本不应为空");

        System.out.println("System: 你是一个只能用中文回答的助手，回答不超过20个字。");
        System.out.println("User: What is Java?");
        System.out.println("AI: " + text);
    }

    // ======================== 多轮对话测试 ========================

    @Test
    @DisplayName("多轮对话 - 验证上下文记忆能力")
    void testMultiTurnChat() {
        // 第一轮
        UserMessage msg1 = UserMessage.from("记住这个数字：42");
        ChatResponse resp1 = chatModel.chat(msg1);
        String answer1 = resp1.aiMessage().text();
        assertNotNull(answer1, "第一轮响应不应为 null");
        assertFalse(answer1.isBlank(), "第一轮响应不应为空");

        // 第二轮：携带上下文
        AiMessage aiMsg1 = resp1.aiMessage();
        UserMessage msg2 = UserMessage.from("我刚才让你记住的数字是多少？");
        ChatResponse resp2 = chatModel.chat(List.of(msg1, aiMsg1, msg2));
        String answer2 = resp2.aiMessage().text();
        assertNotNull(answer2, "第二轮响应不应为 null");
        assertTrue(answer2.contains("42"), "第二轮响应应包含之前记住的数字 42");

        System.out.println("第一轮 Q: " + msg1.singleText());
        System.out.println("第一轮 A: " + answer1);
        System.out.println("第二轮 Q: " + msg2.singleText());
        System.out.println("第二轮 A: " + answer2);
    }

    // ======================== Agent 场景模拟测试 ========================

    @Test
    @DisplayName("Agent 场景 - 模拟任务规划（PlanNode 行为）")
    void testAgentPlanningScenario() {
        String systemPrompt = """
                你是一个任务规划助手。用户会给你一个目标，你需要将其拆解为有序的 TODO 列表。
                每个 TODO 占一行，格式为：- [ ] 任务描述
                """;

        String userPrompt = "帮我写一个 Python 冒泡排序算法";

        var messages = List.of(
                SystemMessage.from(systemPrompt),
                UserMessage.from(userPrompt)
        );

        ChatResponse response = chatModel.chat(messages);
        String text = response.aiMessage().text();

        assertNotNull(text, "规划响应不应为 null");
        assertFalse(text.isBlank(), "规划响应不应为空");
        assertTrue(text.contains("- [ ]") || text.contains("TODO") || text.contains("1."),
                "规划响应应包含 TODO 列表格式");

        System.out.println("用户目标: " + userPrompt);
        System.out.println("规划结果:\n" + text);
    }

    @Test
    @DisplayName("Agent 场景 - 模拟工具调用判断")
    void testAgentToolSelectionScenario() {
        String systemPrompt = """
                你是一个 AI Agent，可以判断是否需要使用工具来完成任务。
                可用工具：
                1. internet_search - 网络搜索（需要最新信息时使用）
                2. sandbox - 沙箱工具（需要运行代码时使用）
                3. file_read - 文件读取

                请判断以下任务需要使用哪些工具，以 JSON 数组格式回复，例如：["internet_search"]
                如果不需要工具，回复：[]
                """;

        // 场景 1：需要搜索
        ChatResponse resp1 = chatModel.chat(List.of(
                SystemMessage.from(systemPrompt),
                UserMessage.from("今天北京的天气怎么样？")
        ));
        String answer1 = resp1.aiMessage().text();
        assertTrue(answer1.contains("internet_search"), "天气查询应需要 internet_search 工具");

        // 场景 2：需要代码执行
        ChatResponse resp2 = chatModel.chat(List.of(
                SystemMessage.from(systemPrompt),
                UserMessage.from("用 Python 计算 2^100")
        ));
        String answer2 = resp2.aiMessage().text();
        assertTrue(answer2.contains("sandbox"), "代码计算应需要 sandbox 工具");

        // 场景 3：不需要工具
        ChatResponse resp3 = chatModel.chat(List.of(
                SystemMessage.from(systemPrompt),
                UserMessage.from("用你自己的话解释什么是递归")
        ));
        String answer3 = resp3.aiMessage().text();
        assertTrue(answer3.contains("[]") || !answer3.contains("sandbox") && !answer3.contains("internet_search"),
                "解释概念不应需要工具");

        System.out.println("场景1(天气): " + answer1);
        System.out.println("场景2(代码): " + answer2);
        System.out.println("场景3(解释): " + answer3);
    }

    // ======================== 异常场景测试 ========================

    @Test
    @DisplayName("异常处理 - 验证超时配置生效")
    void testTimeoutConfiguration() {
        // 使用极短超时验证配置生效
        ChatModel shortTimeoutModel = OpenAiChatModel.builder()
                .baseUrl(ENDPOINT)
                .apiKey(API_KEY)
                .modelName(MODEL_NAME)
                .timeout(Duration.ofMillis(1)) // 1ms，必定超时
                .build();

        assertThrows(Exception.class, () -> shortTimeoutModel.chat(UserMessage.from("Hello")),
                "极短超时配置应导致请求失败");
    }
}
