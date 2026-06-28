package com.hypersense.boot.framework.agents.profile.lint;

import com.hypersense.boot.framework.agents.profile.LintRule;
import java.awt.Color;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 反 slop：禁止非中性色脱离品牌主色超过容差百分比。
 *
 * <p>AI 默认调色板常漂移到 Tailwind 红/蓝/紫（#dc2626、#3b82f6、#8b5cf6 等），
 * 与品牌主色冲突。本规则扫描输入中的 #RRGGBB，跳过黑白灰中性色，对其余颜色
 * 计算与品牌色的 RGB 欧氏距离（归一化到 0-100%），超容差即报错。</p>
 */
public class BrandColorDriftRule implements LintRule {

    private static final Pattern HEX = Pattern.compile("#([0-9A-Fa-f]{6})");

    /** 中性色阈值：max(r,g,b) - min(r,g,b) < 12 视为灰阶（含纯黑/纯白）。 */
    private static final int NEUTRAL_THRESHOLD = 12;

    private final Color brandColor;
    private final double tolerancePct;

    public BrandColorDriftRule(String brandHex, double tolerancePct) {
        this.brandColor = parseHex(brandHex);
        this.tolerancePct = tolerancePct;
    }

    @Override public String id() { return "brand_color_drift"; }
    @Override public String description() { return "禁止色彩脱离品牌主色超过 " + tolerancePct + "%"; }

    @Override
    public String check(String input) {
        if (brandColor == null || input == null) return null;
        Matcher m = HEX.matcher(input);
        // 注意：spec 原文用 int maxDrift 强转 double 会丢精度，这里改为 double 保留计算精度。
        double maxDrift = 0;
        String driftedHex = null;
        while (m.find()) {
            Color c = parseHex("#" + m.group(1));
            if (c == null) continue;
            if (isNeutral(c)) continue;
            double dist = rgbDistancePct(c, brandColor);
            if (dist > maxDrift) {
                maxDrift = dist;
                driftedHex = "#" + m.group(1);
            }
        }
        if (maxDrift > tolerancePct) {
            return String.format("色彩 %s 与品牌主色偏离 %.0f%%（容差 %.0f%%），请基于品牌色配色。",
                    driftedHex, maxDrift, tolerancePct);
        }
        return null;
    }

    private boolean isNeutral(Color c) {
        int r = c.getRed(), g = c.getGreen(), b = c.getBlue();
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        return (max - min) < NEUTRAL_THRESHOLD;
    }

    private double rgbDistancePct(Color a, Color b) {
        long dr = a.getRed() - b.getRed();
        long dg = a.getGreen() - b.getGreen();
        long db = a.getBlue() - b.getBlue();
        double dist = Math.sqrt(dr * dr + dg * dg + db * db);
        return dist / 4.4167; // sqrt(3*255^2) ≈ 441.67，转百分比
    }

    private static Color parseHex(String hex) {
        if (hex == null) return null;
        try {
            return Color.decode(hex);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
