package com.hypersense.boot.framework.agents.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具重试配置
 * <p>
 * 控制工具执行失败时的重试策略：指数退避、最大重试次数。
 * 默认关闭（enabled=false），启用后对所有工具生效。
 * </p>
 *
 * <h3>退避算法：</h3>
 * <pre>
 * delay = min(initialDelayMs × backoffMultiplier^(attempt-1), maxDelayMs)
 * </pre>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 默认配置（3次重试，1秒初始退避）
 * ToolRetryConfig config = ToolRetryConfig.defaults();
 *
 * // 自定义配置
 * ToolRetryConfig config = ToolRetryConfig.builder()
 *     .enabled(true)
 *     .maxAttempts(5)
 *     .initialDelayMs(500L)
 *     .backoffMultiplier(3.0)
 *     .build();
 * }</pre>
 *
 * @author Claude
 * @since 2026/5/23
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolRetryConfig {

    /** 是否启用工具重试（默认 false） */
    @Builder.Default
    private boolean enabled = false;

    /** 最大尝试次数（含首次调用），默认 3 */
    @Builder.Default
    private int maxAttempts = 3;

    /** 初始退避延迟（毫秒），默认 1000 */
    @Builder.Default
    private long initialDelayMs = 1000L;

    /** 最大退避延迟（毫秒），默认 30000 */
    @Builder.Default
    private long maxDelayMs = 30000L;

    /** 退避倍数，默认 2.0 */
    @Builder.Default
    private double backoffMultiplier = 2.0;

    /**
     * 计算第 N 次重试的退避延迟
     *
     * @param attempt 当前失败次数（从 1 开始）
     * @return 延迟毫秒数
     */
    public long calculateDelay(int attempt) {
        double delay = initialDelayMs * Math.pow(backoffMultiplier, attempt - 1);
        return (long) Math.min(delay, maxDelayMs);
    }

    /**
     * 关闭状态工厂方法
     */
    public static ToolRetryConfig disabled() {
        return new ToolRetryConfig();
    }

    /**
     * 默认启用状态工厂方法（3次重试，1秒初始退避，2倍增长）
     */
    public static ToolRetryConfig defaults() {
        return ToolRetryConfig.builder()
                .enabled(true)
                .maxAttempts(3)
                .initialDelayMs(1000L)
                .maxDelayMs(30000L)
                .backoffMultiplier(2.0)
                .build();
    }

    /**
     * 从 AgentProperties 内部类构建（Spring 路径）
     */
    public static ToolRetryConfig fromProperties(AgentProperties.ToolRetryConfig props) {
        if (props == null || !Boolean.TRUE.equals(props.getEnabled())) {
            return disabled();
        }
        return ToolRetryConfig.builder()
                .enabled(true)
                .maxAttempts(props.getMaxAttempts() != null ? props.getMaxAttempts() : 3)
                .initialDelayMs(props.getInitialDelayMs() != null ? props.getInitialDelayMs() : 1000L)
                .maxDelayMs(props.getMaxDelayMs() != null ? props.getMaxDelayMs() : 30000L)
                .backoffMultiplier(props.getBackoffMultiplier() != null ? props.getBackoffMultiplier() : 2.0)
                .build();
    }
}
