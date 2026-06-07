package com.hypersense.boot.framework.agents.engine.node;

import com.hypersense.boot.framework.agents.config.AgentProperties;
import com.hypersense.boot.framework.agents.config.ToolRetryConfig;
import com.hypersense.boot.framework.agents.enums.TodoStatus;
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

        // 更新 TODO 状态（检查是否有工具执行失败）
        boolean hasFailure = toolResults.values().stream()
                .anyMatch(v -> v instanceof String s && s.startsWith("执行失败:"));
        TodoStatus resultStatus = hasFailure ? TodoStatus.FAILED : TodoStatus.COMPLETED;
        String resultPrefix = hasFailure ? "工具调用部分失败" : "工具调用完成";

        List<TodoItem> updatedTodos = new ArrayList<>(state.todos());
        for (int i = 0; i < updatedTodos.size(); i++) {
            if (updatedTodos.get(i).getId().equals(todo.getId())) {
                updatedTodos.set(i, TodoItem.builder()
                        .id(todo.getId())
                        .description(todo.getDescription())
                        .status(resultStatus)
                        .result(resultPrefix + ": " + toolResults.keySet())
                        .assignedAgent(todo.getAssignedAgent())
                        .updatedAt(LocalDateTime.now())
                        .build());
                break;
            }
        }

        return Map.of(
                DeepAgentState.TODOS, updatedTodos,
                DeepAgentState.FILES, files,
                DeepAgentState.MESSAGES, AiMessage.from(
                        String.format("工具调用完成: %s", toolResults.keySet()))
        );
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
