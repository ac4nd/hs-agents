// src/main/java/com/hypersense/boot/framework/agents/profile/ProfileContext.java
package com.hypersense.boot.framework.agents.profile;

import java.util.Map;

/**
 * Profile 系统提示词渲染所需的会话上下文。
 * CapabilityProfile.systemPrompt(ProfileContext) 用此填充模板变量。
 */
public record ProfileContext(
        String sessionId,
        Long userId,
        Long tenantId,
        String userInput,
        Long designSystemId,
        Map<String, Object> hints
) {
    public static ProfileContext minimal(String sessionId, String userInput) {
        return new ProfileContext(sessionId, null, null, userInput, null, Map.of());
    }
}
