package com.hypersense.boot.framework.agents.profile.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.hypersense.boot.framework.agents.profile.AbstractCapabilityProfile;
import com.hypersense.boot.framework.agents.profile.HitlPolicy;
import com.hypersense.boot.framework.agents.profile.LintRule;
import com.hypersense.boot.framework.agents.profile.PlanStrategy;

import java.util.List;

/**
 * 兜底 Profile，用于未知 profileId（理论上不会触发，因为 ProfileNotFoundException 已拦截）。
 * 仅占位以保证 fallback 路径不返回 null。
 */
public class GenericProfile extends AbstractCapabilityProfile {

    public GenericProfile(String id, String name, String template, List<String> tools,
                          PlanStrategy strategy, JsonNode outputFormat, HitlPolicy policy) {
        super(id, name, template, tools, strategy, outputFormat, policy);
    }

    @Override
    public List<LintRule> lintRules() {
        return List.of();
    }
}
