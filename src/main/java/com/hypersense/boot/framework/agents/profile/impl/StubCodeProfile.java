package com.hypersense.boot.framework.agents.profile.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.hypersense.boot.framework.agents.profile.*;
import java.util.List;

/**
 * code-profile stub：Plan A 阶段仅承载 DB 配置。
 * Plan C 将替换为真实 CodeProfile（含 TDD 状态机、package_lookup）。
 */
public class StubCodeProfile extends AbstractCapabilityProfile {

    public StubCodeProfile(String id, String name, String template, List<String> tools,
                           PlanStrategy strategy, JsonNode outputFormat, HitlPolicy policy) {
        super(id, name, template, tools, strategy, outputFormat, policy);
    }

    @Override
    public List<LintRule> lintRules() {
        return List.of(); // Plan C 填充
    }
}
