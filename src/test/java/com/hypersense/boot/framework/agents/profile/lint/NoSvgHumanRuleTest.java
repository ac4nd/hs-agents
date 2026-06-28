package com.hypersense.boot.framework.agents.profile.lint;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NoSvgHumanRuleTest {

    private final NoSvgHumanRule rule = new NoSvgHumanRule();

    @Test
    void shouldPassGeometricSvg() {
        String svg = "<svg><circle cx=\"10\" cy=\"10\" r=\"5\"/></svg>";
        assertNull(rule.check(svg));
    }

    @Test
    void shouldCatchHumanFacePath() {
        String html = "<svg><path d=\"M50,50 Q60,40 70,50\"/><circle cx=\"55\" cy=\"55\" r=\"2\"/></svg>";
        assertNotNull(rule.check(html));
    }

    @Test
    void shouldCatchFaceKeywords() {
        String html = "<svg class=\"face\"><g id=\"eyes\"><circle/></g></svg>";
        assertNotNull(rule.check(html));
    }
}
