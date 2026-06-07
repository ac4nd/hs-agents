package com.hypersense.boot.framework.agents;

import com.hypersense.boot.framework.agents.config.AgentProperties;
import com.hypersense.boot.framework.agents.sandbox.SandboxManager;
import com.hypersense.boot.framework.agents.sandbox.factory.DockerSandboxFactory;
import com.hypersense.boot.framework.agents.tool.impl.SandboxTool;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Agent 交互式沙箱聊天测试
 * <p>
 * 控制台输入指令，LLM（GLM-4.7）理解指令并生成沙箱操作，
 * SandboxTool 在 Docker 容器中执行操作并返回结果。
 * 同一会话复用同一个沙箱容器，支持跨消息文件操作。
 * </p>
 *
 * <h3>运行方式：</h3>
 * <pre>
 * mvn compile test-compile -q
 * mvn exec:java \
 *   -Dexec.mainClass="com.hypersense.boot.framework.agents.AgentInteractiveChatTest" \
 *   -Dexec.classpathScope=test -q
 * </pre>
 *
 * <h3>交互命令示例：</h3>
 * <ul>
 *   <li>用Python打印当前时间</li>
 *   <li>执行 ls -la 查看工作目录</li>
 *   <li>写一个文件 hello.txt 内容是 Hello World</li>
 *   <li>读取 hello.txt</li>
 *   <li>用Python计算斐波那契数列前10项</li>
 *   <li>查看系统信息（uname -a）</li>
 * </ul>
 *
 * @author Claude
 * @since 2026/5/21
 */
public class AgentInteractiveChatTest {

    // ========== Docker 沙箱配置 ==========
    static final String DOCKER_HOST = "tcp://47.107.160.31:2375";
    static final String SANDBOX_IMAGE = "godlikeagents/sandbox:1.0.0";

    // ========== LLM 配置 ==========
    static final String LLM_ENDPOINT = "https://open.bigmodel.cn/api/coding/paas/v4";
    static final String LLM_API_KEY = "40a1cff4ec6c45a09704ec79550211a3.eLcaJYrFS2unG829";
    static final String LLM_MODEL = "glm-4.7";

    // ========== LLM 沙箱指令系统提示 ==========
    static final String SYSTEM_PROMPT = """
            你是一个沙箱操作助手。分析用户指令，生成对应的沙箱操作参数。

            你必须且只能回复以下 JSON 格式（不要有其他内容，不要用 markdown 代码块包裹）：

            对于执行代码：
            {"action":"execute_code","language":"python","code":"print('hello')"}

            对于执行 Shell 命令：
            {"action":"run_command","command":"ls -la"}

            对于写入文件：
            {"action":"write_file","path":"hello.txt","content":"Hello World"}

            对于读取文件：
            {"action":"read_file","path":"hello.txt"}

            对于列出目录：
            {"action":"list_dir","path":"."}

            规则：
            1. language 支持 python、javascript、shell
            2. 默认使用 python
            3. path 为相对路径时基于 /workspace
            4. 只返回纯 JSON，不要 markdown 标记
            """;

    // ========== 结果总结系统提示 ==========
    static final String SUMMARY_PROMPT = """
            你是一个助手。根据沙箱执行结果，用简洁的中文向用户总结发生了什么。
            如果有输出内容，展示关键部分。如果有错误，说明原因。
            回复控制在 3 行以内。
            """;

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  GodlikeAgent 交互式沙箱聊天");
        System.out.println("  LLM: " + LLM_MODEL);
        System.out.println("  Docker: " + DOCKER_HOST);
        System.out.println("  输入 exit/quit 退出");
        System.out.println("========================================\n");

        // 1. 构建组件
        AgentProperties props = buildProps();
        DockerSandboxFactory factory = new DockerSandboxFactory(props);
        SandboxManager sandboxManager = new SandboxManager(factory, props);
        SandboxTool sandboxTool = new SandboxTool(sandboxManager);

        ChatModel chatModel = OpenAiChatModel.builder()
                .baseUrl(LLM_ENDPOINT)
                .apiKey(LLM_API_KEY)
                .modelName(LLM_MODEL)
                .temperature(0.3)
                .maxTokens(4096)
                .timeout(Duration.ofSeconds(120))
                .build();

        String sessionId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        System.out.println("[系统] 会话ID: " + sessionId);

        // 2. 预创建沙箱（避免首次指令时等待容器启动）
        System.out.println("[系统] 正在创建 Docker 沙箱...");
        long startCreate = System.currentTimeMillis();
        try {
            sandboxManager.getOrCreate(sessionId);
            System.out.printf("[系统] 沙箱就绪 (%.1fs)%n%n", (System.currentTimeMillis() - startCreate) / 1000.0);
        } catch (Exception e) {
            System.err.println("[错误] 沙箱创建失败: " + e.getMessage());
            return;
        }

