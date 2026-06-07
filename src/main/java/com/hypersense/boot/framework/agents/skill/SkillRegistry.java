package com.hypersense.boot.framework.agents.skill;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 技能注册表
 * <p>
 * 扫描指定目录下的子文件夹，查找并解析 SKILL.md 文件，
 * 提取 YAML frontmatter 中的 name 和 description 作为技能元数据。
 * </p>
 *
 * <p>目录结构约定：</p>
 * <pre>
 * skills/
 * ├── code-review/
 * │   └── SKILL.md
 * ├── api-design/
 * │   └── SKILL.md
 * └── testing/
 *     └── SKILL.md
 * </pre>
 *
 * @author Claude
 * @since 2026/5/26
 */
@Slf4j
public class SkillRegistry {

    private static final String SKILL_FILE_NAME = "SKILL.md";
    private static final String FRONTMATTER_DELIMITER = "---";

    /** name → SkillDefinition（构造后只读） */
    private final Map<String, SkillDefinition> skills = new ConcurrentHashMap<>();

    /**
     * 扫描指定目录列表
     *
     * @param dirs 技能目录路径数组
     */
    public void scan(String... dirs) {
        if (dirs == null) return;

        for (String dir : dirs) {
            if (dir == null || dir.isBlank()) continue;
            Path dirPath = Path.of(dir.trim());
            if (!Files.isDirectory(dirPath)) {
                log.warn("SkillRegistry: 目录不存在，跳过: {}", dirPath.toAbsolutePath());
                continue;
            }
            scanDirectory(dirPath);
        }

        log.info("SkillRegistry: 扫描完成，共发现 {} 个技能", skills.size());
    }

    /**
     * 根据名称获取技能定义
     */
    public Optional<SkillDefinition> getByName(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        return Optional.ofNullable(skills.get(name.trim()));
    }

    /**
     * 获取所有已注册的技能定义
     */
    public List<SkillDefinition> getAll() {
        return List.copyOf(skills.values());
    }

    /**
     * 是否没有注册任何技能
     */
    public boolean isEmpty() {
        return skills.isEmpty();
    }

    /**
     * 生成技能目录文本（注入到 LLM instructions 中）
     * <p>
     * 仅包含名称和简短描述，详细内容由 LLM 通过 skill_load 工具按需加载。
     * </p>
     */
    public String getCatalogText() {
        if (skills.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("[可用技能]\n");
        for (SkillDefinition skill : skills.values()) {
            sb.append("- ").append(skill.getName())
              .append(": ").append(skill.getDescription())
              .append('\n');
        }
        sb.append("\n使用 skill_load 工具加载技能的详细说明，例如：skill_load(\"")
          .append(skills.values().iterator().next().getName())
          .append("\")");
        return sb.toString();
    }

    // ========== 内部方法 ==========

    private void scanDirectory(Path dirPath) {
        try {
            Files.walkFileTree(dirPath, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
                    new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                            if (SKILL_FILE_NAME.equals(file.getFileName().toString())) {
                                parseSkillFile(file);
                            }
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFileFailed(Path file, IOException exc) {
                            log.warn("SkillRegistry: 无法访问文件: {}", file, exc);
                            return FileVisitResult.CONTINUE;
                        }
                    });
        } catch (IOException e) {
            log.warn("SkillRegistry: 扫描目录失败: {}", dirPath.toAbsolutePath(), e);
        }
    }

    private void parseSkillFile(Path file) {
        try {
            String content = Files.readString(file);
            Map<String, String> frontmatter = parseFrontmatter(content);

            String name = frontmatter.get("name");
            String description = frontmatter.get("description");

            if (name == null || name.isBlank()) {
                log.warn("SkillRegistry: SKILL.md 缺少 name 字段: {}", file.toAbsolutePath());
                return;
            }
            if (description == null || description.isBlank()) {
                log.warn("SkillRegistry: SKILL.md 缺少 description 字段: {}", file.toAbsolutePath());
                return;
            }

            SkillDefinition skill = SkillDefinition.builder()
                    .name(name.trim())
                    .description(description.trim())
                    .filePath(file.toAbsolutePath().toString())
                    .build();

            skills.put(name.trim(), skill);
            log.debug("SkillRegistry: 发现技能 [{}]: {}", name.trim(), file.toAbsolutePath());
        } catch (IOException e) {
            log.warn("SkillRegistry: 读取 SKILL.md 失败: {}", file.toAbsolutePath(), e);
        }
    }

    /**
     * 解析 YAML frontmatter（--- 分隔符之间的内容）
     * <p>
     * 仅提取 name 和 description 字段，不引入 YAML 解析库。
     * 支持多行描述和引号包裹的值。
     * </p>
     */
    Map<String, String> parseFrontmatter(String content) {
        Map<String, String> result = new HashMap<>();

        // 查找 frontmatter 边界
        int firstDelimiter = content.indexOf(FRONTMATTER_DELIMITER);
        if (firstDelimiter < 0) return result;

        int contentStart = firstDelimiter + FRONTMATTER_DELIMITER.length();
        // 跳过紧随的换行符
        while (contentStart < content.length() && content.charAt(contentStart) == '\n') {
            contentStart++;
        }

        int secondDelimiter = content.indexOf(FRONTMATTER_DELIMITER, contentStart);
        if (secondDelimiter < 0) return result;

        String yamlBlock = content.substring(contentStart, secondDelimiter).trim();
        String[] lines = yamlBlock.split("\n");

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            int colonIdx = line.indexOf(':');
            if (colonIdx < 0) continue;

            String key = line.substring(0, colonIdx).trim();
            String value = line.substring(colonIdx + 1).trim();

            // 去除引号包裹
            if (value.length() >= 2) {
                char first = value.charAt(0);
                char last = value.charAt(value.length() - 1);
                if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                    value = value.substring(1, value.length() - 1);
                }
            }

            if ("name".equals(key) || "description".equals(key)) {
                result.put(key, value);
            }
        }

        return result;
    }
}
