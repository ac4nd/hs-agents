package com.hypersense.boot.framework.agents.profile.lint;

import com.hypersense.boot.framework.agents.profile.LintRule;
import java.util.regex.Pattern;

/**
 * 反 slop：检测 emoji-as-icon（AI 默认在每个 bullet 配 emoji）。
 * 仅在 li/i/span/button/div 等元素开头位置检测；段落正文 emoji 视为内容放过。
 *
 * <p>实现说明：Java 正则的 unicode 转义只接受正好 4 位十六进制，
 * 无法直接表达 U+1F300 以上的补充平面 emoji。这里改用 {@code codePointAt}
 * 按代码点范围判断，比代理对 regex 更可读、更可靠。</p>
 */
public class NoEmojiIconRule implements LintRule {

    // 捕获 icon-slot 容器开标签（<li ...>、<span ...> 等），用于定位 emoji 出现位置
    private static final Pattern ICON_SLOT_OPEN = Pattern.compile(
            "<(li|i|span|button|div)\\b[^>]*>",
            Pattern.CASE_INSENSITIVE
    );

    @Override public String id() { return "no_emoji_icon"; }
    @Override public String description() { return "禁止把 emoji 当作图标（应使用真实图标库或文字标签）"; }

    @Override
    public String check(String input) {
        if (input == null || input.isEmpty()) return null;
        // 遍历每个 icon-slot 开标签，检查其紧邻的下一个字符是否为 emoji
        java.util.regex.Matcher m = ICON_SLOT_OPEN.matcher(input);
        while (m.find()) {
            int afterTag = m.end();
            // 跳过空白
            while (afterTag < input.length() && Character.isWhitespace(input.charAt(afterTag))) {
                afterTag++;
            }
            if (afterTag >= input.length()) continue;
            int cp = input.codePointAt(afterTag);
            if (isEmoji(cp)) {
                return "检测到 emoji 充当图标。请替换为 SVG 图标库（如 Heroicons/Lucide）或纯文字标签。";
            }
        }
        return null;
    }

    /**
     * 判断代码点是否属于常见 emoji 范围。
     * 覆盖：杂项符号/象形文字（U+1F300-1F5FF）、表情（U+1F600-1F64F）、
     * 交通地图（U+1F680-1F6FF）、补充符号（U+1F900-1F9FF）、旗帜（U+1F1E0-1F1FF）、
     * Dingbats（U+2700-27BF）、杂项符号（U+2600-26FF）。
     */
    private static boolean isEmoji(int cp) {
        return (cp >= 0x1F300 && cp <= 0x1F5FF)
                || (cp >= 0x1F600 && cp <= 0x1F64F)
                || (cp >= 0x1F680 && cp <= 0x1F6FF)
                || (cp >= 0x1F900 && cp <= 0x1F9FF)
                || (cp >= 0x1FA70 && cp <= 0x1FAFF)
                || (cp >= 0x1F1E0 && cp <= 0x1F1FF)
                || (cp >= 0x2600 && cp <= 0x27BF);
    }
}