        // 3. 交互循环
        Scanner scanner = new Scanner(System.in);
        int round = 0;

        while (true) {
            System.out.print("你> ");
            System.out.flush();

            String input;
            try {
                input = scanner.nextLine().trim();
            } catch (Exception e) {
                break;
            }

            if (input.isEmpty()) {
                continue;
            }
            if ("exit".equalsIgnoreCase(input) || "quit".equalsIgnoreCase(input)) {
                System.out.println("\n[系统] 正在销毁沙箱...");
                sandboxManager.destroy(sessionId);
                System.out.println("[系统] 再见！");
                break;
            }

            round++;
            long roundStart = System.currentTimeMillis();
            System.out.println("--- 第 " + round + " 轮 ---");

            // Step 1: LLM 生成沙箱操作参数
            System.out.println("  [思考中...]");
            Map<String, Object> toolParams;
            try {
                toolParams = generateSandboxCommand(chatModel, input, sessionId);
            } catch (Exception e) {
                System.err.println("  [错误] LLM 调用失败: " + e.getMessage());
                System.out.printf("--- 完成 (%.1fs) ---\n\n", (System.currentTimeMillis() - roundStart) / 1000.0);
                continue;
            }

            String action = String.valueOf(toolParams.getOrDefault("action", "execute_code"));
            System.out.printf("  [操作] %s%n", formatActionSummary(toolParams));

            // Step 2: 执行沙箱操作
            Object result;
            try {
                result = sandboxTool.execute(toolParams);
            } catch (Exception e) {
                System.err.println("  [错误] 沙箱执行失败: " + e.getMessage());
                System.out.printf("--- 完成 (%.1fs) ---\n\n", (System.currentTimeMillis() - roundStart) / 1000.0);
                continue;
            }

            // Step 3: 打印原始结果
            printResult(result);

            // Step 4: LLM 总结（仅在有复杂输出时）
            if (shouldSummarize(result)) {
                try {
                    String summary = summarizeResult(chatModel, input, result);
                    System.out.println("  [总结] " + summary);
                } catch (Exception e) {
                    // 总结失败不影响主流程
                }
            }

            System.out.printf("--- 完成 (%.1fs) ---\n\n", (System.currentTimeMillis() - roundStart) / 1000.0);
        }

