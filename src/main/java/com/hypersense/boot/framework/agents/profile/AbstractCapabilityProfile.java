package com.hypersense.boot.framework.agents.profile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

/**
 * Profile 抽象基类：从 sys_agent_profile 实体加载通用配置（提示词/工具白名单/策略等），
 * 子类只覆写 lintRules 等需要专精逻辑的方法。
 */
public abstract class AbstractCapabilityProfile implements CapabilityProfile {

    protected static final ObjectMapper MAPPER = new ObjectMapper();

    private final String id;
    private final String name;
    private final String systemPromptTemplate;
    private final List<String> allowedTools;
    private final PlanStrategy planStrategy;
    private final JsonNode outputFormat;
    private final HitlPolicy hitlPolicy;

    protected AbstractCapabilityProfile(String id, String name, String systemPromptTemplate,
                                        List<String> allowedTools, PlanStrategy planStrategy,
                                        JsonNode outputFormat, HitlPolicy hitlPolicy) {
        this.id = id;
        this.name = name;
        this.systemPromptTemplate = systemPromptTemplate;
        this.allowedTools = allowedTools;
        this.planStrategy = planStrategy;
        this.outputFormat = outputFormat;
        this.hitlPolicy = hitlPolicy;
    }

    @Override public String id() { return id; }
    @Override public String name() { return name; }
    @Override public List<String> allowedTools() { return allowedTools; }
    @Override public PlanStrategy planStrategy() { return planStrategy; }
    @Override public JsonNode outputFormat() { return outputFormat; }
    @Override public HitlPolicy hitlPolicy() { return hitlPolicy == null ? HitlPolicy.defaultPolicy() : hitlPolicy; }

    @Override
    public String systemPrompt(ProfileContext ctx) {
        if (ctx == null) return systemPromptTemplate;
        String prompt = systemPromptTemplate;
        if (ctx.userInput() != null) prompt = prompt.replace("{{userInput}}", ctx.userInput());
        if (ctx.sessionId() != null) prompt = prompt.replace("{{sessionId}}", ctx.sessionId());
        if (ctx.userId() != null) prompt = prompt.replace("{{userId}}", String.valueOf(ctx.userId()));
        return prompt;
    }

    /** 同包/子类访问 systemPromptTemplate 私有字段（DesignProfile 复制构造用） */
    protected String getSystemPromptTemplate() {
        return systemPromptTemplate;
    }
}
