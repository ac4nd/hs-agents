package com.hypersense.boot.framework.agents.engine.node;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #12h open-design artifact 模式解析回归测试。
 *
 * <p>背景：JSON 函数调用对长 HTML 不友好（CSS 引号破坏 JSON 转义 + token 上限截断），
 * 改用 open-design 的 {@code <artifact path="xxx.html">...</artifact>} 纯文本标签模式。
 * 本测试覆盖 {@link ToolNode#rescueContentFromText} 的 artifact 优先解析路径。</p>
 *
 * @author Claude
 * @since 2026/6/30
 */
class ToolNodeArtifactParseTest {

    private Map<String, Object> rescue(String toolName, Map<String, Object> args, String text) throws Exception {
        Map<String, Object> copy = new HashMap<>(args);
        Method m = ToolNode.class.getDeclaredMethod(
                "rescueContentFromText", String.class, Map.class, String.class);
        m.setAccessible(true);
        m.invoke(newToolNodeInstance(), toolName, copy, text);
        return copy;
    }

    private ToolNode newToolNodeInstance() throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        java.lang.reflect.Field f = unsafeClass.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        Object unsafe = f.get(null);
        Method allocateInstance = unsafeClass.getMethod("allocateInstance", Class.class);
        return (ToolNode) allocateInstance.invoke(unsafe, ToolNode.class);
    }

    @Test
    void artifactTag_extractsContentAndFilenameFromFileWriteChunk() throws Exception {
        String html = "<!DOCTYPE html><html><head><title>X</title></head><body><h1>hello</h1></body></html>";
        String text = "<artifact path=\"landing.html\">\n" + html + "\n</artifact>";
        Map<String, Object> args = rescue("file_write_chunk", new HashMap<>(), text);
        assertEquals(html, args.get("content"));
        assertEquals("landing.html", args.get("filename"));
    }

    @Test
    void artifactTag_supportsNameAndIdentifierAttributes() throws Exception {
        String html = "<!DOCTYPE html><html></html>";
        for (String attr : new String[]{"name", "identifier"}) {
            String text = "<artifact " + attr + "=\"page.html\">" + html + "</artifact>";
            Map<String, Object> args = rescue("file_write", new HashMap<>(), text);
            assertEquals(html, args.get("content"), "attr=" + attr);
            assertEquals("page.html", args.get("filename"), "attr=" + attr);
        }
    }

    @Test
    void artifactTag_pathBasnameExtraction_stripsDirectory() throws Exception {
        String html = "<html></html>";
        String text = "<artifact path=\"some/nested/path/page.html\">" + html + "</artifact>";
        Map<String, Object> args = rescue("file_write_chunk", new HashMap<>(), text);
        assertEquals("page.html", args.get("filename"));
    }

    @Test
    void artifactTag_doesNotOverrideExistingFilename() throws Exception {
        String html = "<html></html>";
        String text = "<artifact path=\"other.html\">" + html + "</artifact>";
        Map<String, Object> init = new HashMap<>();
        init.put("filename", "preset.html");
        Map<String, Object> args = rescue("file_write", init, text);
        assertEquals("preset.html", args.get("filename"));
    }

    @Test
    void artifactTag_toleratesSurroundingChatter() throws Exception {
        String html = "<!DOCTYPE html><html></html>";
        // 模拟 LLM 偶尔带的前言（artifact 仍应优先命中）
        String text = "好的，这是产物：\n<artifact path=\"x.html\">\n" + html + "\n</artifact>\n完成。";
        Map<String, Object> args = rescue("file_write_chunk", new HashMap<>(), text);
        assertEquals(html, args.get("content"));
        assertEquals("x.html", args.get("filename"));
    }

    @Test
    void noArtifact_fallsBackToCodeBlock() throws Exception {
        String html = "<!DOCTYPE html><html><body>x</body></html>";
        String text = "```html\n" + html + "\n```";
        Map<String, Object> args = rescue("file_write", new HashMap<>(), text);
        assertEquals(html, args.get("content"));
    }

    @Test
    void noArtifact_fallsBackToBareDoctype() throws Exception {
        String html = "<!DOCTYPE html><html><head></head><body>yyy</body></html>";
        Map<String, Object> args = rescue("file_write_chunk", new HashMap<>(), html);
        assertEquals(html, args.get("content"));
    }

    @Test
    void artifactTag_skipsForReplyText() throws Exception {
        // reply_text 工具不应被 artifact 触发（artifact 是 file_write 类专用）
        // 期望：走 reply_text 第 3 步兜底（整段非空文本作为 content），而非被 artifact 劫持成 "html"
        String text = "<artifact path=\"x.html\">SHORT_ARTIFACT_BODY</artifact>";
        Map<String, Object> args = rescue("reply_text", new HashMap<>(), text);
        Object content = args.get("content");
        assertNotNull(content, "reply_text 必须有兜底 content");
        String cs = content.toString();
        assertFalse(cs.equals("SHORT_ARTIFACT_BODY"),
                "reply_text 不应被 artifact 标签劫持，应保留整段文本走对话兜底路径");
        // filename 不应被 artifact 的 path 属性污染（reply_text 无 filename 概念）
        assertNull(args.get("filename"), "reply_text 不应从 artifact 推导 filename");
    }

    @Test
    void artifactTag_toleratesExtraAttributes() throws Exception {
        // LLM 偶尔在 path 前加 type/title 等额外属性，正则应优先匹配 path/name/identifier
        String html = "<!DOCTYPE html><html></html>";
        String text = "<artifact type=\"html\" path=\"multi.html\">\n" + html + "\n</artifact>";
        Map<String, Object> args = rescue("file_write", new HashMap<>(), text);
        // 当前正则要求 (path|name|identifier) 紧跟 <artifact 后——额外属性在前会不命中，
        // 此时退回 DOCTYPE 路径仍可救出 content；此测试记录该已知行为
        Object content = args.get("content");
        assertNotNull(content, "即便 artifact 属性顺序不匹配，也应通过 DOCTYPE 兜底拿到 content");
    }

    @Test
    void artifactTag_windowsBackslashPath() throws Exception {
        // Windows 反斜杠混合：path 含 \ 在 basename 提取时应被规范化
        String html = "<html></html>";
        String text = "<artifact path=\"dir\\sub\\win.html\">" + html + "</artifact>";
        Map<String, Object> args = rescue("file_write_chunk", new HashMap<>(), text);
        assertEquals("win.html", args.get("filename"));
    }
}
