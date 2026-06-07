package com.hypersense.boot.framework.agents.tool.impl;

import com.hypersense.boot.framework.agents.tool.ToolProvider;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 文件写入工具 — 将内容写入 Agent 状态的 files 中
 *
 * @author Claude
 * @since 2026/5/15
 */
@Component
public class FileWriteTool implements ToolProvider {

    @Override
    public String name() {
        return "file_write";
    }

    @Override
    public String description() {
        return "将文本内容写入指定文件名的产物中。参数：filename（文件名），content（内容）";
    }

    @Override
    public Object execute(Map<String, Object> params) {
        String filename = (String) params.get("filename");
        String content = (String) params.get("content");
        if (filename == null || content == null) {
            return "错误：缺少 filename 或 content 参数";
        }
        // 实际写入由 ToolNode 通过状态更新完成
        return Map.of("success", true, "filename", filename, "message", "内容已准备写入");
    }
}
