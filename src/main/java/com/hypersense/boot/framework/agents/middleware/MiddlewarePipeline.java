package com.hypersense.boot.framework.agents.middleware;

import com.hypersense.boot.framework.agents.exception.HitlInterruptedException;
import com.hypersense.boot.framework.agents.model.DeepAgentState;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 中间件管道
 * <p>
 * 管理有序的 {@link AgentMiddleware} 列表，提供节点包装能力。
 * 在 {@link com.hypersense.boot.framework.agents.GodlikeAgent.Builder#buildGraph()} 中
 * 通过 {@link #wrap(String, NodeAction)} 包装每个节点。
 * </p>
 *
 * <h3>执行顺序：</h3>
 * <pre>
 * before: M1 → M2 → M3 → [node] → after: M3 → M2 → M1
 * </pre>
 * before 按注册顺序执行，after 按逆序执行（类似洋葱模型）。
 *
 * @author Claude
 * @since 2026/5/22
 */
@Slf4j
public class MiddlewarePipeline {

    private final List<AgentMiddleware> middlewares;

    public MiddlewarePipeline() {
        this.middlewares = new ArrayList<>();
    }

    public MiddlewarePipeline(List<AgentMiddleware> middlewares) {
        this.middlewares = new ArrayList<>(middlewares);
    }

    /**
     * 添加中间件（按添加顺序执行 before，逆序执行 after）
     */
    public void add(AgentMiddleware middleware) {
        middlewares.add(middleware);
        log.debug("MiddlewarePipeline: 注册中间件 [{}]", middleware.name());
    }

    /**
     * 是否为空（无中间件时跳过包装，零开销）
     */
    public boolean isEmpty() {
        return middlewares.isEmpty();
    }

    /**
     * 包装 NodeAction，注入中间件逻辑
     * <p>
     * 无中间件时直接返回原始 NodeAction，避免不必要的 lambda 包装。
     * </p>
     *
     * @param nodeName 节点名称
     * @param delegate 原始 NodeAction
     * @return 包装后的 NodeAction
     */
    public NodeAction<DeepAgentState> wrap(String nodeName, NodeAction<DeepAgentState> delegate) {
        if (middlewares.isEmpty()) {
            return delegate;
        }

        return state -> {
            // Before 钩子：按注册顺序
            executeBefore(nodeName, state);

            // 执行节点
            Map<String, Object> output = delegate.apply(state);

            // After 钩子：按逆序
            return executeAfter(nodeName, state, output);
        };
    }

    /**
     * 获取已注册中间件列表（不可变）
     */
    public List<AgentMiddleware> getMiddlewares() {
        return Collections.unmodifiableList(middlewares);
    }

    /**
     * 替换指定位置的中间件
     */
    public void replace(int index, AgentMiddleware middleware) {
        middlewares.set(index, middleware);
    }

    // ========== 内部方法 ==========

    private void executeBefore(String nodeName, DeepAgentState state) {
        for (AgentMiddleware mw : middlewares) {
            try {
                mw.before(nodeName, state);
            } catch (HitlInterruptedException e) {
                // HITL 中断异常必须穿透，不被吞掉
                throw e;
            } catch (Exception e) {
                log.warn("MiddlewarePipeline: [{}] before 钩子异常, node={}, 忽略",
                        mw.name(), nodeName, e);
            }
        }
    }

    private Map<String, Object> executeAfter(String nodeName, DeepAgentState state, Map<String, Object> output) {
        // 逆序执行 after（洋葱模型）
        for (int i = middlewares.size() - 1; i >= 0; i--) {
            AgentMiddleware mw = middlewares.get(i);
            try {
                output = mw.after(nodeName, state, output);
            } catch (HitlInterruptedException e) {
                // HITL 中断异常必须穿透
                throw e;
            } catch (Exception e) {
                log.warn("MiddlewarePipeline: [{}] after 钩子异常, node={}, 忽略",
                        mw.name(), nodeName, e);
            }
        }
        return output;
    }
}
