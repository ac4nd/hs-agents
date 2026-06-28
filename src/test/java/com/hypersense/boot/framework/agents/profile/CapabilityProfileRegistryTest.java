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
        when(service.loadProfile("design")).thenReturn(profile);

        CapabilityProfile result = registry.get("design");
        assertSame(profile, result);
        verify(service, times(1)).loadProfile("design");
    }

    @Test
    void shouldReturnCachedOnSecondCall() {
        CapabilityProfile profile = new StubDesignProfile(
                "design", "设计", "tpl", java.util.List.of("file_render"),
                PlanStrategy.OUTLINE_DEMO, null, HitlPolicy.defaultPolicy());
        when(service.loadProfile("design")).thenReturn(profile);

        registry.get("design");
        registry.get("design");

        verify(service, times(1)).loadProfile("design");
    }

    @Test
    void shouldThrowWhenProfileNotFound() {
        when(service.loadProfile("nonexistent"))
                .thenThrow(new ProfileNotFoundException("nonexistent"));

        assertThrows(ProfileNotFoundException.class, () -> registry.get("nonexistent"));
    }

    @Test
    void shouldEvictOnInvalidate() {
        CapabilityProfile profile = new StubDesignProfile(
                "design", "设计", "tpl", java.util.List.of("file_render"),
                PlanStrategy.OUTLINE_DEMO, null, HitlPolicy.defaultPolicy());
        when(service.loadProfile("design")).thenReturn(profile);

        registry.get("design");
        registry.invalidate("design");
        registry.get("design");

        verify(service, times(2)).loadProfile("design");
    }
}
