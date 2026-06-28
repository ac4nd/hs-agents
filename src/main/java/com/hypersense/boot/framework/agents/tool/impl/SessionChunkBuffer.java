package com.hypersense.boot.framework.agents.tool.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session 级分块缓冲区。
 * <p>
 * LLM 调 {@code file_write_chunk} 时按 {@code mode=start|append|end} 三阶段操作：
 * <ul>
 *   <li>{@code start}：为 (sessionId, filename) 新建一个 {@link StringBuffer}</li>
 *   <li>{@code append}：向 buffer 追加 chunk</li>
 *   <li>{@code end}：取出完整内容、清理缓冲、返回给 {@link FileWriteChunkTool} 落盘</li>
 * </ul>
 * <p>
 * 单 chunk 超过 {@value #CHUNK_WARN_SIZE} 字符 → 警告日志（不阻塞写入，便于 LLM 自检拆分）。
 *
 * @author Claude
 * @since 2026/6/29
 */
@Component
public class SessionChunkBuffer {

    private static final Logger log = LoggerFactory.getLogger(SessionChunkBuffer.class);

    /** 单 chunk 字符数告警阈值（4K） */
    static final int CHUNK_WARN_SIZE = 4 * 1024;

    /** sessionId → (filename → buffer) */
    private final Map<String, Map<String, StringBuffer>> buffers = new ConcurrentHashMap<>();

    /**
     * 为 (sessionId, filename) 初始化一个新的缓冲区；若已存在则覆盖（容许 LLM 重发 start）。
     */
    public void start(String sessionId, String filename) {
        buffers.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>())
                .put(filename, new StringBuffer(8192));
    }

    /**
     * 追加 chunk 到 (sessionId, filename) 对应缓冲区。
     *
     * @throws IllegalStateException 该会话/文件尚未 start
     */
    public void append(String sessionId, String filename, String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return;
        }
        Map<String, StringBuffer> sessionBuffers = buffers.get(sessionId);
        if (sessionBuffers == null || !sessionBuffers.containsKey(filename)) {
            throw new IllegalStateException(
                    "buffer not started for " + filename + " in session " + sessionId);
        }
        if (chunk.length() > CHUNK_WARN_SIZE) {
            log.warn("file_write_chunk 单 chunk {} 字符超 {}，建议拆分",
                    chunk.length(), CHUNK_WARN_SIZE);
        }
        sessionBuffers.get(filename).append(chunk);
    }

    /**
     * 取出 (sessionId, filename) 的完整内容并清理对应缓冲；缓冲全空时一并清理 session 表项。
     *
     * @throws IllegalStateException 该会话/文件尚未 start
     */
    public String end(String sessionId, String filename) {
        Map<String, StringBuffer> sessionBuffers = buffers.get(sessionId);
        if (sessionBuffers == null || !sessionBuffers.containsKey(filename)) {
            throw new IllegalStateException(
                    "buffer not started for " + filename + " in session " + sessionId);
        }
        String content = sessionBuffers.remove(filename).toString();
        if (sessionBuffers.isEmpty()) {
            buffers.remove(sessionId);
        }
        return content;
    }

    /**
     * 测试 / 监控辅助：返回当前 (sessionId, filename) 的累计字符数；不存在返回 -1。
     */
    public int bufferSize(String sessionId, String filename) {
        Map<String, StringBuffer> sb = buffers.get(sessionId);
        return sb == null || !sb.containsKey(filename) ? -1 : sb.get(filename).length();
    }

    /**
     * 清理整个 session 的所有缓冲（session 结束/异常兜底用）。
     */
    public void clearSession(String sessionId) {
        buffers.remove(sessionId);
    }
}
