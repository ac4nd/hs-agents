package com.hypersense.boot.framework.agents.tool.impl;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * DesignDirectionExploreTool 单测。
 * <p>
 * 注意：spec 草稿里 mock 的是 {@code chatModel.chat(anyString())}，但 LangChain4j 1.0.0
 * 的 {@code chat(String)} 返回 {@code String} 而非 {@code ChatResponse}。返回 ChatResponse
 * 的是 {@code chat(List<ChatMessage>)} 重载（见 IntentClassifierNode 的用法）。为了让测试
 * 既编译通过又保持确定性，这里 mock 的是 {@code chat(List<ChatMessage>)} 重载。
 * </p>
 */
class DesignDirectionExploreToolTest {

    private ChatModel chatModel;
    private DesignDirectionExploreTool tool;

    @BeforeEach
    void setUp() {
        chatModel = Mockito.mock(ChatModel.class);
        tool = new DesignDirectionExploreTool(chatModel);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnThreeDirectionsOnValidJson() {
        String mockResponse = """
                {
                  "outlines": [
                    {"logic":"roulette","anchor":"秒数掷骰","slides":[{"id":"s1","headline":"A"}]},
                    {"logic":"reference","anchor":"Apple官网","slides":[{"id":"s1","headline":"B"}]},
                    {"logic":"designer","anchor":"Pentagram","slides":[{"id":"s1","headline":"C"}]}
                  ]
                }
                """;
        when(chatModel.chat(any(List.class))).thenReturn(
                ChatResponse.builder().aiMessage(AiMessage.from(mockResponse)).build());

        Map<String, Object> result = tool.explore(
                Map.of("title", "世界杯 PPT", "audience", "球迷"),
                "s1");

        List<Map<String, Object>> outlines = (List<Map<String, Object>>) result.get("outlines");
        assertEquals(3, outlines.size());
        assertEquals("roulette", outlines.get(0).get("logic"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldFallbackToThreeGenericOutlinesOnParseError() {
        when(chatModel.chat(any(List.class))).thenReturn(
                ChatResponse.builder().aiMessage(AiMessage.from("not a json")).build());

        Map<String, Object> result = tool.explore(Map.of("title", "x"), "s1");

        List<Map<String, Object>> outlines = (List<Map<String, Object>>) result.get("outlines");
        assertEquals(3, outlines.size(), "解析失败也必须返回 3 个兜底方向");
        assertTrue(outlines.stream().anyMatch(o -> "roulette".equals(o.get("logic"))));
    }
}
