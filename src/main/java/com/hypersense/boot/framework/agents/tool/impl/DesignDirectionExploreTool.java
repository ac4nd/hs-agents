package com.hypersense.boot.framework.agents.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypersense.boot.framework.agents.tool.ToolProvider;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * design_direction_explore 工具 — LLM 驱动的 3 大纲方向探索（spec §3.6 / Plan B Task 10）。
 * <p>
 * 在 design-profile 进入大纲生成前，单次调用 LLM，并行产出 3 条逻辑支线不同的方向草稿，
 * 让用户/下游节点在更高抽象层做"先选路再细化"的决策，避免一次性铺到细节后才发现走错。
 * </p>
 *
 * <h3>三条逻辑支线</h3>
 * <ul>
 *   <li><b>roulette</b>（机制转盘）：以随机化/抽奖/秒数掷骰等交互机制为锚</li>
 *   <li><b>reference</b>（标杆致敬）：以可识别的成熟产品/品牌官网为锚（Apple、Stripe 等）</li>
 *   <li><b>designer</b>（设计师手法）：以知名设计工作室手法为锚（Pentagram、Studio Dumbar 等）</li>
 * </ul>
 *
 * <h3>容错策略</h3>
 * LLM 调用异常或 JSON 解析失败时，必须返回 3 条兜底方向（每条 logic 不同），
 * 保证下游永远拿到契约里承诺的 3 个 outline，不出现空数组导致流程中断。
 *
 * <h3>chat() 重载选择</h3>
 * LangChain4j 1.0.0 的 {@code ChatModel} 有两个常用重载：
 * {@code chat(String)→String} 与 {@code chat(List<ChatMessage>)→ChatResponse}。
 * 本工具采用后者，与 {@code IntentClassifierNode} 保持一致，便于统一取 {@code aiMessage().text()}
 * 并兼容带系统消息的多轮重试扩展。
 *
 * @author Claude
 * @since 2026/6/29
 */
@Component
public class DesignDirectionExploreTool implements ToolProvider {

    private static final Logger log = LoggerFactory.getLogger(DesignDirectionExploreTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String PROMPT_TEMPLATE = """
            你是高级创意总监。基于以下 spec，并行生成 3 条逻辑支线完全不同的幻灯片大纲方向。
            只输出严格 JSON，不要任何解释或 markdown 代码块。

            三条支线定义（必须各不相同）：
            - roulette：以随机化/抽奖/秒数掷骰等"机制"为锚点，强调交互钩子
            - reference：以可识别的成熟产品/品牌官网为锚点（如 Apple、Stripe、Linear）
            - designer：以知名设计工作室手法为锚点（如 Pentagram、Studio Dumbar、Method）

            spec: %s
            sessionId: %s

            输出 JSON schema：
            {"outlines":[{"logic":"roulette|reference|designer","anchor":"...","slides":[{"id":"s1","headline":"..."}]}]}

            约束：outlines 数组长度恰好为 3，logic 各不相同，每条至少含 1 张 slide。
            """;

    private final ChatModel chatModel;

    @Autowired
    public DesignDirectionExploreTool(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public String name() {
        return "design_direction_explore";
    }

    @Override
    public String description() {
        return "在生成幻灯片大纲前，并行探索 3 条逻辑支线（roulette机制/reference标杆/designer设计师）"
                + "的方向草稿，让 LLM 在更高抽象层先做路向选择，再细化。"
                + "解析失败时返回 3 条兜底方向。";
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name("design_direction_explore")
                .description("并行探索 3 条不同逻辑支线（roulette/reference/designer）的幻灯片大纲方向。")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("spec", JsonObjectSchema.builder()
                                .addStringProperty("title", "产物标题（如：世界杯 PPT）")
                                .addStringProperty("audience", "目标受众")
                                .addStringProperty("productType", "产物类型：slides|landing|infographic|poster")
                                .build())
                        .addStringProperty("sessionId", "会话 id（ToolNode 自动注入）")
                        .required(List.of("spec"))
                        .build())
                .build();
    }

    @Override
    public Object execute(Map<String, Object> params) {
        if (params == null) {
            return explore(new LinkedHashMap<>(), null);
        }
        Object spec = params.get("spec");
        Map<String, Object> specMap;
        if (spec instanceof Map<?, ?> m) {
            specMap = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (e.getKey() != null) {
                    specMap.put(e.getKey().toString(), e.getValue());
                }
            }
        } else {
            // spec 未以对象形式传过来时，把整个 params（去掉 sessionId）当 spec 用
            specMap = new LinkedHashMap<>(params);
            specMap.remove("sessionId");
        }
        String sessionId = pickString(params, "sessionId");
        return explore(specMap, sessionId);
    }

