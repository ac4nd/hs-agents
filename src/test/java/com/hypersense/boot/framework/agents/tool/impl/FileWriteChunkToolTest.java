package com.hypersense.boot.framework.agents.tool.impl;

import com.hypersense.boot.framework.agents.sandbox.SandboxManager;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link FileWriteChunkTool} 单元测试：聚焦单次写入模式 + 状态机降级。
 *
 * <p>背景：生产环境观察到 LLM 只调用一次 file_write_chunk 但内容未落盘——
 * 原因是 LLM 没遵循 start/append/end 三阶段，仅 append 后未 end，缓冲被遗弃。
 * 新增 {@code mode=write} 单次写入模式作为兜底；append/end 也加容错。</p>
 *
 * @author Claude
 * @since 2026/6/30
 */
class FileWriteChunkToolTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> run(FileWriteChunkTool tool, Map<String, Object> in) {
        return (Map<String, Object>) tool.execute(in);
    }

    @Test
    void shouldRejectWriteModeWithEmptyChunk() {
        FileWriteChunkTool tool = newToolWithoutSandbox();
        Map<String, Object> r = run(tool, Map.of(
                "sessionId", "s1", "filename", "a.html", "mode", "write"));
        assertEquals(Boolean.FALSE, r.get("success"));
        assertTrue(((String) r.get("error")).contains("write 模式要求 chunk 非空"));
    }

    @Test
    void shouldDefaultToWriteModeWhenModeMissing() {
        FileWriteChunkTool tool = newToolWithoutSandbox();
        Map<String, Object> r = run(tool, Map.of(
                "sessionId", "s1", "filename", "a.html", "chunk", "<html/>"));
        // 无沙箱时 success=false 但 workspacePath 已填充（ToolNode 据此识别副作用）
        assertEquals(Boolean.FALSE, r.get("success"));
        assertEquals("a.html", r.get("filename"));
        assertNotNull(r.get("workspacePath"));
        assertEquals("<html/>", r.get("content"));
    }

    @Test
    void shouldRejectUnknownMode() {
        FileWriteChunkTool tool = newToolWithoutSandbox();
        Map<String, Object> r = run(tool, Map.of(
                "sessionId", "s1", "filename", "a.html", "mode", "bogus"));
        assertEquals(Boolean.FALSE, r.get("success"));
        assertTrue(((String) r.get("error")).contains("unknown mode"));
    }

    @Test
    void shouldDeriveFilenameFromTitleWhenMissing() {
        // 防御性兜底：LLM 漏传 filename 时从 <title> 推导，不再硬拒绝
        FileWriteChunkTool tool = newToolWithoutSandbox();
        Map<String, Object> r = run(tool, Map.of(
                "sessionId", "s1", "mode", "write",
                "chunk", "<html><head><title>World Cup 2026 Schedule</title></head><body>X</body></html>"));
        // filename 应从 title slugify 出来
        assertEquals("world-cup-2026-schedule.html", r.get("filename"));
    }

    @Test
    void shouldFallbackToOutputHtmlWhenNoTitleAndNoFilename() {
        FileWriteChunkTool tool = newToolWithoutSandbox();
        Map<String, Object> r = run(tool, Map.of(
                "sessionId", "s1", "mode", "write",
                "chunk", "<html><body>plain content without title</body></html>"));
        assertEquals("output.html", r.get("filename"));
    }

    @Test
    void shouldAutoPersistWhenStartCarriesContent() {
        // 防御性兜底：LLM 把完整 HTML 塞进 start 但没继续 append/end 时直接落盘
        FileWriteChunkTool tool = newToolWithoutSandbox();
        Map<String, Object> r = run(tool, Map.of(
                "sessionId", "s1", "filename", "a.html", "mode", "start",
                "chunk", "<html><head><title>X</title></head><body>full</body></html>"));
        assertEquals("a.html", r.get("filename"));
        // 无沙箱时 success=false，但 workspacePath/content 应已填充（确认走了 persist 路径）
        assertNotNull(r.get("workspacePath"));
        assertNotNull(r.get("content"));
    }

    @Test
    void shouldStartBufferWhenChunkEmpty() {
        // 正常 start 流程：chunk 为空时进入缓冲态
        FileWriteChunkTool tool = newToolWithoutSandbox();
        Map<String, Object> r = run(tool, Map.of(
                "sessionId", "s1", "filename", "a.html", "mode", "start"));
        assertEquals(Boolean.TRUE, r.get("success"));
        assertEquals("started", r.get("status"));
    }

    @Test
    void noSandboxShouldNotPopulateElapsedMs() {
        // 无沙箱场景（success=false）不应写 elapsedMs——
        // 耗时无业务意义（沙箱未真正调用 writeFile），避免误读为"耗时 0"
        FileWriteChunkTool tool = newToolWithoutSandbox();
        Map<String, Object> r = run(tool, Map.of(
                "sessionId", "s1", "filename", "a.html", "mode", "write", "chunk", "<html/>"));
        assertEquals(Boolean.FALSE, r.get("success"));
        assertNull(r.get("elapsedMs"), "无沙箱场景 elapsedMs 应缺失");
    }

    private FileWriteChunkTool newToolWithoutSandbox() {
        return new FileWriteChunkTool(new SessionChunkBuffer(), null);
    }
}
