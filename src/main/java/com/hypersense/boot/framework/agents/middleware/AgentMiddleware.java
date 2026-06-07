package com.hypersense.boot.framework.agents.middleware;

import com.hypersense.boot.framework.agents.model.DeepAgentState;

import java.util.Map;

/**
 * Agent 中间件接口
 * <p>
 * 在图节点执行前后插入横切逻辑（日志、上下文压缩、大输出卸载等）。
 * 通过 {@link MiddlewarePipeline} 包装 {@link org.bsc.langgraph4j.action.NodeAction} 实现。
 * </p>
 *
 * <h3>生命周期：</h3>
 * <pre>
 * before(nodeName, state) → node.apply(state) → after(nodeName, state, output) → merge
 * </pre>
 *
 * <h3>约定：</h3>
 * <ul>
 *   <li>before() 可直接修改 state（AgentState 继承 HashMap）</li>
 *   <li>after() 可修改 output 返回新的 Map，框架将合并修改后的 output</li>
 *   <li>after() 也可直接修改 state（如压缩 MESSAGES）</li>
 * </ul>
 *
 * @author Claude
 * @since 2026/5/22
 */
public interface AgentMiddleware {

    /**
     * 中间件名称（用于日志和调试）
     */
    String name();

    /**
     * 节点执行前回调
     * <p>
     * 可用于：日志记录、状态快照、前置校验、状态预处理。
     * </p>
     *
     * @param nodeName 节点名称（如 "plan", "execute", "tool"）
     * @param state    当前状态（可变，可直接修改）
     */
    default void before(String nodeName, DeepAgentState state) {
        // 默认空实现
    }

    /**
     * 节点执行后回调
     * <p>
     * 可用于：输出转换、消息压缩、大输出卸载、后置日志。
     * </p>
     *
     * @param nodeName 节点名称
     * @param state    当前状态（节点执行后的状态，可修改）
     * @param output   节点返回的状态更新（可修改后返回新 Map）
     * @return 最终的状态更新（可能被修改）
     */
    default Map<String, Object> after(String nodeName, DeepAgentState state, Map<String, Object> output) {
        return output;
    }
}
