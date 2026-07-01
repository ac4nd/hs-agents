package com.hypersense.boot.framework.agents.profile;

import com.hypersense.boot.framework.agents.profile.impl.StubDesignProfile;
import com.hypersense.boot.framework.agents.service.AgentProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CapabilityProfileRegistryTest {

    private AgentProfileService service;
    private CapabilityProfileRegistry registry;

    @BeforeEach
    void setUp() {
        service = Mockito.mock(AgentProfileService.class);
        registry = new CapabilityProfileRegistry(service);
    }

    @Test
    void shouldDelegateToServiceOnFirstCall() {
        CapabilityProfile profile = new StubDesignProfile(
                "design", "设计", "tpl", java.util.List.of("file_render"),
                PlanStrategy.OUTLINE_DEMO, null, HitlPolicy.defaultPolicy());
        when(service.loadProfile("design", null, null)).thenReturn(profile);

        CapabilityProfile result = registry.get("design");
        assertSame(profile, result);
        verify(service, times(1)).loadProfile("design", null, null);
    }

    @Test
    void shouldReturnCachedOnSecondCall() {
        CapabilityProfile profile = new StubDesignProfile(
                "design", "设计", "tpl", java.util.List.of("file_render"),
                PlanStrategy.OUTLINE_DEMO, null, HitlPolicy.defaultPolicy());
        when(service.loadProfile("design", null, null)).thenReturn(profile);

        registry.get("design");
        registry.get("design");

        verify(service, times(1)).loadProfile("design", null, null);
    }

    @Test
    void shouldThrowWhenProfileNotFound() {
        when(service.loadProfile("nonexistent", null, null))
                .thenThrow(new ProfileNotFoundException("nonexistent"));

        assertThrows(ProfileNotFoundException.class, () -> registry.get("nonexistent"));
    }

    @Test
    void shouldEvictOnInvalidate() {
        CapabilityProfile profile = new StubDesignProfile(
                "design", "设计", "tpl", java.util.List.of("file_render"),
                PlanStrategy.OUTLINE_DEMO, null, HitlPolicy.defaultPolicy());
        when(service.loadProfile("design", null, null)).thenReturn(profile);

        registry.get("design");
        registry.invalidate("design");
        registry.get("design");

        verify(service, times(2)).loadProfile("design", null, null);
    }

    /** P0#4：sessionId 不同应产生不同 cache entry，避免 CodeProfile 跨 session 共享。 */
    @Test
    void shouldCachePerSessionId() {
        CapabilityProfile p1 = new StubDesignProfile(
                "code", "代码", "tpl", java.util.List.of("file_write"),
                PlanStrategy.TDD, null, HitlPolicy.defaultPolicy());
        CapabilityProfile p2 = new StubDesignProfile(
                "code", "代码", "tpl", java.util.List.of("file_write"),
                PlanStrategy.TDD, null, HitlPolicy.defaultPolicy());
        when(service.loadProfile("code", "session-1", null)).thenReturn(p1);
        when(service.loadProfile("code", "session-2", null)).thenReturn(p2);

        assertSame(p1, registry.get("code", "session-1"));
        assertSame(p2, registry.get("code", "session-2"));
        // 同 session 第二次应命中缓存
        assertSame(p1, registry.get("code", "session-1"));

        verify(service, times(1)).loadProfile("code", "session-1", null);
        verify(service, times(1)).loadProfile("code", "session-2", null);
    }

    /** P0#4：invalidate(profileId) 应清除所有 session 变体（前缀匹配）。 */
    @Test
    void shouldEvictAllSessionVariantsOnInvalidate() {
        CapabilityProfile p = new StubDesignProfile(
                "code", "代码", "tpl", java.util.List.of("file_write"),
                PlanStrategy.TDD, null, HitlPolicy.defaultPolicy());
        when(service.loadProfile("code", "s1", null)).thenReturn(p);
        when(service.loadProfile("code", "s2", null)).thenReturn(p);

        registry.get("code", "s1");
        registry.get("code", "s2");
        registry.invalidate("code");
        registry.get("code", "s1");
        registry.get("code", "s2");

        // invalidate 后两个 session 都应重新调 service
        verify(service, times(2)).loadProfile("code", "s1", null);
        verify(service, times(2)).loadProfile("code", "s2", null);
    }
}
