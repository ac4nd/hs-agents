package com.hypersense.boot.framework.agents.engine.node;

import com.hypersense.boot.framework.agents.config.AgentProperties;
import com.hypersense.boot.framework.agents.config.ToolRetryConfig;
import com.hypersense.boot.framework.agents.engine.SubAgentEventBus;
import com.hypersense.boot.framework.agents.enums.AgentEventType;
import com.hypersense.boot.framework.agents.enums.TodoStatus;
import com.hypersense.boot.framework.agents.model.AgentEvent;
import com.hypersense.boot.framework.agents.model.DeepAgentState;
import com.hypersense.boot.framework.agents.model.TodoItem;
import com.hypersense.boot.framework.agents.tool.ToolProvider;
import dev.langchain4j.data.message.AiMessage;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 工具调用节点
 * <p>
 * 根据当前 TODO 的需要，查找并调用注册的工具，将结果写回状态。
 * 支持可配置的指数退避重试（通过 {@link ToolRetryConfig}）。
 * </p>
 *
 * @author Claude
 * @since 2026/5/15
 */
@Slf4j
@Component
public class ToolNode implements NodeAction<DeepAgentState> {

    private final List<ToolProvider> toolProviders;
    private final ToolRetryConfig retryConfig;

    /**
     * Spring 唯一公共构造器（自动注入工具列表和可选配置）
     * <p>
     * Spring Boot 注入 List&lt;ToolProvider&gt; 和可选的 AgentProperties。
     * AgentProperties 存在时从中读取重试配置，否则禁用重试。
     * </p>
     */
    @Autowired
    public ToolNode(@Nullable List<ToolProvider> toolProviders,
                    @Nullable AgentProperties agentProperties) {
        this.toolProviders = toolProviders != null ? toolProviders : List.of();
        this.retryConfig = resolveRetryConfig(agentProperties);
    }

    /**
     * Builder 路径：创建带自定义重试配置的 ToolNode（不经过 Spring）
     *
     * @param toolProviders 工具列表
     * @param retryConfig   重试配置（null 等同于禁用）
     * @return 新实例
     */
    public static ToolNode create(List<ToolProvider> toolProviders, ToolRetryConfig retryConfig) {
        return new ToolNode(toolProviders, retryConfig != null ? retryConfig : ToolRetryConfig.disabled());
    }

    /**
     * 内部构造器（直接传入 ToolRetryConfig，供 create() 使用）
     */
    private ToolNode(List<ToolProvider> toolProviders, ToolRetryConfig retryConfig) {
        this.toolProviders = toolProviders;
        this.retryConfig = retryConfig;
    }

    private static ToolRetryConfig resolveRetryConfig(AgentProperties props) {
        if (props == null) {
            return ToolRetryConfig.disabled();
        }
        return ToolRetryConfig.fromProperties(props.getTools().getToolRetry());
    }

