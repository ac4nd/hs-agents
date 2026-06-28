package com.hypersense.boot.framework.agents.profile.lint;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BrandColorDriftRuleTest {

    private final BrandColorDriftRule rule = new BrandColorDriftRule("#07c160", 10.0);

    @Test
    void shouldPassExactBrandColor() {
        assertNull(rule.check("<div style=\"color:#07c160\">x</div>"));
    }

    @Test
    void shouldPassColorWithinTolerance() {
        assertNull(rule.check("<div style=\"background:#08c160\">x</div>"));
    }

    @Test
    void shouldCatchDriftColor() {
        assertNotNull(rule.check("<div style=\"background:#dc2626\">x</div>"));
    }

    @Test
    void shouldIgnoreWhenNoBrandColorConfigured() {
        BrandColorDriftRule noBrand = new BrandColorDriftRule(null, 10.0);
        assertNull(noBrand.check("<div style=\"color:#dc2626\">x</div>"));
    }
}
