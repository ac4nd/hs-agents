package com.hypersense.boot.framework.agents.skill;

import lombok.Builder;
import lombok.Data;

/**
 * 技能定义
 * <p>
 * 解析 SKILL.md 的 YAML frontmatter 得到的元数据。
 * 完整的 SKILL.md 正文通过 {@link SkillLoadTool} 按需加载。
 * </p>
 *
 * @author Claude
 * @since 2026/5/26
 */
@Data
@Builder
public class SkillDefinition {

    /**
     * 技能名称（YAML frontmatter: name，全局唯一标识）
     */
    private String name;

    /**
     * 技能描述（YAML frontmatter: description，供 LLM 理解技能用途）
     */
    private String description;

    /**
     * SKILL.md 文件的绝对路径（用于按需加载完整正文）
     */
    private String filePath;
}
