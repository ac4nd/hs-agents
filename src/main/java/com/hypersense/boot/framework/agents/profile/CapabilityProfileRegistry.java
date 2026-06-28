package com.hypersense.boot.framework.agents.profile;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hypersense.boot.framework.agents.service.AgentProfileService;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * CapabilityProfile 注册表：包装 Caffeine 缓存（30 分钟 TTL，与 session 同生命周期）。
 * DB 配置变更时通过 invalidate() 清缓存。
 */
@Component
public class CapabilityProfileRegistry {

    private final AgentProfileService service;
    private final Cache<String, CapabilityProfile> cache;

    public CapabilityProfileRegistry(AgentProfileService service) {
        this.service = service;
        this.cache = Caffeine.newBuilder()
                .expireAfterAccess(Duration.ofMinutes(30))
                .maximumSize(64)
                .build();
    }

    public CapabilityProfile get(String profileId) {
        return cache.get(profileId, k -> service.loadProfile(k));
    }

    public void invalidate(String profileId) {
        cache.invalidate(profileId);
    }

    public void invalidateAll() {
        cache.invalidateAll();
    }
}