    @Override
    public Map<String, Object> apply(DeepAgentState state) {
        Optional<TodoItem> currentTodoOpt = state.currentTodo();
        if (currentTodoOpt.isEmpty()) {
            log.warn("ToolNode: 无当前 TODO，跳过工具调用");
            return Map.of();
        }

        TodoItem todo = currentTodoOpt.get();
        log.info("ToolNode: 为 TODO [{}] 查找工具", todo.getDescription());

        // 推送工具调用开始事件，让前端看到进度（而非静默执行）
        emit(AgentEventType.TOOL_CALL, "调用工具: " + todo.getDescription(),
                Map.of("todo", todo, "tools", toolProviders.stream().map(ToolProvider::name).toList()));

        // 根据 enabledTools 过滤工具执行
        List<String> enabledTools = state.enabledTools();
        Map<String, Object> toolResults = new HashMap<>();
        Map<String, String> files = new HashMap<>(state.files());

        for (ToolProvider tool : toolProviders) {
            // 启用了工具过滤时，仅执行白名单中的工具
            if (!enabledTools.isEmpty() && !enabledTools.contains(tool.name())) {
                log.debug("ToolNode: 工具 [{}] 未启用，跳过", tool.name());
                continue;
            }
            // 智能过滤：根据 TODO 描述判断是否需要该工具，避免 FileReadTool/FileWriteTool 无参时被误触发
            if (!shouldInvoke(tool, todo.getDescription())) {
                log.info("ToolNode: 工具 [{}] 与当前 TODO 不匹配，跳过", tool.name());
                continue;
            }
            try {
                Map<String, Object> params = new HashMap<>();
                params.put("todo_description", todo.getDescription());
                params.put("instructions", state.instructions());
                // 传递 sessionId 供沙箱工具等需要会话隔离的组件使用
                String sessionId = state.sessionId();
                if (sessionId != null && !sessionId.isBlank()) {
                    params.put("sessionId", sessionId);
                }
                Object result = executeWithRetry(tool, params);
                toolResults.put(tool.name(), result);

                // 文件写入工具特殊处理
                if ("file_write".equals(tool.name()) && result instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> resultMap = (Map<String, Object>) result;
                    if (Boolean.TRUE.equals(resultMap.get("success"))) {
                        String filename = (String) resultMap.get("filename");
                        if (filename != null) {
                            files.put(filename, todo.getDescription() + " — 执行完成");
                        }
                    }
                }

                log.info("ToolNode: 工具 [{}] 执行成功", tool.name());
            } catch (Exception e) {
                log.error("ToolNode: 工具 [{}] 执行失败（已耗尽重试次数或重试未启用）", tool.name(), e);
                toolResults.put(tool.name(), "执行失败: " + e.getMessage());
            }
        }

        // 若所有工具都被过滤，记录提示（避免 toolResults 空时 final 报错）
        if (toolResults.isEmpty()) {
            log.warn("ToolNode: 无工具匹配 TODO [{}]，可能应由 direct 策略处理", todo.getDescription());
        }

        // 更新 TODO 状态（检查是否有工具执行失败）
        boolean hasFailure = toolResults.values().stream()
                .anyMatch(v -> v instanceof String s && s.startsWith("执行失败:"));
        TodoStatus resultStatus = hasFailure ? TodoStatus.FAILED : TodoStatus.COMPLETED;

        // 提取可读摘要作为 todo.result，让前端展示和 FinalizeNode 引用
        String readableResult = extractReadableResult(toolResults);

        List<TodoItem> updatedTodos = new ArrayList<>(state.todos());
        TodoItem updatedTodo = null;
        for (int i = 0; i < updatedTodos.size(); i++) {
            if (updatedTodos.get(i).getId().equals(todo.getId())) {
                updatedTodo = TodoItem.builder()
                        .id(todo.getId())
                        .description(todo.getDescription())
                        .status(resultStatus)
                        .result(readableResult)
                        .assignedAgent(todo.getAssignedAgent())
                        .updatedAt(LocalDateTime.now())
                        .build();
                updatedTodos.set(i, updatedTodo);
                break;
            }
        }

        // 推送 TODO 完成事件（携带工具结果），让前端展示进度而非静默迭代
        if (updatedTodo != null) {
            emit(AgentEventType.TODO_COMPLETED, "已完成: " + todo.getDescription(),
                    Map.of("todo", updatedTodo, "toolResults", toolResults));
        }

        return Map.of(
                DeepAgentState.TODOS, updatedTodos,
                DeepAgentState.FILES, files,
                DeepAgentState.MESSAGES, AiMessage.from(
                        String.format("工具调用完成: %s", toolResults.keySet()))
        );
    }

    /**
     * 通过 SubAgentEventBus 推送事件
     */
    private void emit(AgentEventType type, String message, Map<String, Object> data) {
        var consumer = SubAgentEventBus.get();
        if (consumer == null) return;
        AgentEvent event = AgentEvent.builder()
                .type(type)
                .message(message)
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build();
        consumer.accept(event);
    }

