package com.hypersense.boot.framework.agents.profile.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hypersense.boot.framework.agents.profile.*;
import com.hypersense.boot.framework.agents.profile.lint.*;

import java.util.List;

/**
 * 真实 design-profile 实现（替换 Plan A 的 StubDesignProfile）。
 *
 * 覆盖能力：
 * - 真实 huashu-design 哲学的 systemPrompt
 * - 5 条反 slop lint 规则（无品牌色时 4 条；有品牌色时含 brand_color_drift）
 * - 强制 demo/batch/lint 三阶段 HITL
 * - slides JSON schema 作为 outputFormat
 */
public class DesignProfile extends AbstractCapabilityProfile {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final List<LintRule> lintRules;

    public DesignProfile(String id, String name, String template, List<String> tools,
                         PlanStrategy strategy, JsonNode outputFormat, HitlPolicy policy) {
        super(id, name, template, tools, strategy, outputFormat, policy);
        this.lintRules = List.of(
                new NoPurpleGradientRule(),
                new NoEmojiIconRule(),
                new NoPlaceholderRule(),
                new NoSvgHumanRule()
        );
    }

    /**
     * 创建带品牌色的 design-profile（推荐入口）。
     * @param brandPrimaryHex 设计系统主色，如 "#07c160"；null/blank 时不含 brand_color_drift
     */
    public static DesignProfile withBrandColor(String brandPrimaryHex,
                                               String template, List<String> tools,
                                               JsonNode outputFormat, HitlPolicy policy) {
        DesignProfile base = new DesignProfile(
                "design", "设计模式", template, tools,
                PlanStrategy.OUTLINE_DEMO, outputFormat, policy);
        if (brandPrimaryHex != null && !brandPrimaryHex.isBlank()) {
            return new DesignProfile(base, List.of(
                    new NoPurpleGradientRule(),
                    new NoEmojiIconRule(),
                    new NoPlaceholderRule(),
                    new NoSvgHumanRule(),
                    new BrandColorDriftRule(brandPrimaryHex, 15.0)
            ));
        }
        return base;
    }

    private DesignProfile(DesignProfile source, List<LintRule> customRules) {
        super(source.id(), source.name(),
                source.getSystemPromptTemplate(),
                source.allowedTools(), source.planStrategy(),
                source.outputFormat(), source.hitlPolicy());
        this.lintRules = customRules;
    }

    @Override
    public List<LintRule> lintRules() {
        return lintRules;
    }

    /** design-profile 默认 outputFormat（spec §4.2 slides JSON schema） */
    public static JsonNode defaultOutputFormat() {
        try {
            return MAPPER.readTree(SlidesSchema.SCHEMA_JSON);
        } catch (Exception e) {
            ObjectNode fallback = MAPPER.createObjectNode();
            fallback.put("note", "fallback: schema parse failed");
            return fallback;
        }
    }
}
