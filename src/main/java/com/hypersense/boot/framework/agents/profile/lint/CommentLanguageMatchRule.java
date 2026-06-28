package com.hypersense.boot.framework.agents.profile.lint;

import com.hypersense.boot.framework.agents.profile.LintRule;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lint：检测同一文件内注释语言不一致。
 *
 * 抓取所有注释（# / // / /* *\/），按主流语言分组：
 * - 中（含 CJK 字符）
 * - 英（仅 ASCII 字母）
 * 若同时出现两组 → 不通过。
 *
 * 单行尾注释数量 < 2 时跳过（避免样本太少误判）。
 */
public class CommentLanguageMatchRule implements LintRule {

    private static final Pattern COMMENT_LINE = Pattern.compile(
            "(?m)^\\s*(#|//|/\\*)\\s*(.+?)\\s*(\\*/)?$"
    );

    private static final Pattern CJK = Pattern.compile(
            "[\\u4e00-\\u9fff\\u3040-\\u30ff\\uac00-\\ud7af]"
    );

    private static final int MIN_COMMENTS_TO_CHECK = 2;

    @Override public String id() { return "comment_language_match"; }
    @Override public String description() { return "同一文件内注释语言必须一致（中文/英文不混用）"; }

    @Override
    public String check(String input) {
        if (input == null || input.isEmpty()) return null;

        Matcher m = COMMENT_LINE.matcher(input);
        boolean hasCjk = false;
        boolean hasAsciiOnly = false;
        int count = 0;

        while (m.find()) {
            String text = m.group(2);
            if (text == null || text.isBlank()) continue;
            count++;
            if (CJK.matcher(text).find()) {
                hasCjk = true;
            } else if (text.matches("^[a-zA-Z].*")) {
                hasAsciiOnly = true;
            }
        }

        if (count < MIN_COMMENTS_TO_CHECK) return null;
        if (hasCjk && hasAsciiOnly) {
            return String.format("检测到注释语言混用（中文与英文并存，共 %d 条注释）。请统一为同一语言。", count);
        }
        return null;
    }
}
