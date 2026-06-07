package com.hypersense.boot.framework.agents.engine.node;

import com.hypersense.boot.framework.agents.enums.AgentEventType;
import com.hypersense.boot.framework.agents.exception.HitlInterruptedException;
import com.hypersense.boot.framework.agents.GodlikeAgent;
import com.hypersense.boot.framework.agents.engine.SubAgentEventBus;
import com.hypersense.boot.framework.agents.model.AgentEvent;
import com.hypersense.boot.framework.agents.model.SubAgentContext;
import com.hypersense.boot.framework.agents.model.SubAgentDefinition;
import com.hypersense.boot.framework.agents.sandbox.SandboxManager;
import com.hypersense.boot.framework.agents.tool.ToolProvider;
import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * 子 Agent 执行器
 * <p>
 * 将 {@link SubAgentContext} 转换为完整配置的 {@link GodlikeAgent} 实例并同步执行。
 * 负责工具过滤、递归深度限制、超时保护和结果提取。
 * </p>
 *
 * <h3>设计要点：</h3>
 * <ul>
 *   <li>子 Agent 通过 {@link GodlikeAgent.Builder} 构建独立实例，拥有自己的 plan→execute→tool 循环</li>
 *   <li>沙箱由子 Agent 自己管理（生成 sessionId + finally 清理），本执行器不干预</li>
 *   <li>超时通过 {@link CompletableFuture#get(long, TimeUnit)} 实现，超时后 {@code cancel(true)} 中断子线程</li>
 *   <li>使用专用 {@link ExecutorService} 避免占用 {@link ForkJoinPool#commonPool()}</li>
 * </ul>
 *
 * @author Claude
 * @since 2026/5/23
 */
@Slf4j
public class SubAgentExecutor {

    /** 全局最大递归深度硬上限（防止配置失误导致无限嵌套） */
    private static final int ABSOLUTE_MAX_DEPTH = 3;

    /** 默认最大递归深度（0=root, 1=第一层子, 2=上限） */
    static final int DEFAULT_MAX_DEPTH = 2;

    /** 子 Agent 专用线程池（守护线程，不阻止 JVM 退出） */
    private static final ExecutorService executorService = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "sub-agent-executor");
        t.setDaemon(true);
        return t;
    });

    private final ChatModel chatModel;
    private final List<ToolProvider> allParentTools;
    private final SandboxManager sandboxManager;

    public SubAgentExecutor(ChatModel chatModel,
                            List<ToolProvider> allParentTools,
                            SandboxManager sandboxManager) {
        this.chatModel = chatModel;
        this.allParentTools = allParentTools;
        this.sandboxManager = sandboxManager;
    }

    /**
     * 同步执行子 Agent（带超时保护）
     * <p>
     * 当 {@link SubAgentEventBus} 中存在活跃消费者时，自动切换为流式模式：
     * 子 Agent 内部节点事件通过消费者包装后向上冒泡到 SSE 通道。
     * 无消费者时走原有同步 {@code run()} 路径，零开销。
     * </p>
     *
     * @param context 子 Agent 委派上下文
     * @return 执行结果
     */
    public SubAgentResult execute(SubAgentContext context) {
        SubAgentDefinition definition = context.getDefinition();

        // 1. 递归深度检查（优先使用 definition 配置，硬上限 ABSOLUTE_MAX_DEPTH）
        int maxDepth = resolveMaxDepth(definition);
        if (context.getCurrentDepth() >= maxDepth) {
            log.warn("SubAgentExecutor: 递归深度 {} >= 最大 {}，拒绝委派",
                    context.getCurrentDepth(), maxDepth);
            return SubAgentResult.failure(
                    "子 Agent 递归深度已达上限 (" + maxDepth + ")，停止委派");
        }

        // 2. 过滤工具子集
        List<ToolProvider> subTools = filterTools(definition);

        // 3. 构建任务输入
        String taskInput = buildTaskInput(context);

        // 4. 获取超时配置
        long timeoutSeconds = definition.getTimeoutSeconds() != null
                ? definition.getTimeoutSeconds() : 120L;
        int subRecursionLimit = definition.getRecursionLimit() != null
                ? definition.getRecursionLimit() : 15;

        // 5. 在图执行线程上捕获事件消费者（闭包传播到子 Agent 线程）
        Consumer<AgentEvent> parentConsumer = SubAgentEventBus.get();

        // 6. 构建并执行嵌套 GodlikeAgent（带超时）
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            GodlikeAgent.Builder builder = GodlikeAgent.builder()
                    .model(chatModel)
                    .recursionLimit(subRecursionLimit);

            // 添加过滤后的工具
            for (ToolProvider tool : subTools) {
                builder.addTool(tool);
            }

            // 复用父 Agent 的 SandboxManager（子 Agent 自己生成 sessionId + 清理）
            if (sandboxManager != null) {
                builder.sandboxManager(sandboxManager);
            }

            // 将 systemPrompt 嵌入输入
            String fullInput = embedSystemPrompt(definition.getSystemPrompt(), taskInput);

            GodlikeAgent subAgent = builder.build();

            if (parentConsumer != null) {
                // 流式模式：子 Agent 事件包装后冒泡到父级 SSE 通道
                return executeWithStreaming(subAgent, definition.getName(), fullInput, context, parentConsumer);
            } else {
                // 同步模式：无事件流（默认路径，零开销）
                return subAgent.run(fullInput, context.getCurrentDepth() + 1);
            }
        }, executorService);

        try {
            String result = future.get(timeoutSeconds, TimeUnit.SECONDS);
            log.info("SubAgentExecutor: 子 Agent [{}] 执行完成", definition.getName());
            return SubAgentResult.success(result);

        } catch (TimeoutException e) {
            future.cancel(true);
            log.error("SubAgentExecutor: 子 Agent [{}] 超时（{}s），已发送取消信号",
                    definition.getName(), timeoutSeconds);
            return SubAgentResult.failure(
                    "子 Agent [" + definition.getName() + "] 执行超时 (" + timeoutSeconds + "s)");
        } catch (ExecutionException e) {
            // HITL 中断异常传播（解包 ExecutionException）
            if (e.getCause() instanceof HitlInterruptedException hitlEx) {
                log.info("SubAgentExecutor: 子 Agent [{}] 触发 HITL 中断", definition.getName());
                throw hitlEx;
            }
            log.error("SubAgentExecutor: 子 Agent [{}] 执行失败",
                    definition.getName(), e.getCause());
            return SubAgentResult.failure("子 Agent 执行失败: " + e.getCause().getMessage());
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            return SubAgentResult.failure("子 Agent 被中断");
        }
    }

    // ========== 内部方法 ==========

    /**
     * 流式执行子 Agent，将内部节点事件包装后冒泡到父级
     * <p>
     * 包装策略：
     * <ul>
     *   <li>NODE_EXECUTION → SUB_AGENT_NODE_EXECUTION</li>
     *   <li>FINAL_RESPONSE → SUB_AGENT_COMPLETED</li>
     *   <li>ERROR → SUB_AGENT_FAILED</li>
     *   <li>其他（HITL 等）→ 原样透传</li>
     * </ul>
     * 同时在子 Agent 线程上设置 {@link SubAgentEventBus}，支持嵌套子 Agent 事件继续冒泡。
     * </p>
     */
    private String executeWithStreaming(GodlikeAgent subAgent,
                                        String agentName,
                                        String fullInput,
                                        SubAgentContext context,
                                        Consumer<AgentEvent> parentConsumer) {
        // 构建包装消费者：为子 Agent 事件添加元数据
        // 注意：FINAL_RESPONSE 和 ERROR 由 executeWithStreaming 的生命周期事件处理，此处抑制避免重复
        Consumer<AgentEvent> wrappedConsumer = event -> {
            if (event.getType() == AgentEventType.FINAL_RESPONSE
                    || event.getType() == AgentEventType.ERROR) {
                return; // 抑制，由 SUB_AGENT_COMPLETED / SUB_AGENT_FAILED 生命周期事件替代
            }
            AgentEventType subType = toSubAgentEventType(event.getType());
            AgentEvent subEvent = AgentEvent.builder()
                    .type(subType)
                    .message("[" + agentName + "] " + event.getMessage())
                    .data(event.getData())
                    .timestamp(event.getTimestamp())
                    .build();
            parentConsumer.accept(subEvent);
        };

        // 在子 Agent 线程上设置 EventBus（嵌套子 Agent 通过闭包捕获继续冒泡）
        SubAgentEventBus.set(wrappedConsumer);

        try {
            // 推送子 Agent 开始事件
            parentConsumer.accept(AgentEvent.builder()
                    .type(AgentEventType.SUB_AGENT_STARTED)
                    .message("子 Agent [" + agentName + "] 开始执行")
                    .data(Map.of("agentName", agentName,
                            "depth", context.getCurrentDepth() + 1,
                            "task", context.getTaskDescription()))
                    .timestamp(System.currentTimeMillis())
                    .build());

            String result = subAgent.streamAndReturn(
                    fullInput, context.getCurrentDepth() + 1, wrappedConsumer);

            // 推送子 Agent 完成事件
            parentConsumer.accept(AgentEvent.builder()
                    .type(AgentEventType.SUB_AGENT_COMPLETED)
                    .message("子 Agent [" + agentName + "] 执行完成")
                    .data(result != null ? Map.of("agentName", agentName,
                            "resultLength", result.length()) : Map.of("agentName", agentName))
                    .timestamp(System.currentTimeMillis())
                    .build());

            return result;
        } catch (Exception e) {
            // 推送子 Agent 失败事件（不重复抛出，由 CompletableFuture 包装）
            parentConsumer.accept(AgentEvent.builder()
                    .type(AgentEventType.SUB_AGENT_FAILED)
                    .message("子 Agent [" + agentName + "] 执行失败: " + e.getMessage())
                    .timestamp(System.currentTimeMillis())
                    .build());
            throw e;
        } finally {
            SubAgentEventBus.remove();
        }
    }

    /**
     * 将父级事件类型映射为子 Agent 事件类型
     */
    private static AgentEventType toSubAgentEventType(AgentEventType type) {
        return switch (type) {
            case NODE_EXECUTION -> AgentEventType.SUB_AGENT_NODE_EXECUTION;
            case FINAL_RESPONSE -> AgentEventType.SUB_AGENT_COMPLETED;
            case ERROR -> AgentEventType.SUB_AGENT_FAILED;
            default -> type; // HITL 事件、子 Agent 事件等原样透传
        };
    }

    /**
     * 解析最大递归深度
     * <p>
     * 优先级：definition.maxDepth → DEFAULT_MAX_DEPTH，不超过 ABSOLUTE_MAX_DEPTH
     * </p>
     */
    private int resolveMaxDepth(SubAgentDefinition definition) {
        int depth = definition.getMaxDepth() != null ? definition.getMaxDepth() : DEFAULT_MAX_DEPTH;
        return Math.min(depth, ABSOLUTE_MAX_DEPTH);
    }

    /**
     * 按 availableTools 白名单过滤工具
     */
    private List<ToolProvider> filterTools(SubAgentDefinition definition) {
        List<String> allowed = definition.getAvailableTools();
        if (allowed == null || allowed.isEmpty()) {
            return List.of();
        }
        return allParentTools.stream()
                .filter(t -> allowed.contains(t.name()))
                .toList();
    }

    /**
     * 构建子 Agent 的任务输入（包含父上下文）
     */
    private String buildTaskInput(SubAgentContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("【父 Agent 原始指令】\n").append(context.getParentInstructions()).append("\n\n");
        sb.append("【当前任务】\n").append(context.getTaskDescription()).append("\n");

        Map<String, String> prevResults = context.getPreviousSubAgentResults();
        if (prevResults != null && !prevResults.isEmpty()) {
            sb.append("\n【其他子 Agent 已完成的结果】\n");
            prevResults.forEach((k, v) ->
                    sb.append("- ").append(k).append(": ")
                            .append(v.length() > 200 ? v.substring(0, 200) + "..." : v)
                            .append("\n"));
        }
        return sb.toString();
    }

    /**
     * 将 systemPrompt 嵌入到任务输入前面
     */
    private String embedSystemPrompt(String systemPrompt, String taskInput) {
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            return systemPrompt + "\n\n" + taskInput;
        }
        return taskInput;
    }

    // ========== 结果值对象 ==========

    /**
     * 子 Agent 执行结果
     */
    @lombok.Value
    public static class SubAgentResult {
        boolean success;
        String output;

        public static SubAgentResult success(String output) {
            return new SubAgentResult(true, output);
        }

        public static SubAgentResult failure(String reason) {
            return new SubAgentResult(false, reason);
        }
    }
}
