package com.hypersense.boot.framework.agents.tool.impl;

import com.hypersense.boot.framework.agents.config.AgentProperties;
import com.hypersense.boot.framework.agents.sandbox.Sandbox;
import com.hypersense.boot.framework.agents.sandbox.SandboxManager;
import com.hypersense.boot.framework.agents.sandbox.SandboxResult;
import com.hypersense.boot.framework.agents.tool.ToolProvider;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 沙箱工具（改名自 CodeExecuteTool）
 * <p>
 * 当 Agent 需要执行代码、读写文件、查看系统资源时调用。
 * 通过 {@link SandboxManager} 获取当前会话的隔离沙箱实例（Thread-scoped）。
 * </p>
 *
 * <h3>参数格式：</h3>
 * <pre>
 * {
 *   "sessionId": "abc123",          // 必填：会话 ID（由 ToolNode 自动注入）
 *   "action": "execute_code",       // 必填：操作类型
 *   "language": "python",           // execute_code 时必填
 *   "code": "print('hello')",       // execute_code 时必填
 *   "path": "/workspace/file.py",   // 文件操作时必填
 *   "content": "...",               // write_file 时必填
 *   "command": "ls -la",           // run_command 时必填
 *   "oldString": "old text",       // edit_file 时使用（文本替换模式）
 *   "newString": "new text",       // edit_file 时使用（文本替换模式）
 *   "startLine": 5,                // edit_file 时使用（行级替换模式，1-based）
 *   "endLine": 10,                 // edit_file 时使用（行级替换模式）
 *   "newContent": "...",           // edit_file 时使用（行级替换模式，替换内容）
 *   "timeout": 60                   // 可选，超时秒数
 * }
 * </pre>
 *
 * <h3>action 取值：</h3>
 * <ul>
 *   <li>execute_code   - 执行代码</li>
 *   <li>read_file      - 读取文件</li>
 *   <li>write_file     - 写入文件</li>
 *   <li>edit_file      - 编辑文件（文本替换或行级替换）</li>
 *   <li>list_dir       - 列出目录</li>
 *   <li>glob           - 文件名模式搜索（递归）</li>
 *   <li>grep           - 文件内容正则搜索（递归）</li>
 *   <li>run_command    - 执行 Shell 命令</li>
 * </ul>
 *
 * @author Claude
 * @since 2026/5/19
 */
@Slf4j
public class SandboxTool implements ToolProvider {

    private final AgentProperties.SandboxConfig config;
    private final SandboxManager sandboxManager;

    /**
     * Spring 容器构造器（通过 AgentProperties 注入配置）
     */
    public SandboxTool(AgentProperties agentProperties, SandboxManager sandboxManager) {
        this.config = agentProperties.getTools().getSandbox();
        this.sandboxManager = sandboxManager;
    }

    /**
     * 便捷构造器（直接启用沙箱，适用于 Builder 模式）
     *
     * @param sandboxManager 沙箱管理器
     */
    public SandboxTool(SandboxManager sandboxManager) {
        this.config = new AgentProperties.SandboxConfig();
        this.config.setEnabled(true);
        this.sandboxManager = sandboxManager;
    }

    @Override
    public String name() {
        return "sandbox";
    }

    @Override
    public String description() {
        return "沙箱工具：在隔离环境中执行代码、读写文件、编辑文件、搜索、运行命令。" +
                "参数：action（execute_code/read_file/write_file/edit_file/list_dir/glob/grep/run_command），" +
                "以及对应操作所需的 language/code/path/content/oldString/newString/startLine/endLine/newContent/pattern/includePattern/command 等参数。";
    }

    @Override
    public Object execute(Map<String, Object> params) {
        // 启用检查
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            return errorResult("沙箱工具未启用，请设置 agent.tools.sandbox.enabled=true");
        }

        // 提取 sessionId
        String sessionId = toString(params.get("sessionId"));
        if (sessionId == null || sessionId.isBlank()) {
            return errorResult("缺少 sessionId 参数，无法定位沙箱实例");
        }

        // 获取该会话的沙箱
        Sandbox sandbox;
        try {
            sandbox = sandboxManager.getOrCreate(sessionId);
        } catch (Exception e) {
            return errorResult("沙箱创建失败: " + e.getMessage());
        }

        // 解析 action，默认 execute_code（向后兼容）
        String action = toString(params.get("action"));
        if (action == null || action.isBlank()) {
            action = "execute_code";
        }

        log.info("SandboxTool: sessionId=[{}], action=[{}], sandbox=[{}]",
                sessionId, action, sandbox.type());

