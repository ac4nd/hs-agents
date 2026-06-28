package com.hypersense.boot.framework.agents.profile.lint;

import com.hypersense.boot.framework.agents.profile.LintRule;
import java.util.regex.Pattern;

/**
 * 反 slop：检测 Lorem Ipsum / TODO / 单独 ... 等占位文本。
 */
public class NoPlaceholderRule implements LintRule {

    private static final Pattern PLACEHOLDER = Pattern.compile(
            "(?i)(lorem\\s+ipsum|todo\\b|tbd\\b|待补内容|" +
            ">\\.\\.\\.<|>\\.\\.\\.&nbsp;|^\\.\\.\\.$|<p>\\.\\.\\.</p>)"
    );

    @Override public String id() { return "no_placeholder"; }
    @Override public String description() { return "禁止 Lorem Ipsum / TODO / 单独省略号占位（用真实内容或诚实 placeholder）"; }

    @Override
    public String check(String input) {
        if (input == null || input.isEmpty()) return null;
        if (PLACEHOLDER.matcher(input).find()) {
            return "检测到占位文本（Lorem/TODO/...）。请替换为真实内容，或使用诚实 placeholder 标注「图待补」。";
        }
        return null;
    }
}
