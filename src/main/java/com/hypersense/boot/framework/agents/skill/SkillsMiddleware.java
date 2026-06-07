package com.hypersense.boot.framework.agents.skill;

import com.hypersense.boot.framework.agents.middleware.AgentMiddleware;
import com.hypersense.boot.framework.agents.model.DeepAgentState;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 技能目录注入中间件
 * <p>
 * 提供技能目录文本生成能力，供 Agent 在构建初始状态时注入到 instructions 中。
 * 仅注入元数据摘要（名称+描述），完整技能内容由 LLM 通过 {@link SkillLoadTool} 按需加载。
 * </p>
 *
 * <h3>注入机制：</h3>
 * <p>
 * 由于 AgentState.data() 返回不可变 Map，无法在运行时通过 before() 直接修改 state。
 * 因此，注入发生在两个层面：
 * </p>
 * <ul>
 *   <li>Builder 路径：GodlikeAgent.buildInitialState() 中调用 {@link #enhanceInstructions(String)}</li>
 *   <li>Spring 路径：AgentServiceImpl.buildInitialState() 中调用 {@link #enhanceInstructions(String)}</li>
 * </ul>
 *
 * @author Claude
 * @since 2026/5/26
 */
@Slf4j
public class SkillsMiddleware implements AgentMiddleware {

    /** 注入到 instructions 中的唯一标记（用于防重复注入检测） */
    static final String SKILL_CATALOG_MARKER = "--- Skills Catalog ---";

    private final SkillRegistry registry;

    public SkillsMiddleware(SkillRegistry registry) {
        this.registry = registry;
    }

    @Override
    public String name() {
        return "skills";
    }

    /**
     * 获取底层技能注册表
     */
    public SkillRegistry getRegistry() {
        return registry;
    }

    /**
     * 将技能目录追加到用户指令中
     * <p>
     * 在构建初始状态时调用，将技能名称+描述列表注入到 instructions 中。
     * LLM 在规划时看到技能目录，决定是否通过 skill_load 加载详细说明。
     * </p>
     *
     * @param instructions 原始用户指令
     * @return 增强后的指令（包含技能目录），如果 registry 为空则返回原始指令
     */
    public String enhanceInstructions(String instructions) {
        if (registry.isEmpty()) return instructions;
        if (instructions != null && instructions.contains(SKILL_CATALOG_MARKER)) return instructions;

        String catalog = registry.getCatalogText();
        String enhanced = (instructions != null ? instructions : "") + "\n\n" + SKILL_CATALOG_MARKER + "\n" + catalog;
        log.info("SkillsMiddleware: 已注入 {} 个技能的目录到 instructions", registry.getAll().size());
        return enhanced;
    }

    /**
     * 检查注册表是否有技能
     */
    public boolean hasSkills() {
        return !registry.isEmpty();
    }

    @Override
    public void before(String nodeName, DeepAgentState state) {
        // 注入在 buildInitialState 中完成，before 不再修改 state
    }

    @Override
    public Map<String, Object> after(String nodeName, DeepAgentState state, Map<String, Object> output) {
        return output;
    }
}
