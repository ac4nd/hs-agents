package com.hypersense.boot.framework.agents.tool.impl;

import com.hypersense.boot.framework.agents.tool.ToolProvider;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 文件读取工具 — 从 Agent 状态的 files 中读取内容
 *
 * @author Claude
 * @since 2026/5/15
 */
@Component
public class FileReadTool implements ToolProvider {

    @Override
    public String name() {
        return "file_read";
    }

    @Override
    public String description() {
        return "从产物中读取指定文件的内容。参数：filename（文件名）";
    }

    @Override
    public Object execute(Map<String, Object> params) {
        String filename = (String) params.get("filename");
        if (filename == null) {
            return "错误：缺少 filename 参数";
        }
        // 实际读取由 ToolNode 从状态中获取
        return Map.of("success", true, "filename", filename, "message", "读取请求已准备");
    }
}
