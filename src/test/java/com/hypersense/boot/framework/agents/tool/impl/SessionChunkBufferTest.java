package com.hypersense.boot.framework.agents.tool.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link SessionChunkBuffer} 单元测试：start/append/end 序列、跨会话隔离、容错降级。
 *
 * @author Claude
 * @since 2026/6/29
 */
class SessionChunkBufferTest {

    @Test
    void shouldBufferStartAppendEndSequence() {
        SessionChunkBuffer buf = new SessionChunkBuffer();
        buf.start("s1", "out.html");
        buf.append("s1", "out.html", "<html>");
        buf.append("s1", "out.html", "<body>hello</body>");
        buf.append("s1", "out.html", "</html>");
        String result = buf.end("s1", "out.html");
        assertEquals("<html><body>hello</body></html>", result);
        assertEquals(-1, buf.bufferSize("s1", "out.html"));
    }

    @Test
    void shouldIsolateBetweenSessions() {
        SessionChunkBuffer buf = new SessionChunkBuffer();
        buf.start("s1", "a.html");
        buf.start("s2", "a.html");
        buf.append("s1", "a.html", "AAA");
        buf.append("s2", "a.html", "BBB");
        assertEquals("AAA", buf.end("s1", "a.html"));
        assertEquals("BBB", buf.end("s2", "a.html"));
    }

    @Test
    void shouldAutoStartOnAppendWithoutStart() {
        // 容错：LLM 漏调 start 时 append 自动初始化缓冲区，不丢内容
        SessionChunkBuffer buf = new SessionChunkBuffer();
        buf.append("s1", "x.html", "data");
        assertEquals(4, buf.bufferSize("s1", "x.html"));
        assertEquals("data", buf.end("s1", "x.html"));
    }

    @Test
    void shouldReturnNullWhenEndWithoutStart() {
        // 容错：未 start 直接 end 不再抛异常，返回 null 由调用方降级
        SessionChunkBuffer buf = new SessionChunkBuffer();
        assertNull(buf.end("s1", "x.html"));
    }
}
