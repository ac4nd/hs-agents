package com.hypersense.boot.framework.agents.skill;

import com.hypersense.boot.framework.agents.tool.ToolProvider;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 技能加载工具
 * <p>
 * LLM 通过此工具按需加载技能的完整 SKILL.md 正文（渐进式加载）。
 * 仅在 LLM 判断需要使用某个技能时才调用，避免一次性加载所有技能内容到上下文。
 * </p>
 *
 * @author Claude
 * @since 2026/5/26
 */
@Slf4j
public class SkillLoadTool implements ToolProvider {

    private static final String PARAM_SKILL_NAME = "skill_name";

    private final SkillRegistry registry;

    public SkillLoadTool(SkillRegistry registry) {
        this.registry = registry;
    }

    @Override
    public String name() {
        return "skill_load";
    }

    @Override
    public String description() {
        return "加载指定技能的完整指令。参数：skill_name（技能名称，从技能目录中选择）";
    }

    @Override
    public Object execute(Map<String, Object> params) {
        // 提取 skill_name 参数
        String skillName = extractSkillName(params);

        if (skillName == null || skillName.isBlank()) {
            return buildError("缺少 skill_name 参数", registry.getAll().stream()
                    .map(SkillDefinition::getName)
                    .collect(Collectors.joining(", ")));
        }

        // 查找技能
        var skillOpt = registry.getByName(skillName.trim());
        if (skillOpt.isEmpty()) {
            String available = registry.getAll().stream()
                    .map(s -> s.getName() + ": " + s.getDescription())
                    .collect(Collectors.joining("\n"));
            return buildError("未找到技能: " + skillName, available);
        }

        // 读取完整 SKILL.md 正文
        SkillDefinition skill = skillOpt.get();
        try {
            String fullContent = Files.readString(Path.of(skill.getFilePath()));
            log.info("SkillLoadTool: 加载技能 [{}] 成功，内容长度 {}",
                    skill.getName(), fullContent.length());

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("skill_name", skill.getName());
            result.put("content", fullContent);
            return result;
        } catch (IOException e) {
            log.error("SkillLoadTool: 读取 SKILL.md 失败: {}", skill.getFilePath(), e);
            return buildError("读取技能文件失败: " + e.getMessage(), null);
        }
    }

    /**
     * 从参数中提取技能名称
     * <p>
     * 优先从 skill_name 参数获取，回退到 todo_description 中尝试匹配。
     * </p>
     */
    private String extractSkillName(Map<String, Object> params) {
        if (params == null) return null;

        // 优先级 1：直接传入 skill_name
        Object name = params.get(PARAM_SKILL_NAME);
        if (name instanceof String s && !s.isBlank()) {
            return s;
        }

        // 优先级 2：从 todo_description 中提取（LLM 可能把技能名写在描述里）
        Object todoDesc = params.get("todo_description");
        if (todoDesc instanceof String desc && !desc.isBlank()) {
            // 尝试匹配 "使用 xxx 技能" 或 "加载 xxx" 模式
            for (SkillDefinition skill : registry.getAll()) {
                if (desc.contains(skill.getName())) {
                    return skill.getName();
                }
            }
        }

        return name instanceof String ? (String) name : null;
    }

    private Map<String, Object> buildError(String error, String available) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("error", error);
        if (available != null) {
            result.put("available_skills", available);
        }
        return result;
    }
}
