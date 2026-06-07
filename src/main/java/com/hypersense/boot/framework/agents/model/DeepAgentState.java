package com.hypersense.boot.framework.agents.model;

import dev.langchain4j.data.message.ChatMessage;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.bsc.langgraph4j.state.Reducer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Deep Agent 状态模型
 * <p>
 * 基于 langgraph4j AgentState，通过 Channel 机制管理状态更新策略。
 * </p>
 *
 * <ul>
 *   <li>MESSAGES — 对话历史，追加模式</li>
 *   <li>TODOS — 任务列表，整体替换</li>
 *   <li>FILES — 产物文件，整体替换</li>
 *   <li>CURRENT_TODO — 当前执行中的 TODO，整体替换</li>
 *   <li>SUB_AGENT_RESULTS — 子 Agent 结果，整体替换</li>
 *   <li>FINAL_RESPONSE — 最终响应，整体替换</li>
 * </ul>
 *
 * @author Claude
 * @since 2026/5/15
 */
public class DeepAgentState extends AgentState {

    public static final String MESSAGES = "messages";
    public static final String TODOS = "todos";
    public static final String FILES = "files";
    public static final String CURRENT_TODO = "current_todo";
    public static final String SUB_AGENT_RESULTS = "sub_agent_results";
    public static final String FINAL_RESPONSE = "final_response";
    public static final String INSTRUCTIONS = "instructions";
    public static final String ITERATION_COUNT = "iteration_count";
    public static final String ENABLED_TOOLS = "enabled_tools";
    public static final String EXECUTE_STRATEGY = "execute_strategy";
    public static final String SESSION_ID = "session_id";

    /** 压缩后的对话摘要（由 MessageCompressionMiddleware 写入） */
    public static final String COMPRESSED_CONTEXT = "compressed_context";

    /** 当前委派深度（0 = 根 Agent，1 = 第一层子 Agent，2 = 上限） */
    public static final String DELEGATION_DEPTH = "delegation_depth";

    // ========== HITL（Human-in-the-Loop）状态通道 ==========

    /** 是否启用 HITL 审批（base, default false） */
    public static final String HITL_ENABLED = "hitl_enabled";

    /** 审批状态：NONE / PENDING / APPROVED / REJECTED / MODIFIED */
    public static final String APPROVAL_STATUS = "approval_status";

    /** 人工反馈文本 */
    public static final String HUMAN_FEEDBACK = "human_feedback";

    /** 触发中断的节点名 */
    public static final String INTERRUPTED_NODE = "interrupted_node";

    /** 序列化的 InterruptContext（JSON） */
    public static final String INTERRUPT_CONTEXT = "interrupt_context";

    // ========== 长期记忆状态通道 ==========

    /** 当前用户 ID（用于记忆的租户+用户隔离） */
    public static final String USER_ID = "user_id";

    /** 当前租户 ID */
    public static final String TENANT_ID = "tenant_id";

    /**
     * 状态 Schema 定义
     * <ul>
     *   <li>MESSAGES: 追加模式（Appender），对话消息不断累积</li>
     *   <li>其他字段: base 模式，新值覆盖旧值</li>
     * </ul>
     */
    public static final Map<String, Channel<?>> SCHEMA = Map.ofEntries(
            Map.entry(MESSAGES, Channels.appender(ArrayList::new)),
            Map.entry(TODOS, Channels.base((Supplier<List<TodoItem>>) ArrayList::new)),
            Map.entry(FILES, Channels.base((Supplier<Map<String, String>>) HashMap::new)),
            Map.entry(CURRENT_TODO, Channels.base((Supplier<TodoItem>) TodoItem::new)),
            Map.entry(SUB_AGENT_RESULTS, Channels.base((Supplier<Map<String, String>>) HashMap::new)),
            Map.entry(FINAL_RESPONSE, Channels.base((Supplier<String>) () -> "")),
            Map.entry(INSTRUCTIONS, Channels.base((Supplier<String>) () -> "")),
            Map.entry(ITERATION_COUNT, Channels.base((Reducer<Integer>) (current, update) -> update, () -> 0)),
            Map.entry(ENABLED_TOOLS, Channels.base((Supplier<List<String>>) ArrayList::new)),
            Map.entry(EXECUTE_STRATEGY, Channels.base((Supplier<String>) () -> "direct")),
            Map.entry(SESSION_ID, Channels.base((Supplier<String>) () -> "")),
            Map.entry(COMPRESSED_CONTEXT, Channels.base((Supplier<String>) () -> "")),
            Map.entry(DELEGATION_DEPTH, Channels.base((Reducer<Integer>) (current, update) -> update, () -> 0)),
            // HITL 状态通道
            Map.entry(HITL_ENABLED, Channels.base((Supplier<Boolean>) () -> false)),
            Map.entry(APPROVAL_STATUS, Channels.base((Supplier<String>) () -> "NONE")),
            Map.entry(HUMAN_FEEDBACK, Channels.base((Supplier<String>) () -> "")),
            Map.entry(INTERRUPTED_NODE, Channels.base((Supplier<String>) () -> "")),
            Map.entry(INTERRUPT_CONTEXT, Channels.base((Supplier<String>) () -> "")),
            // 长期记忆状态通道
            Map.entry(USER_ID, Channels.base((Supplier<Long>) () -> 0L)),
            Map.entry(TENANT_ID, Channels.base((Supplier<Long>) () -> 0L))
    );

