package com.hypersense.boot.system.constant;

/**
 * 品牌规范（brandSpec）默认值常量
 *
 * <p>v2.0 分层令牌结构：identity（主品牌色 / 强调色 / 中性色）、semantic（语义色）、
 * fonts（显示字体 / 正文字体 / 等宽字体 + 字号 / 字重 / 行高 / 字距等排版令牌）、
 * radius（圆角令牌）、logo。</p>
 *
 * <p>本类集中托管默认 brandSpec JSON 字符串，供：</p>
 * <ul>
 *     <li>{@code DesignSystemConfigServiceImpl} 在新增配置时兜底赋值</li>
 *     <li>{@code BrandSpecMigrator} 在迁移异常 / 空值时兜底</li>
 *     <li>SQL 种子数据保持与 Java 常量一致（手工同步）</li>
 * </ul>
 *
 * <p>不修改 DDL：brandSpec 在数据库中仍为 JSONB 字符串字段，仅扩展内部结构。</p>
 *
 * @author Claude
 * @since 2026/6/25
 */
public final class BrandSpecDefaults {

    private BrandSpecDefaults() {
    }

    /**
     * v2.0 默认 brandSpec JSON（主色 #1A56DB，强调色 #F97316）。
     * 用于新增配置 / 迁移兜底。
     */
    public static final String V2_DEFAULT = "{"
            + "\"version\":\"2.0\","
            + "\"colors\":{"
            + "\"identity\":{\"primary\":\"#1A56DB\",\"accent\":\"#F97316\",\"neutral\":\"#1F2937\"},"
            + "\"semantic\":{\"success\":\"#16A34A\",\"warning\":\"#F59E0B\",\"danger\":\"#DC2626\",\"info\":\"#0EA5E9\"}"
            + "},"
            + "\"fonts\":{"
            + "\"display\":\"Geist, Inter, system-ui, sans-serif\","
            + "\"body\":\"Geist, Inter, system-ui, sans-serif\","
            + "\"mono\":\"JetBrains Mono, ui-monospace, monospace\","
            + "\"baseSize\":16,"
            + "\"scaleRatio\":1.25,"
            + "\"weight\":{\"regular\":400,\"medium\":500,\"semibold\":600,\"bold\":700},"
            + "\"lineHeight\":{\"tight\":1.25,\"normal\":1.5,\"relaxed\":1.625},"
            + "\"tracking\":{\"tight\":\"-0.025em\",\"normal\":\"0\",\"wide\":\"0.025em\"}"
            + "},"
            + "\"radius\":{\"sm\":4,\"md\":6,\"lg\":8,\"xl\":12,\"2xl\":16},"
            + "\"logo\":\"\""
            + "}";

    /**
     * OpenAI 风格模板 brandSpec（primary=#10A37F，正文字体 Söhne）。
     */
    public static final String V2_OPENAI = "{"
            + "\"version\":\"2.0\","
            + "\"colors\":{"
            + "\"identity\":{\"primary\":\"#10A37F\",\"accent\":\"#F97316\",\"neutral\":\"#0D1B2A\"},"
            + "\"semantic\":{\"success\":\"#16A34A\",\"warning\":\"#F59E0B\",\"danger\":\"#DC2626\",\"info\":\"#0EA5E9\"}"
            + "},"
            + "\"fonts\":{"
            + "\"display\":\"Söhne, Inter, system-ui, sans-serif\","
            + "\"body\":\"Söhne, Inter, system-ui, sans-serif\","
            + "\"mono\":\"JetBrains Mono, ui-monospace, monospace\","
            + "\"baseSize\":16,"
            + "\"scaleRatio\":1.25,"
            + "\"weight\":{\"regular\":400,\"medium\":500,\"semibold\":600,\"bold\":700},"
            + "\"lineHeight\":{\"tight\":1.25,\"normal\":1.5,\"relaxed\":1.625},"
            + "\"tracking\":{\"tight\":\"-0.025em\",\"normal\":\"0\",\"wide\":\"0.025em\"}"
            + "},"
            + "\"radius\":{\"sm\":4,\"md\":6,\"lg\":8,\"xl\":12,\"2xl\":16},"
            + "\"logo\":\"https://example.com/openai.svg\""
            + "}";

    /**
     * Vercel 风格模板 brandSpec（primary=#000000，正文字体 Geist）。
     */
    public static final String V2_VERCEL = "{"
            + "\"version\":\"2.0\","
            + "\"colors\":{"
            + "\"identity\":{\"primary\":\"#000000\",\"accent\":\"#0070F3\",\"neutral\":\"#111111\"},"
            + "\"semantic\":{\"success\":\"#16A34A\",\"warning\":\"#F59E0B\",\"danger\":\"#DC2626\",\"info\":\"#0EA5E9\"}"
            + "},"
            + "\"fonts\":{"
            + "\"display\":\"Geist, Inter, system-ui, sans-serif\","
            + "\"body\":\"Geist, Inter, system-ui, sans-serif\","
            + "\"mono\":\"JetBrains Mono, ui-monospace, monospace\","
            + "\"baseSize\":16,"
            + "\"scaleRatio\":1.25,"
            + "\"weight\":{\"regular\":400,\"medium\":500,\"semibold\":600,\"bold\":700},"
            + "\"lineHeight\":{\"tight\":1.25,\"normal\":1.5,\"relaxed\":1.625},"
            + "\"tracking\":{\"tight\":\"-0.025em\",\"normal\":\"0\",\"wide\":\"0.025em\"}"
            + "},"
            + "\"radius\":{\"sm\":4,\"md\":6,\"lg\":8,\"xl\":12,\"2xl\":16},"
            + "\"logo\":\"https://example.com/vercel.svg\""
            + "}";

    /**
     * Linear 风格模板 brandSpec（primary=#5E6AD2，正文字体 Inter）。
     */
    public static final String V2_LINEAR = "{"
            + "\"version\":\"2.0\","
            + "\"colors\":{"
            + "\"identity\":{\"primary\":\"#5E6AD2\",\"accent\":\"#F97316\",\"neutral\":\"#1F2937\"},"
            + "\"semantic\":{\"success\":\"#16A34A\",\"warning\":\"#F59E0B\",\"danger\":\"#DC2626\",\"info\":\"#0EA5E9\"}"
            + "},"
            + "\"fonts\":{"
            + "\"display\":\"Inter, system-ui, sans-serif\","
            + "\"body\":\"Inter, system-ui, sans-serif\","
            + "\"mono\":\"JetBrains Mono, ui-monospace, monospace\","
            + "\"baseSize\":16,"
            + "\"scaleRatio\":1.25,"
            + "\"weight\":{\"regular\":400,\"medium\":500,\"semibold\":600,\"bold\":700},"
            + "\"lineHeight\":{\"tight\":1.25,\"normal\":1.5,\"relaxed\":1.625},"
            + "\"tracking\":{\"tight\":\"-0.025em\",\"normal\":\"0\",\"wide\":\"0.025em\"}"
            + "},"
            + "\"radius\":{\"sm\":4,\"md\":6,\"lg\":8,\"xl\":12,\"2xl\":16},"
            + "\"logo\":\"https://example.com/linear.svg\""
            + "}";
}
