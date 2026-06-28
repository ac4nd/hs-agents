package com.hypersense.boot.framework.agents.engine.node;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypersense.boot.framework.agents.profile.IntentClassification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 意图分类节点：在 PlanNode 之前单次调用 LLM，识别用户输入的主能力档位。
 *
 * - primary 为主能力（design/code/think/docs/learning）
 * - secondary 为复合任务的串联顺序
 * - confidence < 0.6 应触发 HITL（由调用方判定）
 *
 * 失败兜底：LLM 异常或 JSON 解析失败 → 降级为 code-profile（最通用）+ confidence=0.3
 */
@Component
public class IntentClassifierNode {

    private static final Logger log = LoggerFactory.getLogger(IntentClassifierNode.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            你是意图分类器。根据用户输入，识别最匹配的能力档位。
            只能输出严格 JSON，不要任何解释。

            档位定义：
            - design：视觉产物（PPT/幻灯片/landing page/信息图/海报/主页设计）
            - code：编写/重构/修复源代码
            - think：调研、规划、深度思考、分析报告（合并了原来的 research 与 planning）
            - docs：撰写文档/规范/教程/README
            - learning：教学讲解、知识科普、答疑

            历史 research 和 planning 已合并为 think，不要输出 research 或 planning。

            输出 JSON schema：
            {"primary":"design|code|think|docs|learning","secondary":[],"confidence":0.0~1.0,"reason":"...","profileHints":{"productType":"...","audience":"...","estimatedTokens":5000}}

            用户输入：%s
            """;

    private final ChatModel chatModel;

    public IntentClassifierNode(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public IntentClassification classify(String userInput, String sessionId) {
        String prompt = String.format(SYSTEM_PROMPT_TEMPLATE, userInput);
        List<ChatMessage> messages = List.of(UserMessage.from(prompt));
        ChatResponse response;
        try {
            response = chatModel.chat(messages);
        } catch (Exception e) {
            log.warn("IntentClassifier LLM 调用失败，降级为 code-profile. sessionId={}, err={}",
                    sessionId, e.getMessage());
            return fallback(userInput);
        }

        String text = response.aiMessage().text();
        IntentClassification parsed = tryParse(text, sessionId);
        if (parsed != null) {
            return normalizeLegacy(parsed);
        }

        // 重试 1 次
        log.warn("IntentClassifier JSON 解析失败，重试 1 次. sessionId={}, raw={}", sessionId, text);
        try {
            List<ChatMessage> retryMessages = List.of(
                    SystemMessage.from("注意：上次输出无法解析为 JSON，请严格按 schema 输出，只输出 JSON。"),
                    UserMessage.from(prompt));
            ChatResponse retry = chatModel.chat(retryMessages);
            IntentClassification retryParsed = tryParse(retry.aiMessage().text(), sessionId);
            if (retryParsed != null) return normalizeLegacy(retryParsed);
        } catch (Exception ignored) {
        }
        return fallback(userInput);
    }

    private IntentClassification tryParse(String text, String sessionId) {
        if (text == null) return null;
        String cleaned = extractJson(text);
        try {
            IntentClassification c = MAPPER.readValue(cleaned, IntentClassification.class);
            if (c.primary() == null || c.primary().isBlank()) return null;
            return c;
        } catch (Exception e) {
            log.debug("IntentClassifier JSON 解析失败. sessionId={}, cleaned={}", sessionId, cleaned);
            return null;
        }
    }

    /** 从可能含 markdown code fence 的文本中提取 JSON 对象 */
    private String extractJson(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('\n');
            int end = trimmed.lastIndexOf("```");
            if (start > 0 && end > start) {
                return trimmed.substring(start + 1, end).trim();
            }
        }
        int braceStart = trimmed.indexOf('{');
        int braceEnd = trimmed.lastIndexOf('}');
        if (braceStart >= 0 && braceEnd > braceStart) {
            return trimmed.substring(braceStart, braceEnd + 1);
        }
        return trimmed;
    }

    private IntentClassification normalizeLegacy(IntentClassification c) {
        String primary = c.primary();
        List<String> secondary = c.safeSecondary().stream()
                .map(s -> "research".equalsIgnoreCase(s) || "planning".equalsIgnoreCase(s) ? "think" : s)
                .toList();
        if ("research".equalsIgnoreCase(primary) || "planning".equalsIgnoreCase(primary)) {
            return new IntentClassification("think", secondary, c.confidence(),
                    "legacy mapping: " + primary + " → think; " + c.reason(), c.profileHints());
        }
        return new IntentClassification(primary.toLowerCase(), secondary, c.confidence(),
                c.reason(), c.profileHints());
    }

    private IntentClassification fallback(String userInput) {
        return new IntentClassification(
                "code", List.of(), 0.3,
                "意图识别失败，默认进入 code-profile（最通用）",
                Map.of("fallback", true, "userInput", userInput == null ? "" : userInput));
    }
}
