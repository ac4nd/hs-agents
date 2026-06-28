package com.hypersense.boot.framework.agents.profile.lint;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NoPurpleGradientRuleTest {

    private final NoPurpleGradientRule rule = new NoPurpleGradientRule();

    @Test
    void shouldPassCleanHtml() {
        assertNull(rule.check("<div style=\"color:#07c160\">hello</div>"));
    }

    @Test
    void shouldCatchLinearGradientPurple() {
        String html = "<div style=\"background:linear-gradient(135deg,#7C3AED,#A855F7)\">x</div>";
        String err = rule.check(html);
        assertNotNull(err);
        assertTrue(err.contains("purple"));
    }

    @Test
    void shouldCatchRadialGradientIndigo() {
        String html = "<div style=\"background:radial-gradient(#6366F1,#4338CA)\"></div>";
        assertNotNull(rule.check(html));
    }

    @Test
    void shouldNotFlagBrandPurpleUsage() {
        assertNull(rule.check("<div style=\"background:#7C3AED;color:#fff\">x</div>"));
    }
}
