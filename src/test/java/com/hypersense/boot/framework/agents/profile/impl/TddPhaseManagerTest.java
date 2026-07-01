// src/test/java/com/hypersense/boot/framework/agents/profile/impl/TddPhaseManagerTest.java
package com.hypersense.boot.framework.agents.profile.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TddPhaseManagerTest {

    @Test
    void shouldStartAtRead() {
        TddPhaseManager m = new TddPhaseManager();
        assertEquals(TddPhase.READ, m.current());
    }

    @Test
    void shouldTransitionThroughHappyPath() {
        TddPhaseManager m = new TddPhaseManager();
        m.transition(TddPhase.TEST);
        m.transition(TddPhase.TEST_HITL);
        m.approveHITL();
        m.transition(TddPhase.IMPL);
        m.transition(TddPhase.EXEC);
        m.transition(TddPhase.LINT);
        m.transition(TddPhase.DONE);
        assertEquals(TddPhase.DONE, m.current());
        assertTrue(m.isTerminal());
    }

    @Test
    void shouldRetryImplOnLintFailureWithinBudget() {
        TddPhaseManager m = new TddPhaseManager();
        moveToLint(m);

        m.failLint(); // 1st fail → 回 IMPL
        assertEquals(TddPhase.IMPL, m.current());
        assertEquals(1, m.lintFailures());

        m.transition(TddPhase.EXEC);
        m.transition(TddPhase.LINT);
        m.failLint(); // 2nd fail
        assertEquals(TddPhase.IMPL, m.current());
        assertEquals(2, m.lintFailures());

        m.transition(TddPhase.EXEC);
        m.transition(TddPhase.LINT);
        m.failLint(); // 3rd fail → 触发 HITL
        assertEquals(3, m.lintFailures());
        assertTrue(m.shouldInterruptForHITL());
    }

    /**
     * P1#5：第 3 次失败时 phase 必须停在 LINT 等待 HITL，
     * 不再自动切回 IMPL（否则 retry 预算耗不尽，HITL 永远触发不了）。
     */
    @Test
    void shouldHaltAtLintOnThirdFailure() {
        TddPhaseManager m = new TddPhaseManager();
        moveToLint(m);

        m.failLint();
        m.transition(TddPhase.EXEC);
        m.transition(TddPhase.LINT);
        m.failLint();
        m.transition(TddPhase.EXEC);
        m.transition(TddPhase.LINT);

        m.failLint(); // 第 3 次失败
        assertEquals(TddPhase.LINT, m.current(), "第 3 次失败后必须停在 LINT 等待 HITL");
        assertTrue(m.shouldInterruptForHITL());
    }

    @Test
    void shouldRejectInvalidTransitions() {
        TddPhaseManager m = new TddPhaseManager();
        assertThrows(IllegalStateException.class, () -> m.transition(TddPhase.IMPL));
    }

    @Test
    void shouldRequireApprovalBeforeImpl() {
        TddPhaseManager m = new TddPhaseManager();
        m.transition(TddPhase.TEST);
        m.transition(TddPhase.TEST_HITL);
        assertThrows(IllegalStateException.class, () -> m.transition(TddPhase.IMPL));
        m.approveHITL();
        m.transition(TddPhase.IMPL);
        assertEquals(TddPhase.IMPL, m.current());
    }

    private void moveToLint(TddPhaseManager m) {
        m.transition(TddPhase.TEST);
        m.transition(TddPhase.TEST_HITL);
        m.approveHITL();
        m.transition(TddPhase.IMPL);
        m.transition(TddPhase.EXEC);
        m.transition(TddPhase.LINT);
    }
}
