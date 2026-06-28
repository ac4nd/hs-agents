package com.hypersense.boot.framework.agents.profile.lint;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NoPlaceholderRuleTest {

    private final NoPlaceholderRule rule = new NoPlaceholderRule();

    @Test
    void shouldPassRealContent() {
        assertNull(rule.check("<p>2026 世界杯第 3 比赛日</p>"));
    }

    @Test
    void shouldCatchLoremIpsum() {
        assertNotNull(rule.check("<p>Lorem ipsum dolor sit amet</p>"));
    }

    @Test
    void shouldCatchTodo() {
        assertNotNull(rule.check("<div>TODO: 待补内容</div>"));
    }

    @Test
    void shouldCatchDotsPlaceholder() {
        assertNotNull(rule.check("<p>...</p>"));
    }
}
