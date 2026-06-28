package com.hypersense.boot.framework.agents.profile;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlanStrategyTest {

    @Test
    void shouldContainAllStrategiesFromSpec() {
        assertNotNull(PlanStrategy.OUTLINE_DEMO);
        assertNotNull(PlanStrategy.TDD);
        assertNotNull(PlanStrategy.DIVERGE_THEN_STRUCTURE);
        assertNotNull(PlanStrategy.OUTLINE_THEN_FILL);
        assertNotNull(PlanStrategy.LAYERED_LEARNING);
        assertNotNull(PlanStrategy.GENERIC);
    }

    @Test
    void shouldParseFromString() {
        assertEquals(PlanStrategy.TDD, PlanStrategy.fromString("TDD"));
        assertEquals(PlanStrategy.GENERIC, PlanStrategy.fromString("GENERIC"));
    }

    @Test
    void shouldFallbackToGenericForUnknownString() {
        assertEquals(PlanStrategy.GENERIC, PlanStrategy.fromString("UNKNOWN_STRATEGY"));
    }

    @Test
    void shouldHaveStringValue() {
        assertEquals("TDD", PlanStrategy.TDD.strategy());
    }
}
