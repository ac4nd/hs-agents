package com.hypersense.boot.framework.agents.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * LangChain4j 消息类型的 Jackson 序列化模块
 * <p>
 * 用于 {@code PostgresqlSaver} 通过 Jackson 将 Agent state Map 序列化为 JSONB。
 * 与 {@link LangChain4jStateSerializer}（ObjectStream 路径）保持字段语义一致：
 * <ul>
 *   <li>UserMessage / SystemMessage：仅保留文本</li>
 *   <li>AiMessage：保留 text 与 toolExecutionRequests</li>
 *   <li>ToolExecutionRequest：保留 id / name / arguments</li>
 * </ul>
 * </p>
 *
 * @author Claude
 * @since 2026/6/17
 */
@Slf4j
public class LangChain4jJacksonModule extends SimpleModule {

    public LangChain4jJacksonModule() {
        super("langchain4j");
        addSerializer(UserMessage.class, new UserMessageSerializer());
        addDeserializer(UserMessage.class, new UserMessageDeserializer());
        addSerializer(SystemMessage.class, new SystemMessageSerializer());
        addDeserializer(SystemMessage.class, new SystemMessageDeserializer());
        addSerializer(AiMessage.class, new AiMessageSerializer());
        addDeserializer(AiMessage.class, new AiMessageDeserializer());
        addSerializer(ToolExecutionRequest.class, new ToolExecutionRequestSerializer());
        addDeserializer(ToolExecutionRequest.class, new ToolExecutionRequestDeserializer());
    }

    // ========== UserMessage ==========

    static class UserMessageSerializer extends JsonSerializer<UserMessage> {
        @Override
        public void serialize(UserMessage value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeStartObject();
            gen.writeStringField("type", "USER");
            gen.writeStringField("text", value.singleText());
            gen.writeEndObject();
        }
    }

    static class UserMessageDeserializer extends JsonDeserializer<UserMessage> {
        @Override
        public UserMessage deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readTree(p);
            String text = node.hasNonNull("text") ? node.get("text").asText() : "";
            return UserMessage.from(text);
        }
    }

    // ========== SystemMessage ==========

    static class SystemMessageSerializer extends JsonSerializer<SystemMessage> {
        @Override
        public void serialize(SystemMessage value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeStartObject();
            gen.writeStringField("type", "SYSTEM");
            gen.writeStringField("text", value.text());
            gen.writeEndObject();
        }
    }

    static class SystemMessageDeserializer extends JsonDeserializer<SystemMessage> {
        @Override
        public SystemMessage deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readTree(p);
            String text = node.hasNonNull("text") ? node.get("text").asText() : "";
            return SystemMessage.from(text);
        }
    }

    // ========== AiMessage ==========

    static class AiMessageSerializer extends JsonSerializer<AiMessage> {
        @Override
        public void serialize(AiMessage value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeStartObject();
            gen.writeStringField("type", "AI");
            String text = value.text();
            if (text != null) {
                gen.writeStringField("text", text);
            } else {
                gen.writeNullField("text");
            }
            List<ToolExecutionRequest> requests = value.toolExecutionRequests();
            if (requests != null) {
                gen.writeArrayFieldStart("toolExecutionRequests");
                for (ToolExecutionRequest req : requests) {
                    gen.writeStartObject();
                    gen.writeStringField("id", req.id() != null ? req.id() : "");
                    gen.writeStringField("name", req.name() != null ? req.name() : "");
                    gen.writeStringField("arguments", req.arguments() != null ? req.arguments() : "");
                    gen.writeEndObject();
                }
                gen.writeEndArray();
            }
            gen.writeEndObject();
        }
    }

    static class AiMessageDeserializer extends JsonDeserializer<AiMessage> {
        @Override
        public AiMessage deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readTree(p);
            String text = node.hasNonNull("text") ? node.get("text").asText() : null;

            List<ToolExecutionRequest> requests = null;
            if (node.hasNonNull("toolExecutionRequests")) {
                JsonNode arr = node.get("toolExecutionRequests");
                requests = new ArrayList<>(arr.size());
                for (JsonNode item : arr) {
                    requests.add(ToolExecutionRequest.builder()
                            .id(asNullable(item, "id"))
                            .name(asNullable(item, "name"))
                            .arguments(asNullable(item, "arguments"))
                            .build());
                }
            }

            if (text != null && requests != null && !requests.isEmpty()) {
                return new AiMessage(text, requests);
            } else if (requests != null && !requests.isEmpty()) {
                return new AiMessage(requests);
            } else if (text != null) {
                return AiMessage.from(text);
            }
            return AiMessage.from("");
        }

        private static String asNullable(JsonNode parent, String field) {
            if (!parent.hasNonNull(field)) return null;
            String v = parent.get(field).asText();
            return v == null || v.isEmpty() ? null : v;
        }
    }

    // ========== ToolExecutionRequest ==========

    static class ToolExecutionRequestSerializer extends JsonSerializer<ToolExecutionRequest> {
        @Override
        public void serialize(ToolExecutionRequest value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeStartObject();
            gen.writeStringField("id", value.id() != null ? value.id() : "");
            gen.writeStringField("name", value.name() != null ? value.name() : "");
            gen.writeStringField("arguments", value.arguments() != null ? value.arguments() : "");
            gen.writeEndObject();
        }
    }

    static class ToolExecutionRequestDeserializer extends JsonDeserializer<ToolExecutionRequest> {
        @Override
        public ToolExecutionRequest deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readTree(p);
            return ToolExecutionRequest.builder()
                    .id(asNullable(node, "id"))
                    .name(asNullable(node, "name"))
                    .arguments(asNullable(node, "arguments"))
                    .build();
        }

        private static String asNullable(JsonNode parent, String field) {
            if (!parent.hasNonNull(field)) return null;
            String v = parent.get(field).asText();
            return v == null || v.isEmpty() ? null : v;
        }
    }
}
