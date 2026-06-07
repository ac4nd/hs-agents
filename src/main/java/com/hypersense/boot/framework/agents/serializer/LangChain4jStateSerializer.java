package com.hypersense.boot.framework.agents.serializer;

import com.hypersense.boot.framework.agents.model.DeepAgentState;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.bsc.langgraph4j.serializer.Serializer;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.List;

/**
 * 兼容 langchain4j 1.0.0 的状态序列化器工厂
 * <p>
 * langgraph4j 1.8.16 内置的 LC4jStateSerializer 调用了 AiMessage.thinking()，
 * 但该方法在 langchain4j 1.0.0 中不存在。本类提供手动注册的序列化器，
 * 仅使用 langchain4j 1.0.0 已有的 API。
 * </p>
 *
 * @author Claude
 * @since 2026/5/20
 */
public final class LangChain4jStateSerializer {

    private LangChain4jStateSerializer() {}

    /**
     * 创建兼容 langchain4j 1.0.0 的 ObjectStreamStateSerializer
     */
    public static ObjectStreamStateSerializer<DeepAgentState> create() {
        var serializer = new ObjectStreamStateSerializer<>(DeepAgentState::new);
        var mapper = serializer.mapper();
        mapper.register(UserMessage.class, new UserMessageSerializer());
        mapper.register(AiMessage.class, new AiMessageSerializer());
        mapper.register(SystemMessage.class, new SystemMessageSerializer());
        mapper.register(ToolExecutionRequest.class, new ToolExecutionRequestSerializer());
        return serializer;
    }

    // ========== 内部序列化器实现 ==========

    static class UserMessageSerializer implements Serializer<UserMessage> {
        @Override
        public void write(UserMessage msg, ObjectOutput out) throws IOException {
            out.writeUTF(msg.singleText());
        }

        @Override
        public UserMessage read(ObjectInput in) throws IOException, ClassNotFoundException {
            return UserMessage.from(in.readUTF());
        }
    }

    static class SystemMessageSerializer implements Serializer<SystemMessage> {
        @Override
        public void write(SystemMessage msg, ObjectOutput out) throws IOException {
            out.writeUTF(msg.text());
        }

        @Override
        public SystemMessage read(ObjectInput in) throws IOException, ClassNotFoundException {
            return SystemMessage.from(in.readUTF());
        }
    }

    static class AiMessageSerializer implements Serializer<AiMessage> {
        @Override
        public void write(AiMessage msg, ObjectOutput out) throws IOException {
            // text（可能为 null）
            String text = msg.text();
            out.writeBoolean(text != null);
            if (text != null) {
                out.writeUTF(text);
            }

            // toolExecutionRequests
            List<ToolExecutionRequest> requests = msg.toolExecutionRequests();
            out.writeBoolean(requests != null);
            if (requests != null) {
                out.writeInt(requests.size());
                for (ToolExecutionRequest req : requests) {
                    writeToolExecutionRequest(req, out);
                }
            }
        }

        @Override
        public AiMessage read(ObjectInput in) throws IOException, ClassNotFoundException {
            String text = null;
            if (in.readBoolean()) {
                text = in.readUTF();
            }

            List<ToolExecutionRequest> requests = null;
            if (in.readBoolean()) {
                int size = in.readInt();
                requests = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    requests.add(readToolExecutionRequest(in));
                }
            }

            if (text != null && requests != null && !requests.isEmpty()) {
                return new AiMessage(text, requests);
            } else if (requests != null && !requests.isEmpty()) {
                return new AiMessage(requests);
            } else if (text != null) {
                return AiMessage.from(text);
            } else {
                return AiMessage.from("");
            }
        }
    }

    static class ToolExecutionRequestSerializer implements Serializer<ToolExecutionRequest> {
        @Override
        public void write(ToolExecutionRequest req, ObjectOutput out) throws IOException {
            writeToolExecutionRequest(req, out);
        }

        @Override
        public ToolExecutionRequest read(ObjectInput in) throws IOException, ClassNotFoundException {
            return readToolExecutionRequest(in);
        }
    }

    // ========== 共用读写方法 ==========

    private static void writeToolExecutionRequest(ToolExecutionRequest req, ObjectOutput out) throws IOException {
        out.writeUTF(req.id() != null ? req.id() : "");
        out.writeUTF(req.name() != null ? req.name() : "");
        out.writeUTF(req.arguments() != null ? req.arguments() : "");
    }

    private static ToolExecutionRequest readToolExecutionRequest(ObjectInput in) throws IOException {
        String id = in.readUTF();
        String name = in.readUTF();
        String arguments = in.readUTF();
        return ToolExecutionRequest.builder()
                .id(id.isEmpty() ? null : id)
                .name(name.isEmpty() ? null : name)
                .arguments(arguments.isEmpty() ? null : arguments)
                .build();
    }
}
