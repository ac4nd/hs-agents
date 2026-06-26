package com.hypersense.boot.framework.agents.serializer;

import com.hypersense.boot.framework.agents.model.DeepAgentState;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ContentType;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import org.bsc.langgraph4j.serializer.Serializer;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.nio.charset.StandardCharsets;
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
        /** 多模态序列化版本 magic byte，用于未来扩展识别 */
        private static final byte MAGIC_V1 = 0x01;

        @Override
        public void write(UserMessage msg, ObjectOutput out) throws IOException {
            out.writeByte(MAGIC_V1);
            // name（可空）
            out.writeBoolean(msg.name() != null);
            if (msg.name() != null) {
                writeLargeUTF(msg.name(), out);
            }
            // contents
            List<Content> contents = msg.contents();
            out.writeInt(contents.size());
            for (Content c : contents) {
                writeContent(c, out);
            }
        }

        @Override
        public UserMessage read(ObjectInput in) throws IOException, ClassNotFoundException {
            byte magic = in.readByte();
            if (magic != MAGIC_V1) {
                throw new IOException("Unsupported UserMessage serialization magic: " + magic
                        + "，请清理 Redis/PostgreSQL 中的旧 checkpoint 数据后重试");
            }
            String name = in.readBoolean() ? readLargeUTF(in) : null;
            int size = in.readInt();
            List<Content> contents = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                contents.add(readContent(in));
            }
            return name == null ? UserMessage.from(contents) : UserMessage.from(name, contents);
        }

        // ---------- Content 读写 ----------

        private static void writeContent(Content c, ObjectOutput out) throws IOException {
            ContentType type = c.type();
            out.writeUTF(type.name());
            switch (type) {
                case TEXT -> writeLargeUTF(((TextContent) c).text(), out);
                case IMAGE -> writeImageContent((ImageContent) c, out);
                default -> throw new IOException("Unsupported ContentType in ObjectStream path: " + type
                        + "，仅支持 TEXT / IMAGE");
            }
        }

        private static Content readContent(ObjectInput in) throws IOException {
            String typeName = in.readUTF();
            ContentType type = ContentType.valueOf(typeName);
            switch (type) {
                case TEXT -> {
                    return TextContent.from(readLargeUTF(in));
                }
                case IMAGE -> {
                    return readImageContent(in);
                }
                default -> throw new IOException("Unsupported ContentType in ObjectStream path: " + type);
            }
        }

        private static void writeImageContent(ImageContent c, ObjectOutput out) throws IOException {
            Image img = c.image();
            String url = img != null && img.url() != null ? img.url().toString() : null;
            String base64 = img != null ? img.base64Data() : null;
            String mimeType = img != null ? img.mimeType() : null;
            String detail = c.detailLevel() != null ? c.detailLevel().name() : null;

            out.writeBoolean(url != null);
            if (url != null) writeLargeUTF(url, out);
            out.writeBoolean(base64 != null);
            if (base64 != null) writeLargeBytes(base64.getBytes(StandardCharsets.UTF_8), out);
            out.writeBoolean(mimeType != null);
            if (mimeType != null) writeLargeUTF(mimeType, out);
            out.writeBoolean(detail != null);
            if (detail != null) writeLargeUTF(detail, out);
        }

        private static ImageContent readImageContent(ObjectInput in) throws IOException {
            String url = in.readBoolean() ? readLargeUTF(in) : null;
            String base64 = in.readBoolean() ? new String(readLargeBytes(in), StandardCharsets.UTF_8) : null;
            String mimeType = in.readBoolean() ? readLargeUTF(in) : null;
            String detail = in.readBoolean() ? readLargeUTF(in) : null;

            ImageContent.DetailLevel level = detail != null
                    ? ImageContent.DetailLevel.valueOf(detail) : null;
            if (url != null) {
                return level != null
                        ? ImageContent.from(url, mimeType, level)
                        : ImageContent.from(url, mimeType);
            }
            if (base64 != null) {
                return level != null
                        ? ImageContent.from(base64, mimeType, level)
                        : ImageContent.from(base64, mimeType);
            }
            throw new IOException("Invalid ImageContent: both url and base64Data are null");
        }

        // ---------- 大字段读写（绕过 writeUTF 64KB 限制） ----------

        private static void writeLargeUTF(String s, ObjectOutput out) throws IOException {
            writeLargeBytes(s.getBytes(StandardCharsets.UTF_8), out);
        }

        private static String readLargeUTF(ObjectInput in) throws IOException {
            return new String(readLargeBytes(in), StandardCharsets.UTF_8);
        }

        private static void writeLargeBytes(byte[] bytes, ObjectOutput out) throws IOException {
            out.writeInt(bytes.length);
            out.write(bytes);
        }

        private static byte[] readLargeBytes(ObjectInput in) throws IOException {
            int len = in.readInt();
            if (len < 0) throw new IOException("Negative byte length: " + len);
            byte[] bytes = new byte[len];
            in.readFully(bytes);
            return bytes;
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
