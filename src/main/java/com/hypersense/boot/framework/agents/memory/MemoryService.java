package com.hypersense.boot.framework.agents.memory;

import com.hypersense.boot.framework.agents.config.AgentProperties;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 长期记忆核心服务
 *
 * @author Claude
 * @since 2026/5/27
 */
@Slf4j
public class MemoryService {

    private static final String FACT_EXTRACTION_PROMPT = """
            你是一个事实提取助手。从以下对话中提取关键事实，每行一个事实，格式：
            FACT: [事实内容] | CATEGORY: [preference/fact/decision/procedure]

            规则：
            - 只提取明确表达的客观事实和偏好
            - 不提取临时性/上下文相关的信息（如"你好"、"谢谢"）
            - 不提取问候、确认等无效信息
            - 每条事实独立、完整，脱离对话上下文也能理解
            - preference: 用户偏好（格式、风格、工具选择）
            - fact: 客观事实（项目信息、技术栈、API 端点）
            - decision: 重要决策（架构选择、方案取舍）
            - procedure: 操作流程（用户总结的步骤、工作方式）
            - 如果对话中没有值得提取的事实，输出：NO_FACTS

            对话内容：
            """;

    private static final String MEMORY_MARKER = "--- User Memory ---";

    private static final Set<String> VALID_CATEGORIES = Set.of(
            AgentMemory.Category.PREFERENCE, AgentMemory.Category.FACT,
            AgentMemory.Category.DECISION, AgentMemory.Category.PROCEDURE);

    private final MemoryRepository repository;
    private final AliyunEmbeddingClient embeddingClient;
    private final ChatModel chatModel;
    private final AgentProperties.MemoryConfig config;

    public MemoryService(MemoryRepository repository, AliyunEmbeddingClient embeddingClient,
                         ChatModel chatModel, AgentProperties.MemoryConfig config) {
        this.repository = repository;
        this.embeddingClient = embeddingClient;
        this.chatModel = chatModel;
        this.config = config;
    }

    /**
     * 从对话中提取事实并批量存储
     */
    public void extractAndStore(Long userId, Long tenantId, String sessionId, List<ChatMessage> messages) {
        if (messages == null || messages.size() < config.getMinMessagesForExtraction()) {
            log.debug("MemoryService: 消息数({})不足提取阈值({}), 跳过",
                    messages != null ? messages.size() : 0, config.getMinMessagesForExtraction());
            return;
        }

        try {
            String conversationText = formatConversation(messages);
            String extractionResult = extractFacts(conversationText);
            if (extractionResult == null || extractionResult.contains("NO_FACTS") || extractionResult.isBlank()) {
                return;
            }

            List<ExtractedFact> facts = parseFacts(extractionResult);
            if (facts.isEmpty()) {
                return;
            }

            // 批量向量化
            List<String> contents = facts.stream().map(f -> f.content).toList();
            List<float[]> embeddings = batchEmbed(contents);

            // 批量存储
            for (int i = 0; i < facts.size(); i++) {
                try {
                    AgentMemory memory = AgentMemory.builder()
                            .tenantId(tenantId)
                            .userId(userId)
                            .content(facts.get(i).content)
                            .category(facts.get(i).category)
                            .embedding(embeddings != null && i < embeddings.size() ? embeddings.get(i) : null)
                            .sessionId(sessionId)
                            .build();
                    repository.store(memory);
                } catch (Exception e) {
                    log.warn("MemoryService: 存储记忆失败: {}", e.getMessage());
                }
            }
            log.info("MemoryService: 从会话 {} 提取并存储 {} 条记忆", sessionId, facts.size());
        } catch (Exception e) {
            log.error("MemoryService: 事实提取失败", e);
        }
    }

    /**
     * 检索相关记忆（向量 + 关键词双信号）
     */
    public List<AgentMemory> retrieve(Long userId, Long tenantId, String query, int limit) {
        List<AgentMemory> results = new ArrayList<>();
        try {
            float[] queryEmbedding = embed(query);
            if (queryEmbedding != null) {
                List<AgentMemory> vectorResults = repository.search(tenantId, userId, queryEmbedding,
                        limit, config.getSimilarityThreshold());
                results.addAll(vectorResults);
            }

            List<AgentMemory> keywordResults = repository.searchByKeyword(tenantId, userId, query,
                    Math.max(3, limit / 3));

            Set<Long> seen = new HashSet<>(results.stream().map(AgentMemory::getId).toList());
            for (AgentMemory km : keywordResults) {
                if (seen.add(km.getId())) {
                    results.add(km);
                }
            }

            if (results.size() > limit) {
                results = results.subList(0, limit);
            }

            // 批量更新访问计数（避免 N+1）
            List<Long> ids = results.stream().map(AgentMemory::getId).toList();
            repository.batchIncrementAccessCount(ids);

        } catch (Exception e) {
            log.error("MemoryService: 记忆检索失败", e);
        }
        return results;
    }

    public String formatMemoryContext(List<AgentMemory> memories) {
        if (memories == null || memories.isEmpty()) {
            return "";
        }
        return memories.stream()
                .map(m -> String.format("[%s] %s", categoryLabel(m.getCategory()), m.getContent()))
                .collect(Collectors.joining("\n"));
    }

