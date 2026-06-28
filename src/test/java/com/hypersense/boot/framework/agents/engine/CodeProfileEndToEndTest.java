package com.hypersense.boot.framework.agents.engine;

import com.hypersense.boot.framework.agents.profile.*;
import com.hypersense.boot.framework.agents.profile.impl.CodeProfile;
import com.hypersense.boot.framework.agents.profile.impl.TddPhase;
import com.hypersense.boot.framework.agents.profile.impl.TddPhaseManager;
import com.hypersense.boot.framework.agents.profile.lint.SymbolRegistry;
import com.hypersense.boot.framework.agents.tool.SandboxExecutor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 端到端：模拟 code-profile 完整 TDD 链路（不调真实 LLM/sandbox）。
 *
 * <p>标 {@code @Tag("integration")} — 需要 PostgreSQL + code profile DB seed
 * （{@code sql/postgresql/sys_agent_profile_code_c.sql}）。</p>
 *
 * <ol>
 *   <li>从 DB 加载 code profile（含 6 工具 + 4 lintRules + TDD 策略）</li>
 *   <li>TddPhaseManager 状态机走 READ → TEST → HITL → IMPL → EXEC → LINT → DONE</li>
 *   <li>验证 3 次 lint 失败触发 HITL</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
class CodeProfileEndToEndTest {

    @Autowired private CapabilityProfileRegistry registry;
    @Autowired private TddPhaseManager tddPhaseManager;
    @Autowired private SymbolRegistry symbolRegistry;
    @Autowired private SandboxExecutor sandbox;

    @Test
    void shouldLoadCodeProfileWithFullConfig() {
        CapabilityProfile profile = registry.get("code");
        assertEquals("code", profile.id());
        assertEquals(PlanStrategy.TDD, profile.planStrategy());
        assertTrue(profile.allowedTools().contains("package_lookup"));
        assertTrue(profile.allowedTools().contains("file_write_chunk"));
        assertTrue(profile.systemPrompt(ProfileContext.minimal("s1", "test"))
                .contains("SOLID"));
    }

    @Test
    void shouldWalkThroughTddStateMachineForQuickSort() {
        String sessionId = "qs-test";
        tddPhaseManager.clear(sessionId);
        symbolRegistry.clearSession(sessionId);

        // Phase READ
        assertEquals(TddPhase.READ, tddPhaseManager.current(sessionId));
        tddPhaseManager.transition(sessionId, TddPhase.TEST);

        // Phase TEST
        assertEquals(TddPhase.TEST, tddPhaseManager.current(sessionId));
        tddPhaseManager.transition(sessionId, TddPhase.TEST_HITL);

        // Phase TEST_HITL → 等待审批
        assertEquals(TddPhase.TEST_HITL, tddPhaseManager.current(sessionId));
        tddPhaseManager.approveHITL(sessionId);
        tddPhaseManager.transition(sessionId, TddPhase.IMPL);

        // Phase IMPL → EXEC → LINT
        tddPhaseManager.transition(sessionId, TddPhase.EXEC);
        tddPhaseManager.transition(sessionId, TddPhase.LINT);
        tddPhaseManager.transition(sessionId, TddPhase.DONE);

        assertEquals(TddPhase.DONE, tddPhaseManager.current(sessionId));
        assertTrue(tddPhaseManager.isTerminal(sessionId));
    }

    @Test
    void shouldHaltAfterThreeLintFailures() {
        String sessionId = "qs-retry";
        tddPhaseManager.clear(sessionId);
        // 快速走到 LINT
        tddPhaseManager.transition(sessionId, TddPhase.TEST);
        tddPhaseManager.transition(sessionId, TddPhase.TEST_HITL);
        tddPhaseManager.approveHITL(sessionId);
        tddPhaseManager.transition(sessionId, TddPhase.IMPL);
        tddPhaseManager.transition(sessionId, TddPhase.EXEC);
        tddPhaseManager.transition(sessionId, TddPhase.LINT);

        // 3 次 lint 失败
        tddPhaseManager.failLint(sessionId);
        tddPhaseManager.transition(sessionId, TddPhase.EXEC);
        tddPhaseManager.transition(sessionId, TddPhase.LINT);
        tddPhaseManager.failLint(sessionId);
        tddPhaseManager.transition(sessionId, TddPhase.EXEC);
        tddPhaseManager.transition(sessionId, TddPhase.LINT);
        tddPhaseManager.failLint(sessionId);

        assertTrue(tddPhaseManager.shouldInterruptForHITL(sessionId));
    }
}
