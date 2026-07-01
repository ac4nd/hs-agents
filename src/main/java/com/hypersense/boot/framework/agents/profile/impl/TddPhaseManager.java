// src/main/java/com/hypersense/boot/framework/agents/profile/impl/TddPhaseManager.java
package com.hypersense.boot.framework.agents.profile.impl;

import org.springframework.stereotype.Component;

/**
 * TDD 状态机管理器。
 * 单例 Component，但每个 session 用 sessionId 维护独立 phase（per-session map）。
 */
@Component
public class TddPhaseManager {

    private static final int MAX_LINT_RETRIES = 3;

    private final java.util.Map<String, TddPhase> sessionPhase = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, Integer> sessionLintFailures = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, Boolean> sessionApproved = new java.util.concurrent.ConcurrentHashMap<>();

    public TddPhase current(String sessionId) {
        return sessionPhase.getOrDefault(sessionId, TddPhase.READ);
    }

    /** 测试辅助：无 sessionId 重载 */
    public TddPhase current() {
        return current("__test__");
    }

    public void transition(String sessionId, TddPhase next) {
        TddPhase cur = current(sessionId);
        validateTransition(cur, next, sessionId);
        sessionPhase.put(sessionId, next);
    }

    public void transition(TddPhase next) {
        transition("__test__", next);
    }

    private void validateTransition(TddPhase from, TddPhase to, String sessionId) {
        if (to == TddPhase.IMPL && from != TddPhase.TEST_HITL && from != TddPhase.LINT) {
            throw new IllegalStateException("非法状态迁移：" + from + " → " + to);
        }
        if (to == TddPhase.IMPL && from == TddPhase.TEST_HITL
                && !Boolean.TRUE.equals(sessionApproved.get(sessionId))) {
            throw new IllegalStateException("TEST_HITL 未审批通过，禁止进入 IMPL");
        }
        java.util.Set<TddPhase> allowed = switch (from) {
            case READ -> java.util.Set.of(TddPhase.TEST);
            case TEST -> java.util.Set.of(TddPhase.TEST_HITL);
            case TEST_HITL -> java.util.Set.of(TddPhase.IMPL, TddPhase.ABORTED);
            case IMPL -> java.util.Set.of(TddPhase.EXEC);
            case EXEC -> java.util.Set.of(TddPhase.LINT);
            case LINT -> java.util.Set.of(TddPhase.DONE, TddPhase.IMPL);
            default -> java.util.Set.of();
        };
        if (!allowed.contains(to)) {
            throw new IllegalStateException("非法状态迁移：" + from + " → " + to);
        }
    }

    public void approveHITL(String sessionId) {
        sessionApproved.put(sessionId, true);
    }
    public void approveHITL() { approveHITL("__test__"); }

    /**
     * Plan C P1#5：lint 失败推进语义修正。
     * <p>第 1/2 次失败（n < MAX_LINT_RETRIES）→ phase 切回 IMPL 等待 retry；
     * 第 3 次（n == MAX_LINT_RETRIES）→ 保持 LINT，等待 HITL 审批。</p>
     * <p>原逻辑 {@code n <= MAX_LINT_RETRIES} 会让第 3 次失败仍切到 IMPL，导致 retry 预算
     * 永远耗不尽，TDD 链路无法触发 HITL 中断。</p>
     */
    public void failLint(String sessionId) {
        int n = sessionLintFailures.merge(sessionId, 1, Integer::sum);
        if (n < MAX_LINT_RETRIES) {
            sessionPhase.put(sessionId, TddPhase.IMPL);
        }
        // n >= MAX_LINT_RETRIES：保持 LINT，由 shouldInterruptForHITL 触发中断
    }
    public void failLint() { failLint("__test__"); }

    public int lintFailures(String sessionId) {
        return sessionLintFailures.getOrDefault(sessionId, 0);
    }
    public int lintFailures() { return lintFailures("__test__"); }

    public boolean shouldInterruptForHITL(String sessionId) {
        return lintFailures(sessionId) >= MAX_LINT_RETRIES;
    }
    public boolean shouldInterruptForHITL() {
        return shouldInterruptForHITL("__test__");
    }

    public boolean isTerminal(String sessionId) {
        TddPhase p = current(sessionId);
        return p == TddPhase.DONE || p == TddPhase.ABORTED;
    }
    public boolean isTerminal() { return isTerminal("__test__"); }

    public void clear(String sessionId) {
        sessionPhase.remove(sessionId);
        sessionLintFailures.remove(sessionId);
        sessionApproved.remove(sessionId);
    }
}
