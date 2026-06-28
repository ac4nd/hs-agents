package com.hypersense.boot.framework.agents.profile.lint;

import com.hypersense.boot.framework.agents.profile.LintRule;
import java.util.regex.Pattern;

/**
 * 反 slop：检测紫渐变（AI 通用科技感公式）。
 * 仅在渐变中出现紫/靛色系视为违规；品牌纯色紫（如 Linear）允许。
 */
public class NoPurpleGradientRule implements LintRule {

    // 渐变函数体 + 紫/靛 hex 名单（非捕获组）
    private static final Pattern GRADIENT_PURPLE = Pattern.compile(
            "(?:linear-gradient|radial-gradient|conic-gradient)\\s*\\([^)]*?" +
            "(?:#(?:7C3AED|8B5CF6|A855F7|9333EA|6366F1|4338CA|6D28D9)" +
            "|rgb\\s*\\(\\s*1(?:2[0-9]|3[0-9])\\s*,\\s*[5-9][0-9]\\s*,\\s*(?:1[0-9][0-9]|2[0-4][0-9])\\s*\\))",
            Pattern.CASE_INSENSITIVE
    );

    @Override public String id() { return "no_purple_gradient"; }
    @Override public String description() { return "禁止在 CSS 渐变中使用紫/靛色系（AI slop 标志）"; }

    @Override
    public String check(String input) {
        if (input == null || input.isEmpty()) return null;
        if (GRADIENT_PURPLE.matcher(input).find()) {
            return "检测到紫/靛色渐变，这是 AI slop 的典型模式。请改用品牌主色或单色背景。 (purple gradient)";
        }
        return null;
    }
}
