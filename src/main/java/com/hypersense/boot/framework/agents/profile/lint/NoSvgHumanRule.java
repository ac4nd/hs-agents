package com.hypersense.boot.framework.agents.profile.lint;

import com.hypersense.boot.framework.agents.profile.LintRule;
import java.util.regex.Pattern;

/**
 * 反 slop：禁止用 SVG 手画人脸。
 *
 * <p>AI 手画五官的比例、对称性、贝塞尔曲线控制点都异常僵硬，是典型的 slop 信号。
 * 检测两种模式：</p>
 * <ol>
 *   <li>关键词命中：svg 内含 face/eyes/nose/mouth 等 id/class/文本；</li>
 *   <li>结构命中：同一 svg 内同时出现「贝塞尔曲线 path（d 以 M/Q/C 开头）」和
 *       「小半径 circle（r=1 或 2，典型眼睛画法）」，顺序不限。</li>
 * </ol>
 *
 * <p>spec 原始 FACE_PATH_PATTERN 要求「两个 circle 在前、path 在后」的严格顺序，
 * 但典型人脸绘制常是 path 在前（脸轮廓）+ 单 circle 在后（眼睛），会漏检。
 * 这里改用顺序无关的 lookahead 合取，更贴近真实 slop 形态。</p>
 */
public class NoSvgHumanRule implements LintRule {

    private static final Pattern FACE_KEYWORD = Pattern.compile(
            "(?i)\\b(face|eyes|nose|mouth|ear|eyebrow|hair|chin|cheek|lip)\\b"
    );

    /**
     * 在同一 svg 片段内，同时满足：(A) 含贝塞尔曲线 path；(B) 含小半径 circle（眼睛）。
     * 用 lookahead 合取实现顺序无关；外层 .*? 让两个特征可分布在 svg 任意位置。
     */
    private static final Pattern FACE_PATH_PATTERN = Pattern.compile(
            "(?is)<svg[^>]*>" +
            "(?=.*?<path[^>]*d=\"[MQC][^\"]+\"[^>]*/?>)" +     // 贝塞尔曲线（脸轮廓）
            "(?=.*?<circle[^>]*r=\"[12]\"[^>]*/?>)" +          // 小眼睛
            ".*?</svg>"
    );

    @Override public String id() { return "no_svg_human"; }
    @Override public String description() { return "禁止用 SVG 手画人脸（用真实人像图或抽象图标）"; }

    @Override
    public String check(String input) {
        if (input == null || input.isEmpty()) return null;
        if (!input.toLowerCase().contains("<svg")) return null;
        if (FACE_KEYWORD.matcher(input).find() || FACE_PATH_PATTERN.matcher(input).find()) {
            return "检测到 SVG 内疑似人脸绘制。AI 手画五官比例异常，请改用真实人像图或抽象图形。";
        }
        return null;
    }
}
