package com.hypersense.boot.framework.agents.profile.lint;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NoEmojiIconRuleTest {

    private final NoEmojiIconRule rule = new NoEmojiIconRule();

    @Test
    void shouldPassPlainText() {
        assertNull(rule.check("<div>注意事项</div>"));
    }

    @Test
    void shouldCatchEmojiInIconSlot() {
        String html = "<li><span>🚀</span>速度提升</li>";
        assertNotNull(rule.check(html));
    }

    @Test
    void shouldPassEmojiInBodyParagraph() {
        assertNull(rule.check("<p>用户说「这页面太花哨了 😅」</p>"));
    }
}
