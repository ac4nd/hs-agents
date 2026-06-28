package com.hypersense.boot.framework.agents.engine.node;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypersense.boot.framework.agents.profile.IntentClassification;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.data.message.AiMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IntentClassifierNodeTest {

    private ChatModel chatModel;
    private IntentClassifierNode node;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @BeforeEach
    void setUp() {
        chatModel = Mockito.mock(ChatModel.class);
        node = new IntentClassifierNode(chatModel);
    }

    @Test
    void shouldParseValidJsonResponse() throws Exception {
        String json = MAPPER.writeValueAsString(new IntentClassification(
                "design", List.of(), 0.92, "PPT 请求", Map.of("productType", "ppt")));
        when(chatModel.chat(anyList())).thenReturn(
                ChatResponse.builder().aiMessage(AiMessage.from(json)).build());

        IntentClassification result = node.classify("做一份世界杯 PPT", "sess-1");

        assertEquals("design", result.primary());
        assertEquals(0.92, result.confidence());
        assertTrue(result.safeSecondary().isEmpty());
    }

    @Test
    void shouldFallbackToCodeWhenLlmThrows() {
        when(chatModel.chat(anyList())).thenThrow(new RuntimeException("LLM down"));

        IntentClassification result = node.classify("任意输入", "sess-1");

        assertEquals("code", result.primary());
        assertTrue(result.confidence() < 0.5);
        verify(chatModel, times(1)).chat(anyList());
    }

    @Test
    void shouldFallbackToCodeWhenJsonInvalid() {
        when(chatModel.chat(anyList())).thenReturn(
                ChatResponse.builder().aiMessage(AiMessage.from("not a json")).build());

        IntentClassification result = node.classify("任意输入", "sess-1");

        assertEquals("code", result.primary());
    }

    @Test
    void shouldRetryOnceOnMalformedJson() throws Exception {
        String validJson = MAPPER.writeValueAsString(new IntentClassification(
                "research", List.of("code"), 0.85, "复合任务", Map.of()));
        when(chatModel.chat(anyList()))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("malformed")).build())
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from(validJson)).build());

        IntentClassification result = node.classify("调研 RAG 并实现 demo", "sess-1");

        assertEquals("think", result.primary()); // 注：Plan A 把 research 统一映射到 think
        assertEquals(List.of("code"), result.safeSecondary());
        verify(chatModel, times(2)).chat(anyList());
    }

    @Test
    void shouldMapLegacyResearchToThink() throws Exception {
        String json = MAPPER.writeValueAsString(new IntentClassification(
                "research", List.of(), 0.9, "调研", Map.of()));
        when(chatModel.chat(anyList())).thenReturn(
                ChatResponse.builder().aiMessage(AiMessage.from(json)).build());

        IntentClassification result = node.classify("调研 RAG", "sess-1");

        assertEquals("think", result.primary());
    }
}
