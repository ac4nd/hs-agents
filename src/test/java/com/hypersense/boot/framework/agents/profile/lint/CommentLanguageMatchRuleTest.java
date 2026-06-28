package com.hypersense.boot.framework.agents.profile.lint;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CommentLanguageMatchRuleTest {

    private final CommentLanguageMatchRule rule = new CommentLanguageMatchRule();

    @Test
    void shouldPassWhenAllCommentsSameLanguage() {
        String code = """
                # 计算总和
                def sum(a, b):
                    return a + b  # 返回相加结果
                """;
        assertNull(rule.check(code));
    }

    @Test
    void shouldCatchMixedLanguageComments() {
        // 现有注释是中文，新注释是英文 → 不一致
        String code = """
                # 计算总和
                def sum(a, b):
                    # Calculate the sum of two numbers
                    return a + b
                """;
        assertNotNull(rule.check(code));
    }

    @Test
    void shouldPassWhenAllEnglishComments() {
        String code = """
                # calculate sum
                def sum(a, b):
                    return a + b  # English comment
                """;
        assertNull(rule.check(code));
    }

    @Test
    void shouldPassNoComments() {
        assertNull(rule.check("def sum(a, b):\n    return a + b\n"));
    }

    @Test
    void shouldHandleJsBlockComments() {
        String code = """
                // 中文注释
                function sum(a, b) {
                    /* another 中文 comment */
                    return a + b;
                }
                """;
        assertNull(rule.check(code));
    }
}