    /**
     * 核心入口：调用 LLM 生成 3 条方向，解析失败兜底。
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> explore(Map<String, Object> spec, String sessionId) {
        Map<String, Object> safeSpec = spec == null ? new LinkedHashMap<>() : spec;
        String prompt = String.format(PROMPT_TEMPLATE, jsonOrString(safeSpec), sessionId);

        String raw;
        try {
            List<ChatMessage> messages = List.of(UserMessage.from(prompt));
            ChatResponse response = chatModel.chat(messages);
            raw = response.aiMessage().text();
        } catch (Exception e) {
            log.warn("design_direction_explore LLM 调用失败，走兜底. sessionId={}, err={}",
                    sessionId, e.getMessage());
            return fallback();
        }

        List<Map<String, Object>> outlines = tryParseOutlines(raw, sessionId);
        if (outlines != null && outlines.size() == 3) {
            Map<String, Object> ok = new LinkedHashMap<>();
            ok.put("outlines", outlines);
            ok.put("source", "llm");
            return ok;
        }
        log.warn("design_direction_explore JSON 解析失败或长度!=3，走兜底. sessionId={}, raw={}",
                sessionId, raw);
        return fallback();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> tryParseOutlines(String text, String sessionId) {
        if (text == null) return null;
        String cleaned = extractJson(text);
        try {
            Map<String, Object> root = MAPPER.readValue(cleaned, Map.class);
            Object out = root.get("outlines");
            if (!(out instanceof List<?> list)) return null;
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof Map<?, ?> mm) {
                    Map<String, Object> conv = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> e : mm.entrySet()) {
                        if (e.getKey() != null) conv.put(e.getKey().toString(), e.getValue());
                    }
                    result.add(conv);
                }
            }
            return result;
        } catch (Exception e) {
            log.debug("design_direction_explore 解析异常. sessionId={}, cleaned={}", sessionId, cleaned);
            return null;
        }
    }

    /** 从可能含 markdown code fence 的文本中提取 JSON 对象。与 IntentClassifierNode 同口径。 */
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

    /** 3 条兜底方向：每条 logic 各不相同，保证契约长度恒为 3。 */
    private Map<String, Object> fallback() {
        List<Map<String, Object>> outlines = new ArrayList<>();
        outlines.add(buildFallbackOutline("roulette", "秒数掷骰",
                "用秒数滚动/落定机制做封面钩子，内部页保留随机彩蛋"));
        outlines.add(buildFallbackOutline("reference", "Apple官网",
                "Apple 产品页大留白 + 滚动视差，标题大字号居中、配 1 张高质感主图"));
        outlines.add(buildFallbackOutline("designer", "Pentagram",
                "Pentagram 式强网格 + 无衬线大标题，每页 12 列对齐、用色克制（黑+1 强调色）"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("outlines", outlines);
        result.put("source", "fallback");
        return result;
    }

    private Map<String, Object> buildFallbackOutline(String logic, String anchor, String copy) {
        Map<String, Object> slide = new LinkedHashMap<>();
        slide.put("id", "s1");
        slide.put("headline", copy);
        Map<String, Object> outline = new LinkedHashMap<>();
        outline.put("logic", logic);
        outline.put("anchor", anchor);
        outline.put("slides", List.of(slide));
        return outline;
    }

    private static String jsonOrString(Map<String, Object> spec) {
        try {
            return MAPPER.writeValueAsString(spec);
        } catch (Exception e) {
            return spec.toString();
        }
    }

    private static String pickString(Map<String, Object> params, String... keys) {
        if (params == null) return null;
        for (String key : keys) {
            Object v = params.get(key);
            if (v != null && !v.toString().isBlank()) {
                return v.toString();
            }
        }
        return null;
    }
}
