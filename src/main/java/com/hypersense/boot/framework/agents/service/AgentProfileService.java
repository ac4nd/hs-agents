package com.hypersense.boot.framework.agents.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypersense.boot.framework.agents.mapper.AgentProfileMapper;
import com.hypersense.boot.framework.agents.model.AgentProfileEntity;
import com.hypersense.boot.framework.agents.profile.*;
import com.hypersense.boot.framework.agents.profile.impl.DesignProfile;
import com.hypersense.boot.framework.agents.profile.impl.GenericProfile;
import com.hypersense.boot.framework.agents.profile.impl.StubCodeProfile;
import com.hypersense.boot.common.annotation.IgnoreTenant;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * AgentProfile 业务服务：把 DB 实体转换为 CapabilityProfile 实例。
 *
 * 当前实现：5 个 profile_id（design/code/think/docs/learning）对应固定的 Java stub 类，
 * sys_agent_profile 表提供 systemPrompt / allowedTools / planStrategy / hitlPolicy 等可热更新的配置。
 *
 * Plan B/C 阶段会把 StubDesignProfile / StubCodeProfile 替换为真实实现，注册逻辑保持不变。
 */
@Service
public class AgentProfileService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AgentProfileMapper mapper;

    public AgentProfileService(AgentProfileMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 按 profileId 加载实体并包装为 CapabilityProfile。
     * @throws ProfileNotFoundException 当 profileId 不存在或被禁用
     */
    @IgnoreTenant
    public CapabilityProfile loadProfile(String profileId) {
        AgentProfileEntity entity = mapper.findEnabledByProfileId(profileId);
        if (entity == null) {
            throw new ProfileNotFoundException(profileId);
        }
        return buildProfile(entity);
    }

    @IgnoreTenant
    private CapabilityProfile buildProfile(AgentProfileEntity entity) {
        List<String> tools = parseStringList(entity.getAllowedTools());
        HitlPolicy policy = parseHitlPolicy(entity.getHitlPolicy());
        String template = entity.getSystemPrompt();
        String id = entity.getProfileId();
        String name = entity.getName();
        JsonNode outputFormat = entity.getOutputFormat();
        PlanStrategy strategy = PlanStrategy.fromString(entity.getPlanStrategy());

        return switch (id) {
            case "design" -> DesignProfile.withBrandColor(
                    resolveBrandPrimary(outputFormat), template, tools, outputFormat, policy);
            case "code" -> new StubCodeProfile(id, name, template, tools, strategy, outputFormat, policy);
            default -> new GenericProfile(id, name, template, tools, strategy, outputFormat, policy);
        };
    }

    /** 从 outputFormat 或 entity 字段提取 design system 主色；无则返回 null 跳过 brand_color_drift */
    private String resolveBrandPrimary(JsonNode outputFormat) {
        if (outputFormat == null || outputFormat.isMissingNode() || outputFormat.isNull()) return null;
        JsonNode ds = outputFormat.path("properties").path("meta").path("properties").path("designSystem");
        if (ds.isMissingNode()) {
            ds = outputFormat.path("designSystem");
        }
        JsonNode primary = ds.path("primary");
        return primary.isTextual() ? primary.asText() : null;
    }

    private List<String> parseStringList(JsonNode node) {
        List<String> result = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(n -> result.add(n.asText()));
        }
        return result;
    }

    private HitlPolicy parseHitlPolicy(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return HitlPolicy.defaultPolicy();
        }
        boolean enable = node.path("enableInterrupt").asBoolean(true);
        List<String> phases = new ArrayList<>();
        JsonNode phasesNode = node.get("interruptPhases");
        if (phasesNode != null && phasesNode.isArray()) {
            phasesNode.forEach(n -> phases.add(n.asText()));
        }
        int maxLint = node.path("maxLintRetriesBeforeInterrupt").asInt(3);
        int maxViolation = node.path("maxToolViolationsBeforeInterrupt").asInt(3);
        return new HitlPolicy(enable, phases, maxLint, maxViolation);
    }
}