    public DeepAgentState(Map<String, Object> initData) {
        super(initData);
    }

    // ========== 便捷访问方法 ==========

    /**
     * 获取对话历史
     */
    @SuppressWarnings("unchecked")
    public List<ChatMessage> chatMessages() {
        return this.<List<ChatMessage>>value(MESSAGES).orElse(List.of());
    }

    /**
     * 获取 TODO 列表
     */
    @SuppressWarnings("unchecked")
    public List<TodoItem> todos() {
        return this.<List<TodoItem>>value(TODOS).orElse(List.of());
    }

    /**
     * 获取产物文件
     */
    @SuppressWarnings("unchecked")
    public Map<String, String> files() {
        return this.<Map<String, String>>value(FILES).orElse(Map.of());
    }

    /**
     * 获取当前执行的 TODO
     * <p>
     * 过滤哨兵值（id 为空的默认 TodoItem），保持 Optional.empty() 语义。
     * </p>
     */
    public Optional<TodoItem> currentTodo() {
        return this.<TodoItem>value(CURRENT_TODO)
                .filter(todo -> todo != null && todo.getId() != null && !todo.getId().isBlank());
    }

    /**
     * 获取子 Agent 执行结果
     */
    @SuppressWarnings("unchecked")
    public Map<String, String> subAgentResults() {
        return this.<Map<String, String>>value(SUB_AGENT_RESULTS).orElse(Map.of());
    }

    /**
     * 获取最终响应
     */
    public Optional<String> finalResponse() {
        return this.value(FINAL_RESPONSE);
    }

    /**
     * 获取系统指令
     */
    public String instructions() {
        return this.<String>value(INSTRUCTIONS).orElse("");
    }

    /**
     * 获取迭代计数
     */
    public int iterationCount() {
        return this.<Integer>value(ITERATION_COUNT).orElse(0);
    }

    /**
     * 获取启用的工具列表
     */
    @SuppressWarnings("unchecked")
    public List<String> enabledTools() {
        return this.<List<String>>value(ENABLED_TOOLS).orElse(List.of());
    }

    /**
     * 获取执行策略
     */
    public String executeStrategy() {
        return this.<String>value(EXECUTE_STRATEGY).orElse("direct");
    }

    /**
     * 获取当前会话 ID（沙箱隔离的 key）
     */
    public String sessionId() {
        return this.<String>value(SESSION_ID).orElse("");
    }

    /**
     * 获取压缩后的对话上下文摘要
     */
    public Optional<String> compressedContext() {
        return this.<String>value(COMPRESSED_CONTEXT).filter(s -> !s.isBlank());
    }

    /**
     * 获取当前委派深度（0 = 根 Agent）
     */
    public int delegationDepth() {
        return this.<Integer>value(DELEGATION_DEPTH).orElse(0);
    }

    // ========== HITL 便捷访问方法 ==========

    /**
     * 是否启用 HITL 审批
     */
    public boolean hitlEnabled() {
        return this.<Boolean>value(HITL_ENABLED).orElse(false);
    }

    /**
     * 获取审批状态
     */
    public String approvalStatus() {
        return this.<String>value(APPROVAL_STATUS).orElse("NONE");
    }

    /**
     * 获取人工反馈
     */
    public String humanFeedback() {
        return this.<String>value(HUMAN_FEEDBACK).orElse("");
    }

    /**
     * 获取触发中断的节点名
     */
    public String interruptedNode() {
        return this.<String>value(INTERRUPTED_NODE).orElse("");
    }

    /**
     * 获取中断上下文 JSON
     */
    public String interruptContext() {
        return this.<String>value(INTERRUPT_CONTEXT).orElse("");
    }

    // ========== 长期记忆便捷访问方法 ==========

    /**
     * 获取当前用户 ID
     */
    public Long userId() {
        return this.<Long>value(USER_ID).orElse(0L);
    }

    /**
     * 获取当前租户 ID
     */
    public Long tenantId() {
        return this.<Long>value(TENANT_ID).orElse(0L);
    }
}