    public String getMemoryMarker() {
        return MEMORY_MARKER;
    }

    public int getMaxRetrievalCount() {
        return config.getMaxRetrievalCount();
    }

    // ========== 内部方法 ==========

    /**
     * 批量向量化（单次 API 调用）
     */
    private List<float[]> batchEmbed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return null;
        }
        try {
            List<float[]> result = embeddingClient.batchEmbed(texts);
            return result.isEmpty() ? null : result;
        } catch (Exception e) {
            log.error("MemoryService: 批量 Embedding 异常", e);
            return null;
        }
    }

    private float[] embed(String text) {
        List<float[]> results = batchEmbed(List.of(text));
        return (results != null && !results.isEmpty()) ? results.get(0) : null;
    }

    private String extractFacts(String conversationText) {
        try {
            // 注意：百炼 qwen-flash 等模型要求 messages 至少含一条 user 消息，
            // 仅传 SystemMessage 会触发 400 / code=1214 非法 messages 错误。
            ChatResponse response = chatModel.chat(
                    SystemMessage.from(FACT_EXTRACTION_PROMPT),
                    UserMessage.from("请从以下对话中提取可长期保存的用户事实：\n\n" + conversationText)
            );
            return response.aiMessage().text();
        } catch (Exception e) {
            log.error("MemoryService: LLM 事实提取失败", e);
            return null;
        }
    }

    private String formatConversation(List<ChatMessage> messages) {
        StringBuilder sb = new StringBuilder();
        // 注意：用 Object 遍历避免 for-each 在循环开始时强转为 ChatMessage 导致 ClassCastException。
        // 实际元素可能是 LinkedHashMap（checkpoint 反序列化降级），由下方 instanceof 分支处理。
        for (Object obj : messages) {
            String role;
            String text;
            if (obj instanceof UserMessage um) {
                role = "User";
                text = um.singleText();
            } else if (obj instanceof SystemMessage sm) {
                role = "System";
                text = sm.text();
            } else if (obj instanceof AiMessage ai) {
                role = "Assistant";
                text = ai.text();
            } else if (obj instanceof Map<?, ?> map) {
                // Jackson 反序列化降级：按 LangChain4j 序列化结构提取
                role = mapToRole(map);
                text = mapToText(map);
            } else {
                role = "Unknown";
                text = obj != null ? obj.toString() : "";
            }
            sb.append(role).append(": ").append(text != null ? text : "").append("\n");
        }
        return sb.toString();
    }

    /**
     * 从 Jackson 反序列化后的 LinkedHashMap 中提取角色名。
     * LangChain4j 序列化结构：{"type":"userMessage"/"aiMessage"/"systemMessage", ...} 或 {"role":"user", ...}
     */
    private String mapToRole(Map<?, ?> map) {
        Object type = map.get("type");
        if (type != null) {
            String s = type.toString().toLowerCase();
            if (s.contains("user")) return "User";
            if (s.contains("ai") || s.contains("assistant")) return "Assistant";
            if (s.contains("system")) return "System";
        }
        Object role = map.get("role");
        if (role != null) return role.toString();
        return "Unknown";
    }

    /**
     * 从 Jackson 反序列化后的 LinkedHashMap 中提取文本内容。
     * 兼容字段：text / contents / content / singleText。
     */
    @SuppressWarnings("unchecked")
    private String mapToText(Map<?, ?> map) {
        for (String key : new String[]{"text", "contents", "content"}) {
            Object v = map.get(key);
            if (v instanceof CharSequence cs) return cs.toString();
            if (v instanceof List<?> list && !list.isEmpty()) {
                // contents 可能是 [{text: "..."}] 结构
                Object first = list.get(0);
                if (first instanceof Map<?, ?> m) {
                    Object t = m.get("text");
                    if (t != null) return t.toString();
                }
                return first.toString();
            }
        }
        return "";
    }

    private List<ExtractedFact> parseFacts(String extractionResult) {
        List<ExtractedFact> facts = new ArrayList<>();
        for (String line : extractionResult.split("\n")) {
            line = line.trim();
            if (!line.startsWith("FACT:")) {
                continue;
            }
            String content = line;
            String category = AgentMemory.Category.FACT;

            int categoryIdx = line.indexOf("| CATEGORY:");
            if (categoryIdx > 0) {
                content = line.substring("FACT:".length(), categoryIdx).trim();
                String catStr = line.substring(categoryIdx + "| CATEGORY:".length()).trim().toLowerCase();
                if (VALID_CATEGORIES.contains(catStr)) {
                    category = catStr;
                }
            } else {
                content = line.substring("FACT:".length()).trim();
            }

            if (!content.isBlank()) {
                facts.add(new ExtractedFact(content, category));
            }
        }
        return facts;
    }

    private String categoryLabel(String category) {
        if (category == null) category = AgentMemory.Category.FACT;
        return switch (category) {
            case AgentMemory.Category.PREFERENCE -> "偏好";
            case AgentMemory.Category.DECISION -> "决策";
            case AgentMemory.Category.PROCEDURE -> "流程";
            default -> "事实";
        };
    }

    private record ExtractedFact(String content, String category) {}
}