        scanner.close();
    }

    // ========== LLM 交互 ==========

    /**
     * 调用 LLM 将用户指令转为沙箱操作参数
     */
    private static Map<String, Object> generateSandboxCommand(ChatModel chatModel, String userInput, String sessionId) {
        ChatResponse response = chatModel.chat(List.of(
                SystemMessage.from(SYSTEM_PROMPT),
                UserMessage.from(userInput)
        ));

        String llmOutput = response.aiMessage().text().trim();
        // 清理可能的 markdown 代码块包裹
        String json = extractJson(llmOutput);

        Map<String, Object> params = parseSimpleJson(json);
        params.put("sessionId", sessionId);
        return params;
    }

    /**
     * 调用 LLM 总结沙箱执行结果
     */
    private static String summarizeResult(ChatModel chatModel, String userQuery, Object result) {
        ChatResponse response = chatModel.chat(List.of(
                SystemMessage.from(SUMMARY_PROMPT),
                UserMessage.from("用户指令: " + userQuery + "\n\n沙箱结果: " + result)
        ));
        return response.aiMessage().text().trim();
    }

    // ========== JSON 解析 ==========

    /**
     * 从 LLM 输出中提取 JSON（兼容 markdown 代码块包裹）
     */
    private static String extractJson(String text) {
        // 尝试提取 ```json ... ``` 块
        Pattern codeBlock = Pattern.compile("```(?:json)?\\s*\\n?(\\{.*?})\\s*\\n?```", Pattern.DOTALL);
        Matcher matcher = codeBlock.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        // 尝试直接提取 { ... }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1).trim();
        }
        return text;
    }

    /**
     * 简易 JSON 解析（不依赖外部库，仅支持扁平键值对）
     */
    private static Map<String, Object> parseSimpleJson(String json) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (json == null || !json.startsWith("{")) {
            // 非 JSON，默认为 execute_code + python
            map.put("action", "execute_code");
            map.put("language", "python");
            map.put("code", json);
            return map;
        }

        // 去掉首尾 { }
        String content = json.substring(1, json.length() - 1).trim();
        // 按键值对分割（简易实现，不处理嵌套）
        StringBuilder currentKey = new StringBuilder();
        StringBuilder currentValue = new StringBuilder();
        boolean inKey = true;
        boolean inString = false;
        char stringChar = 0;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (inString) {
                if (c == stringChar && (i + 1 >= content.length() || content.charAt(i + 1) != stringChar)) {
                    inString = false;
                } else if (c == '\\' && i + 1 < content.length()) {
                    // 处理转义字符：\n → 换行, \\ → \, \" → "
                    char next = content.charAt(i + 1);
                    String escaped = switch (next) {
                        case 'n' -> "\n";
                        case 't' -> "\t";
                        case '\\' -> "\\";
                        case '"' -> "\"";
                        case '/' -> "/";
                        default -> String.valueOf(next);
                    };
                    if (inKey) {
                        currentKey.append(escaped);
                    } else {
                        currentValue.append(escaped);
                    }
                    i++; // 跳过转义的下一个字符
                } else {
                    if (inKey) {
                        currentKey.append(c);
                    } else {
                        currentValue.append(c);
                    }
                }
            } else if (c == '"' || c == '\'') {
                inString = true;
                stringChar = c;
            } else if (c == ':') {
                inKey = false;
                currentValue.setLength(0);
            } else if (c == ',') {
                putIfNotEmpty(map, currentKey.toString().trim(), currentValue.toString().trim());
                currentKey.setLength(0);
                inKey = true;
            } else if (inKey) {
                currentKey.append(c);
            } else {
                currentValue.append(c);
            }
        }
        putIfNotEmpty(map, currentKey.toString().trim(), currentValue.toString().trim());

        return map;
    }

    private static void putIfNotEmpty(Map<String, Object> map, String key, String value) {
        if (!key.isEmpty() && !value.isEmpty()) {
            // 去除可能的引号
            if (value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            map.put(key, value);
        }
    }

    // ========== 输出格式化 ==========

    private static String formatActionSummary(Map<String, Object> params) {
        String action = String.valueOf(params.getOrDefault("action", "execute_code"));
        return switch (action) {
            case "execute_code" -> String.format("执行 %s 代码", params.getOrDefault("language", "python"));
            case "run_command" -> String.format("执行命令: %s", params.get("command"));
            case "write_file" -> String.format("写入文件: %s", params.get("path"));
            case "read_file" -> String.format("读取文件: %s", params.get("path"));
            case "list_dir" -> String.format("列出目录: %s", params.getOrDefault("path", "."));
            default -> action;
        };
    }

    @SuppressWarnings("unchecked")
    private static void printResult(Object result) {
        if (result instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) result;
            Boolean success = (Boolean) map.get("success");

            if (Boolean.TRUE.equals(success)) {
                // 优先显示 content（read_file），其次 stdout
                if (map.containsKey("content") && map.get("content") != null) {
                    System.out.println("  [内容] " + map.get("content"));
                }
                if (map.containsKey("stdout") && map.get("stdout") != null) {
                    String stdout = String.valueOf(map.get("stdout"));
                    if (!stdout.isBlank()) {
                        System.out.println("  [输出] " + stdout.stripTrailing());
                    }
                }
                if (map.containsKey("elapsedMs")) {
                    System.out.printf("  [耗时] %dms%n", map.get("elapsedMs"));
                }
            } else {
                System.err.println("  [失败] " + map.getOrDefault("error", "未知错误"));
                if (map.containsKey("stderr")) {
                    String stderr = String.valueOf(map.get("stderr"));
                    if (!stderr.isBlank()) {
                        System.err.println("  [错误输出] " + stderr.stripTrailing());
                    }
                }
            }
        } else {
            System.out.println("  [结果] " + result);
        }
    }

    private static boolean shouldSummarize(Object result) {
        if (!(result instanceof Map)) return false;
        Map<?, ?> map = (Map<?, ?>) result;
        // 有 stdout 且较长，或有错误时总结
        Object stdout = map.get("stdout");
        if (stdout instanceof String s && s.length() > 200) return true;
        return !Boolean.TRUE.equals(map.get("success"));
    }

    // ========== 配置构建 ==========

    static AgentProperties buildProps() {
        AgentProperties props = new AgentProperties();
        AgentProperties.SandboxConfig sandboxConfig = props.getTools().getSandbox();
        sandboxConfig.setEnabled(true);
        sandboxConfig.setTimeout(60);

        AgentProperties.CustomSandboxConfig customConfig = sandboxConfig.getCustom();
        customConfig.setSocketPath(DOCKER_HOST);
        customConfig.setImage(SANDBOX_IMAGE);
        customConfig.setMemoryLimit("512m");
        customConfig.setCpuLimit(1.0);
        customConfig.setNetworkMode("none");
        customConfig.setWorkspacePath("/workspace");
        customConfig.setAutoRemove(true);
        customConfig.setPidsLimit(100);
        customConfig.setVolumeBasePath("/data/agent-sandbox");

        return props;
    }
}