        return switch (action) {
            case "execute_code" -> handleExecuteCode(params, sandbox);
            case "read_file"    -> handleReadFile(params, sandbox);
            case "write_file"   -> handleWriteFile(params, sandbox);
            case "edit_file"    -> handleEditFile(params, sandbox);
            case "list_dir"     -> handleListDir(params, sandbox);
            case "glob"         -> handleGlob(params, sandbox);
            case "grep"         -> handleGrep(params, sandbox);
            case "run_command"  -> handleRunCommand(params, sandbox);
            default -> errorResult("未知的操作类型: " + action + "，支持: execute_code/read_file/write_file/edit_file/list_dir/glob/grep/run_command");
        };
    }

    // ========== Action 处理 ==========

    private Object handleExecuteCode(Map<String, Object> params, Sandbox sandbox) {
        String language = toString(params.get("language"));
        String code = extractCode(params);
        Integer timeout = toInteger(params.get("timeout"));

        if (language == null || language.isBlank()) {
            language = "python";
            log.debug("SandboxTool: language 未指定，默认使用 python");
        }
        if (code == null || code.isBlank()) {
            return errorResult("缺少 code 参数");
        }

        SandboxResult result = sandbox.executeCode(language, code, timeout);
        return toMap(result);
    }

    private Object handleReadFile(Map<String, Object> params, Sandbox sandbox) {
        String path = toString(params.get("path"));
        if (path == null || path.isBlank()) {
            return errorResult("缺少 path 参数");
        }

        SandboxResult result = sandbox.readFile(path);
        return toMap(result);
    }

    private Object handleWriteFile(Map<String, Object> params, Sandbox sandbox) {
        String path = toString(params.get("path"));
        String content = toString(params.get("content"));
        if (path == null || path.isBlank()) {
            return errorResult("缺少 path 参数");
        }

        SandboxResult result = sandbox.writeFile(path, content);
        return toMap(result);
    }

    private Object handleEditFile(Map<String, Object> params, Sandbox sandbox) {
        String path = toString(params.get("path"));
        if (path == null || path.isBlank()) {
            return errorResult("缺少 path 参数");
        }

        String oldString = toString(params.get("oldString"));
        String newString = toString(params.get("newString"));
        Integer startLine = toInteger(params.get("startLine"));
        Integer endLine = toInteger(params.get("endLine"));
        String newContent = toString(params.get("newContent"));

        // 模式判断
        boolean textMode = oldString != null && !oldString.isBlank();
        boolean lineMode = startLine != null && endLine != null;

        if (!textMode && !lineMode) {
            return errorResult("edit_file 需要指定参数：文本替换模式用 oldString+newString，行级替换模式用 startLine+endLine+newContent");
        }
        if (!textMode && startLine != null && endLine == null) {
            return errorResult("行级替换模式缺少 endLine 参数");
        }

        SandboxResult result = sandbox.editFile(path, oldString, newString, startLine, endLine, newContent);
        return toMap(result);
    }

    private Object handleListDir(Map<String, Object> params, Sandbox sandbox) {
        String path = toString(params.get("path"));
        if (path == null || path.isBlank()) {
            path = ".";
        }

        SandboxResult result = sandbox.listDirectory(path);
        return toMap(result);
    }

    private Object handleGlob(Map<String, Object> params, Sandbox sandbox) {
        String path = toString(params.get("path"));
        if (path == null || path.isBlank()) {
            path = ".";
        }
        String pattern = toString(params.get("pattern"));
        if (pattern == null || pattern.isBlank()) {
            return errorResult("glob 操作缺少 pattern 参数（如 **/*.py, *.json）");
        }

        SandboxResult result = sandbox.searchFiles(path, pattern);
        return toMap(result);
    }

    private Object handleGrep(Map<String, Object> params, Sandbox sandbox) {
        String path = toString(params.get("path"));
        if (path == null || path.isBlank()) {
            path = ".";
        }
        String pattern = toString(params.get("pattern"));
        if (pattern == null || pattern.isBlank()) {
            return errorResult("grep 操作缺少 pattern 参数（正则表达式）");
        }
        String includePattern = toString(params.get("includePattern"));

        SandboxResult result = sandbox.searchContent(path, pattern, includePattern);
        return toMap(result);
    }

    private Object handleRunCommand(Map<String, Object> params, Sandbox sandbox) {
        String command = toString(params.get("command"));
        if (command == null || command.isBlank()) {
            return errorResult("缺少 command 参数");
        }

        SandboxResult result = sandbox.executeCommand(command);
        return toMap(result);
    }

    // ========== 参数提取 ==========

    /**
     * 从参数中提取代码（兼容多种字段名）
     */
    private String extractCode(Map<String, Object> params) {
        String code = toString(params.get("code"));
        if (code != null && !code.isBlank()) {
            return code;
        }
        String source = toString(params.get("source_code"));
        if (source != null && !source.isBlank()) {
            return source;
        }
        // 兼容 ToolNode 传入的 todo_description
        String todoDesc = toString(params.get("todo_description"));
        return todoDesc != null ? todoDesc : null;
    }

    // ========== 结果转换 ==========

    /**
     * 将 SandboxResult 转为 Map 返回给 ToolProvider 调用方
     */
    private Map<String, Object> toMap(SandboxResult result) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("success", result.isSuccess());
        map.put("sandboxType", result.getSandboxType());

        if (result.getExitCode() != null) {
            map.put("exitCode", result.getExitCode());
        }
        if (result.getStdout() != null && !result.getStdout().isBlank()) {
            map.put("stdout", result.getStdout());
        }
        if (result.getStderr() != null && !result.getStderr().isBlank()) {
            map.put("stderr", result.getStderr());
        }
        if (result.getError() != null && !result.getError().isBlank()) {
            map.put("error", result.getError());
        }
        if (result.getElapsedMs() != null) {
            map.put("elapsedMs", result.getElapsedMs());
        }
        if (result.getContent() != null) {
            map.put("content", result.getContent());
        }

        return map;
    }

    private Map<String, Object> errorResult(String message) {
        return Map.of("success", false, "error", message);
    }

    // ========== 工具方法 ==========

    private String toString(Object obj) {
        // 注意：不使用 trim()，保留代码原始缩进（Python 等语言依赖缩进）
        return obj != null ? obj.toString() : null;
    }

    private Integer toInteger(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number) return ((Number) obj).intValue();
        try {
            return Integer.parseInt(obj.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
