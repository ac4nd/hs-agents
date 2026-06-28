package com.hypersense.boot.framework.agents.profile;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * 能力档位接口。每个实现代表一类专精能力（design/code/think/docs/learning）。
 * PlanNode/ExecuteNode/ToolNode 实例化时按 activeProfile 取这些方法返回值。
 */
public interface CapabilityProfile {

    /** 档位 ID，如 "design" / "code"，必须与 sys_agent_profile.profile_id 对齐 */
    String id();

    /** 展示名称 */
    String name();

    /** 注入到 PlanNode/ExecuteNode 的系统提示词 */
    String systemPrompt(ProfileContext ctx);

    /** 工具白名单：ToolNode 仅允许调用此列表内的工具 */
    List<String> allowedTools();

    /** Plan 策略枚举 */
    PlanStrategy planStrategy();

    /** 输出格式 JSON schema，约束 LLM 输出（可为 null 表示不强制） */
    JsonNode outputFormat();

    /** lint 规则列表（Plan A 仅返回空，Plan B/C 由各 Profile 实现） */
    List<LintRule> lintRules();

    /** HITL 策略 */
    HitlPolicy hitlPolicy();
}
