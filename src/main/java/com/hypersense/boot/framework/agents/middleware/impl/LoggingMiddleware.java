package com.hypersense.boot.framework.agents.middleware.impl;

import com.hypersense.boot.framework.agents.middleware.AgentMiddleware;
import com.hypersense.boot.framework.agents.model.DeepAgentState;
import dev.langchain4j.data.message.ChatMessage;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 结构化日志中间件
 * <p>
 * 统一记录每个节点的执行情况，替代各 Node 中分散的 log.info。
 * 提供：节点执行耗时、消息数量、当前迭代。
 * </p>
 *
 * @author Claude
 * @since 2026/5/22
 */
@Slf4j
public class LoggingMiddleware implements AgentMiddleware {

    /** 使用 ThreadLocal 存储计时（AgentState 不支持直接 put/get） */
    private final ThreadLocal<Long> timingHolder = new ThreadLocal<>();

    @Override
    public String name() {
        return "logging";
    }

    @Override
    public void before(String nodeName, DeepAgentState state) {
        timingHolder.set(System.currentTimeMillis());

        int messageCount = state.chatMessages().size();
        int todoCount = state.todos().size();
        String sessionId = state.sessionId();

        log.info("[Agent] → node={}, session={}, messages={}, todos={}",
                nodeName, abbreviate(sessionId), messageCount, todoCount);
    }

    @Override
    public Map<String, Object> after(String nodeName, DeepAgentState state, Map<String, Object> output) {
        Long startMs = timingHolder.get();
        timingHolder.remove();

        long elapsed = startMs != null ? System.currentTimeMillis() - startMs : -1;

        int messageCount = state.chatMessages().size();
        String strategy = state.executeStrategy();
        int iteration = state.iterationCount();

        log.info("[Agent] ← node={}, elapsed={}ms, messages={}, strategy={}, iteration={}",
                nodeName, elapsed, messageCount, strategy, iteration);

        if (log.isDebugEnabled() && output != null) {
            log.debug("[Agent] node={} output keys: {}", nodeName, output.keySet());
        }

        return output;
    }

    private String abbreviate(String s) {
        if (s == null || s.length() <= 8) return s;
        return s.substring(0, 8) + "...";
    }
}
