// src/main/java/com/hypersense/boot/framework/agents/profile/HitlPolicy.java
package com.hypersense.boot.framework.agents.profile;

import java.util.List;

/**
 * HITL（Human-in-the-Loop）触发策略。
 * 描述在哪些 phase 强制中断等待用户审批。
 */
public record HitlPolicy(
        boolean enableInterrupt,
        List<String> interruptPhases,
        int maxLintRetriesBeforeInterrupt,
        int maxToolViolationsBeforeInterrupt
) {
    public static HitlPolicy defaultPolicy() {
        return new HitlPolicy(true, List.of(), 3, 3);
    }

    public static HitlPolicy disabled() {
        return new HitlPolicy(false, List.of(), 0, 999);
    }

    public boolean shouldInterrupt(String phase) {
        return enableInterrupt && interruptPhases != null && interruptPhases.contains(phase);
    }
}
