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
     * <p>容错：若未显式 start，自动初始化缓冲区（防 LLM 漏调 start 导致内容丢失）。</p>
     */
    public void append(String sessionId, String filename, String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return;
        }
        Map<String, StringBuffer> sessionBuffers = buffers.get(sessionId);
        StringBuffer sb = sessionBuffers == null ? null : sessionBuffers.get(filename);
        if (sb == null) {
            // 容错自动 start：LLM 漏调 start 时不再丢内容
            log.warn("file_write_chunk: append 前 buffer 未 start，自动初始化 sessionId={}, file={}",
                    sessionId, filename);
            start(sessionId, filename);
            sb = buffers.get(sessionId).get(filename);
        }
        if (chunk.length() > CHUNK_WARN_SIZE) {
            log.warn("file_write_chunk 单 chunk {} 字符超 {}，建议拆分",
                    chunk.length(), CHUNK_WARN_SIZE);
        }
        sb.append(chunk);
    }

    /**
     * 取出 (sessionId, filename) 的完整内容并清理对应缓冲；缓冲全空时一并清理 session 表项。
     * <p>容错：未 start 时返回 null（不抛异常），由调用方决定降级策略。</p>
     */
    public String end(String sessionId, String filename) {
        Map<String, StringBuffer> sessionBuffers = buffers.get(sessionId);
        StringBuffer sb = sessionBuffers == null ? null : sessionBuffers.remove(filename);
        if (sb == null) {
            log.warn("file_write_chunk: end 时 buffer 未 start，返回 null sessionId={}, file={}",
                    sessionId, filename);
            return null;
        }
        if (sessionBuffers.isEmpty()) {
            buffers.remove(sessionId);
        }
        return sb.toString();
    }

    /**
     * 测试 / 监控辅助：返回当前 (sessionId, filename) 的累计字符数；不存在返回 -1。
     */
    public int bufferSize(String sessionId, String filename) {
        Map<String, StringBuffer> sessionBuffers = buffers.get(sessionId);
        StringBuffer sb = sessionBuffers == null ? null : sessionBuffers.get(filename);
        return sb == null ? -1 : sb.length();
    }

    /**
     * 清理整个 session 的所有缓冲（session 结束/异常兜底用）。
     */
    public void clearSession(String sessionId) {
        buffers.remove(sessionId);
    }
}
