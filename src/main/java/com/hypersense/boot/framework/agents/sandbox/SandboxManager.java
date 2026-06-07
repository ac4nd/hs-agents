package com.hypersense.boot.framework.agents.sandbox;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hypersense.boot.framework.agents.config.AgentProperties;
import com.hypersense.boot.framework.agents.sandbox.factory.SandboxFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 沙箱生命周期管理器（Thread-scoped 模式）
 * <p>
 * 基于 Caffeine 缓存管理每个会话的沙箱实例：
 * <ul>
 *   <li>每个 sessionId 对应一个独立的 Sandbox 实例</li>
 *   <li>缓存过期时自动调用 {@link Sandbox#destroy()} 释放资源</li>
 *   <li>TTL 与 Agent 会话一致（agent.deep.session-ttl）</li>
 * </ul>
 * </p>
 *
 * @author Claude
 * @since 2026/5/20
 */
@Slf4j
@Component
public class SandboxManager {

    private final SandboxFactory sandboxFactory;
    private final Cache<String, Sandbox> sandboxCache;

    public SandboxManager(SandboxFactory sandboxFactory,
                          AgentProperties agentProperties) {
        this.sandboxFactory = sandboxFactory;

        long ttl = agentProperties.getDeep().getSessionTtl();
        this.sandboxCache = Caffeine.newBuilder()
                .expireAfterAccess(ttl, TimeUnit.SECONDS)
                .maximumSize(500)
                .removalListener((key, value, cause) -> {
                    if (value != null) {
                        log.info("SandboxManager: 沙箱回收 sessionId={}, cause={}", key, cause);
                        try {
                            ((Sandbox) value).destroy();
                        } catch (Exception e) {
                            log.warn("SandboxManager: 沙箱销毁异常 sessionId={}", key, e);
                        }
                    }
                })
                .build();

        log.info("SandboxManager: 初始化完成，TTL={}s", ttl);
    }

    /**
     * 获取或创建指定会话的沙箱实例
     * <p>
     * 同一 sessionId 只会创建一个 Sandbox 实例（Caffeine 原子操作保证）。
     * </p>
     *
     * @param sessionId 会话 ID
     * @return 该会话专属的沙箱实例
     */
    public Sandbox getOrCreate(String sessionId) {
        return sandboxCache.get(sessionId, id -> {
            log.info("SandboxManager: 创建新沙箱 sessionId={}", id);
            return sandboxFactory.create(id);
        });
    }

    /**
     * 主动销毁指定会话的沙箱（会话结束时调用）
     * <p>
     * 触发 Caffeine removalListener → sandbox.destroy()
     * </p>
     *
     * @param sessionId 会话 ID
     */
    public void destroy(String sessionId) {
        sandboxCache.invalidate(sessionId);
    }
}