    /**
     * 从工具返回结果中提取可读摘要，用于前端展示和 FinalizeNode 引用。     * <p>
     * 支持的返回结构：
     * <ul>
     *   <li>internet_search: {success, query, resultCount, results: [{title, url, content}]}</li>
     *   <li>其他工具：尝试 toString 兜底</li>
     * </ul>
     * </p>
     */
    @SuppressWarnings("unchecked")
    private String extractReadableResult(Map<String, Object> toolResults) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : toolResults.entrySet()) {
            String toolName = entry.getKey();
            Object result = entry.getValue();
            if (result instanceof String s && s.startsWith("执行失败:")) {
                sb.append("[").append(toolName).append("] ").append(s).append("\n");
                continue;
            }
            if (result instanceof Map<?, ?> resultMap) {
                // internet_search 结构
                Object resultsObj = resultMap.get("results");
                Object queryObj = resultMap.get("query");
                if (resultsObj instanceof List<?> list && !list.isEmpty()) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append("搜索关键词: ").append(queryObj != null ? queryObj : "N/A").append("\n");
                    int idx = 1;
                    for (Object item : list) {
                        if (!(item instanceof Map<?, ?>)) continue;
                        Map<?, ?> m = (Map<?, ?>) item;
                        String title = stringOrEmpty(m.get("title"));
                        String content = stringOrEmpty(m.get("content"));
                        String url = stringOrEmpty(m.get("url"));
                        sb.append(idx++).append(". ").append(title);
                        if (!content.isBlank()) sb.append(" — ").append(content);
                        if (!url.isBlank()) sb.append("（").append(url).append("）");
                        sb.append("\n");
                        if (idx > 5) break; // 最多 5 条避免过长
                    }
                    continue;
                }
                // 其他 Map 结果：优先取 message/success 字段，否则整体 toString
                Object msg = resultMap.get("message");
                sb.append("[").append(toolName).append("] ")
                        .append(msg != null ? msg : resultMap).append("\n");
                continue;
            }
            sb.append("[").append(toolName).append("] ").append(result).append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * null 安全的字符串提取：null/非 String 值都返回空串。
     */
    private String stringOrEmpty(Object v) {
        return v != null ? String.valueOf(v) : "";
    }

    /**
     * 根据 TODO 描述判断是否应该调用该工具，避免无参数工具被无脑遍历触发。
     * <p>
     * 规则：
     * <ul>
     *   <li>internet_search: TODO 含搜索/查询/查/获取/找/最新/实时/今天/当前/天气/新闻/股价/汇率 等关键词</li>
     *   <li>file_read: TODO 含"读取/打开/read" 等关键词</li>
     *   <li>file_write: TODO 含"写入/保存/write/创建文件" 等关键词</li>
     *   <li>sandbox_execute: TODO 含"执行/运行代码/sandbox"</li>
     *   <li>其他工具：默认 true（向后兼容）</li>
     * </ul>
     * </p>
     */
    private boolean shouldInvoke(ToolProvider tool, String todoDescription) {
        if (todoDescription == null || todoDescription.isBlank()) return true;
        String desc = todoDescription.toLowerCase();
        String name = tool.name();
        switch (name) {
            case "internet_search":
                return desc.matches(".*\\b(search|query|lookup|fetch|news|weather|price|rate|latest)\\b.*")
                        || desc.contains("搜索") || desc.contains("查询") || desc.contains("获取")
                        || desc.contains("查找") || desc.contains("最新") || desc.contains("实时")
                        || desc.contains("今天") || desc.contains("当前") || desc.contains("天气")
                        || desc.contains("新闻") || desc.contains("股价") || desc.contains("汇率");
            case "file_read":
                return desc.contains("读取文件") || desc.contains("打开文件") || desc.contains("read")
                        || desc.contains("查看文件") || desc.contains("读取") && desc.contains("文件");
            case "file_write":
                return desc.contains("写入文件") || desc.contains("保存文件") || desc.contains("write")
                        || desc.contains("创建文件") || (desc.contains("写入") && desc.contains("文件"))
                        || (desc.contains("保存") && desc.contains("文件"));
            case "sandbox_execute":
                return desc.contains("执行") || desc.contains("运行代码") || desc.contains("sandbox");
            default:
                return true;
        }
    }

    // ========== 重试逻辑 ==========

    /**
     * 带重试的工具执行
     * <p>
     * 关闭时直接调用 {@link ToolProvider#execute}，零开销。
     * 启用时按指数退避策略重试，最多 {@code maxAttempts} 次。
     * </p>
     *
     * @param tool   工具提供者
     * @param params 执行参数
     * @return 工具执行结果
     */
    private Object executeWithRetry(ToolProvider tool, Map<String, Object> params) throws Exception {
        if (!retryConfig.isEnabled()) {
            return tool.execute(params);
        }

        int maxAttempts = Math.max(1, retryConfig.getMaxAttempts());
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return tool.execute(params);
            } catch (Exception e) {
                lastException = e;

                if (attempt >= maxAttempts) {
                    log.warn("ToolNode: 工具 [{}] 达到最大重试次数 {}，放弃重试",
                            tool.name(), maxAttempts);
                    break;
                }

                long delay = retryConfig.calculateDelay(attempt);
                log.warn("ToolNode: 工具 [{}] 第 {}/{} 次执行失败，{}ms 后重试: {}",
                        tool.name(), attempt, maxAttempts, delay, e.getMessage());

                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("ToolNode: 工具 [{}] 重试等待被中断", tool.name());
                    throw e;
                }
            }
        }

        throw lastException;
    }
}
