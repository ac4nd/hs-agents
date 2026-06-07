package com.hypersense.boot.framework.agents.engine;

import com.hypersense.boot.framework.agents.model.AgentEvent;

import java.util.function.Consumer;

/**
 * 子 Agent 事件总线（线程本地传播）
 * <p>
 * 通过 {@link ThreadLocal} 在父 Agent 图执行线程上持有事件消费者，
 * 子 Agent 执行器通过闭包捕获传播到自身线程，实现子 Agent 事件向上冒泡。
 * </p>
 *
 * <h3>传播链路：</h3>
 * <pre>
 * AgentServiceImpl (图执行线程)
 *   → SubAgentEventBus.set(consumer)     ← 设置消费者
 *   → graph.stream()
 *     → DelegateNode.apply()             ← 同一图线程读取
 *       → SubAgentExecutor.execute()
 *         → 捕获 consumer，传给 CompletableFuture
 *           → SubAgentEventBus.set(wrapped)  ← 子 Agent 线程上设置
 *             → subAgent.streamAndReturn()   ← 子 Agent 事件冒泡
 * </pre>
 *
 * <h3>嵌套子 Agent：</h3>
 * <p>
 * 外层子 Agent 设置 wrappedConsumer 到线程本地，内层子 Agent 捕获后再次包装，
 * 事件携带嵌套层级信息（如 [agent-a > agent-b]）自然冒泡到顶层。
 * </p>
 *
 * @author Claude
 * @since 2026/5/23
 */
public final class SubAgentEventBus {

    private SubAgentEventBus() {
    }

    private static final ThreadLocal<Consumer<AgentEvent>> CONSUMER = new ThreadLocal<>();

    /**
     * 设置当前线程的事件消费者
     *
     * @param consumer 事件消费者（null 等效于 {@link #remove()}）
     */
    public static void set(Consumer<AgentEvent> consumer) {
        if (consumer != null) {
            CONSUMER.set(consumer);
        } else {
            CONSUMER.remove();
        }
    }

    /**
     * 获取当前线程的事件消费者
     *
     * @return 消费者实例，不存在时返回 null
     */
    public static Consumer<AgentEvent> get() {
        return CONSUMER.get();
    }

    /**
     * 是否存在活跃的事件消费者
     */
    public static boolean hasConsumer() {
        return CONSUMER.get() != null;
    }

    /**
     * 清除当前线程的事件消费者
     */
    public static void remove() {
        CONSUMER.remove();
    }
}
