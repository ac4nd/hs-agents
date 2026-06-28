package com.hypersense.boot.framework.agents.tool.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link SessionChunkBuffer} 单元测试：start/append/end 序列、跨会话隔离、非法状态异常。
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
    void shouldThrowWhenAppendWithoutStart() {
        SessionChunkBuffer buf = new SessionChunkBuffer();
        assertThrows(IllegalStateException.class, () -> buf.append("s1", "x.html", "data"));
    }

    @Test
    void shouldThrowWhenEndWithoutStart() {
        SessionChunkBuffer buf = new SessionChunkBuffer();
        assertThrows(IllegalStateException.class, () -> buf.end("s1", "x.html"));
    }
}
