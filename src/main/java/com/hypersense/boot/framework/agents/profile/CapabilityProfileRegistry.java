package com.hypersense.boot.framework.agents.profile;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hypersense.boot.framework.agents.service.AgentProfileService;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

/**
 * CapabilityProfile 注册表：包装 Caffeine 缓存（30 分钟 TTL，与 session 同生命周期）。
 * DB 配置变更时通过 invalidate() 清缓存。
 *
 * <h3>Plan C P0#4：缓存键含 sessionId</h3>
 * <p>CodeProfile 持有 per-session 状态（sessionId / SymbolRegistry 引用 / lint 规则实例），
 * 单纯按 profileId 缓存会导致第二个 session 拿到第一个 session 的 CodeProfile，
 * SymbolRegistry 校验错乱。缓存键改为 {@code profileId + "::" + sessionId}（sessionId 为 null 时退化为 profileId）。</p>
 *
 * <p>Hint（language/sourceFile/testFile）影响 CodeProfile 构造，因此缓存键需包含 hints 摘要。
 * 当前实现：hints 不同则视为不同 cache entry。简单稳定，避免 hint 漂移导致 lint 误判。</p>
 */
@Component
public class CapabilityProfileRegistry {

    private final AgentProfileService service;
    private final Cache<String, CapabilityProfile> cache;

    public CapabilityProfileRegistry(AgentProfileService service) {
        this.service = service;
        this.cache = Caffeine.newBuilder()
                .expireAfterAccess(Duration.ofMinutes(30))
                .maximumSize(256)
                .build();
    }

    /** 旧签名兼容：sessionId=null、hints=null（仅适用于无 per-session 状态的 profile）。 */
    public CapabilityProfile get(String profileId) {
        return get(profileId, null, null);
    }

    /** 带 sessionId 的查询（hints=null）。 */
    public CapabilityProfile get(String profileId, String sessionId) {
        return get(profileId, sessionId, null);
    }

    /**
     * 完整签名：profileId + sessionId + hints 三元组唯一定位 profile 实例。
     * <p>三者组合作为 cache key，确保不同 session / 不同 hints 拿到独立的 CodeProfile。</p>
     */
    public CapabilityProfile get(String profileId, String sessionId, Map<String, Object> hints) {
        String key = cacheKey(profileId, sessionId, hints);
        return cache.get(key, k -> service.loadProfile(profileId, sessionId, hints));
    }

    /**
     * 失效指定 profileId 的所有缓存条目（含所有 sessionId / hints 变体）。
     * <p>P0#4：原 {@code cache.invalidate(profileId)} 只移除精确匹配的 key，
     * 改为前缀匹配清除 {@code profileId::xxx} 所有变体。</p>
     */
    public void invalidate(String profileId) {
        String prefix = profileId + "::";
        // 遍历缓存 key 集合，前缀匹配清除所有 session/hints 变体 + 精确匹配（无 sessionId 路径）
        cache.asMap().keySet().removeIf(k -> k.equals(profileId) || k.startsWith(prefix));
    }

    public void invalidateAll() {
        cache.invalidateAll();
    }

    /** 组合缓存键：profileId [+ "::" + sessionId] [+ "#" + hintsHash]。 */
    private static String cacheKey(String profileId, String sessionId, Map<String, Object> hints) {
        StringBuilder sb = new StringBuilder(profileId);
        if (sessionId != null && !sessionId.isBlank()) {
            sb.append("::").append(sessionId);
        }
        if (hints != null && !hints.isEmpty()) {
            sb.append("#").append(hints.hashCode());
        }
        return sb.toString();
    }
}
