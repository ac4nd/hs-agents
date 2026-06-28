package com.hypersense.boot.framework.agents.profile.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.hypersense.boot.framework.agents.profile.*;
import java.util.List;

/**
 * design-profile stub：Plan A 阶段仅承载 DB 配置，不实现反 slop lint / 资产协议。
 * Plan B 将替换为真实 DesignProfile（含 lintRules、design_asset_fetch 等）。
 */
public class StubDesignProfile extends AbstractCapabilityProfile {

    public StubDesignProfile(String id, String name, String template, List<String> tools,
                             PlanStrategy strategy, JsonNode outputFormat, HitlPolicy policy) {
        super(id, name, template, tools, strategy, outputFormat, policy);
    }

    @Override
    public List<LintRule> lintRules() {
        return List.of(); // Plan B 填充
    }
}
