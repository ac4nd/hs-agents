package com.hypersense.boot.framework.agents.tool.impl;

import com.hypersense.boot.framework.agents.tool.ToolProvider;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import org.springframework.stereotype.Component;

import java.util.List;
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
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name("file_read")
                .description("读取沙箱工作目录下指定文件的内容（文本/代码/CSV/JSON 等）")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("path", "要读取的文件相对路径（如 uploads/data.csv、output/result.txt）")
                        .required(List.of("path"))
                        .build())
                .build();
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
