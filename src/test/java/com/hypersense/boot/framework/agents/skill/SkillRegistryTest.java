package com.hypersense.boot.framework.agents.skill;

import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SkillRegistry 单元测试
 * <p>
 * 覆盖：目录扫描、frontmatter 解析、技能查找、目录文本生成、边界情况。
 * 使用 JUnit 5 @TempDir 创建临时文件系统。
 *
 * @author test
 */
class SkillRegistryTest {

    // ======================== 扫描测试 ========================

    @Nested
    @DisplayName("目录扫描")
    class ScanTests {

        @Test
        @DisplayName("扫描含有效 SKILL.md 的目录 → 正确发现技能")
        void testScan_validSkills() throws IOException {
            Path skillDir = Files.createTempDirectory("skills");
            try {
                // 创建 code-review 技能
                Path reviewDir = skillDir.resolve("code-review");
                Files.createDirectories(reviewDir);
                Files.writeString(reviewDir.resolve("SKILL.md"), """
                        ---
                        name: code-review
                        description: 代码审查技能
                        ---

                        # Code Review
                        """);

                // 创建 api-design 技能
                Path apiDir = skillDir.resolve("api-design");
                Files.createDirectories(apiDir);
                Files.writeString(apiDir.resolve("SKILL.md"), """
                        ---
                        name: api-design
                        description: API 设计技能
                        ---

                        # API Design
                        """);

                SkillRegistry registry = new SkillRegistry();
                registry.scan(skillDir.toString());

                assertFalse(registry.isEmpty());
                assertEquals(2, registry.getAll().size());
                assertTrue(registry.getByName("code-review").isPresent());
                assertTrue(registry.getByName("api-design").isPresent());
            } finally {
                deleteRecursively(skillDir);
            }
        }

        @Test
        @DisplayName("嵌套子目录 → 递归发现 SKILL.md")
        void testScan_nestedDirs() throws IOException {
            Path skillDir = Files.createTempDirectory("skills");
            try {
                Path nested = skillDir.resolve("category").resolve("testing");
                Files.createDirectories(nested);
                Files.writeString(nested.resolve("SKILL.md"), """
                        ---
                        name: unit-testing
                        description: 单元测试技能
                        ---

                        # Testing
                        """);

                SkillRegistry registry = new SkillRegistry();
                registry.scan(skillDir.toString());

                assertEquals(1, registry.getAll().size());
                assertEquals("unit-testing", registry.getAll().get(0).getName());
            } finally {
                deleteRecursively(skillDir);
            }
        }

        @Test
        @DisplayName("空目录 → 返回空列表")
        void testScan_emptyDir() throws IOException {
            Path emptyDir = Files.createTempDirectory("empty-skills");
            try {
                SkillRegistry registry = new SkillRegistry();
                registry.scan(emptyDir.toString());

                assertTrue(registry.isEmpty());
                assertEquals(0, registry.getAll().size());
            } finally {
                deleteRecursively(emptyDir);
            }
        }

        @Test
        @DisplayName("不存在的目录 → 不抛异常，返回空")
        void testScan_nonExistentDir() {
            SkillRegistry registry = new SkillRegistry();
            assertDoesNotThrow(() -> registry.scan("/non/existent/path"));
            assertTrue(registry.isEmpty());
        }

        @Test
        @DisplayName("null 目录 → 不抛异常")
        void testScan_nullDirs() {
            SkillRegistry registry = new SkillRegistry();
            assertDoesNotThrow(() -> registry.scan((String) null));
            assertDoesNotThrow(() -> registry.scan((String[]) null));
        }

        @Test
        @DisplayName("多个目录 → 合并所有技能")
        void testScan_multipleDirs() throws IOException {
            Path dir1 = Files.createTempDirectory("skills1");
            Path dir2 = Files.createTempDirectory("skills2");
            try {
                createSkillFile(dir1, "skill-a", "技能 A");
                createSkillFile(dir2, "skill-b", "技能 B");

                SkillRegistry registry = new SkillRegistry();
                registry.scan(dir1.toString(), dir2.toString());

                assertEquals(2, registry.getAll().size());
            } finally {
                deleteRecursively(dir1);
                deleteRecursively(dir2);
            }
        }
    }

    // ======================== Frontmatter 解析测试 ========================

    @Nested
    @DisplayName("YAML Frontmatter 解析")
    class FrontmatterTests {

        @Test
        @DisplayName("标准 frontmatter → 正确提取 name 和 description")
        void testParseFrontmatter_standard() {
            SkillRegistry registry = new SkillRegistry();
            Map<String, String> result = registry.parseFrontmatter("""
                    ---
                    name: code-review
                    description: 代码审查技能
                    ---

                    # Content
                    """);

            assertEquals("code-review", result.get("name"));
            assertEquals("代码审查技能", result.get("description"));
        }

        @Test
        @DisplayName("引号包裹的值 → 去除引号")
        void testParseFrontmatter_quotedValues() {
            SkillRegistry registry = new SkillRegistry();
            Map<String, String> result = registry.parseFrontmatter("""
                    ---
                    name: "api-design"
                    description: 'RESTful API 设计技能'
                    ---
                    """);

            assertEquals("api-design", result.get("name"));
            assertEquals("RESTful API 设计技能", result.get("description"));
        }

        @Test
        @DisplayName("无 frontmatter → 返回空 map")
        void testParseFrontmatter_noFrontmatter() {
            SkillRegistry registry = new SkillRegistry();
            Map<String, String> result = registry.parseFrontmatter("# Just content\nNo frontmatter");

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("只有第一个分隔符 → 返回空 map")
        void testParseFrontmatter_onlyOneDelimiter() {
            SkillRegistry registry = new SkillRegistry();
            Map<String, String> result = registry.parseFrontmatter("""
                    ---
                    name: test
                    """);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("缺少 name → 跳过该技能")
        void testScan_missingName() throws IOException {
            Path dir = Files.createTempDirectory("skills");
            try {
                Path skillSubDir = dir.resolve("bad-skill");
                Files.createDirectories(skillSubDir);
                Files.writeString(skillSubDir.resolve("SKILL.md"), """
                        ---
                        description: 缺少 name 的技能
                        ---
                        """);

                SkillRegistry registry = new SkillRegistry();
                registry.scan(dir.toString());

                assertTrue(registry.isEmpty());
            } finally {
                deleteRecursively(dir);
            }
        }

        @Test
        @DisplayName("缺少 description → 跳过该技能")
        void testScan_missingDescription() throws IOException {
            Path dir = Files.createTempDirectory("skills");
            try {
                Path skillSubDir = dir.resolve("no-desc");
                Files.createDirectories(skillSubDir);
                Files.writeString(skillSubDir.resolve("SKILL.md"), """
                        ---
                        name: no-desc
                        ---
                        """);

                SkillRegistry registry = new SkillRegistry();
                registry.scan(dir.toString());

                assertTrue(registry.isEmpty());
            } finally {
                deleteRecursively(dir);
            }
        }

        @Test
        @DisplayName("含注释行 → 正确跳过注释")
        void testParseFrontmatter_withComments() {
            SkillRegistry registry = new SkillRegistry();
            Map<String, String> result = registry.parseFrontmatter("""
                    ---
                    # 这是注释
                    name: test-skill
                    # 另一个注释
                    description: 测试技能
                    ---
                    """);

            assertEquals("test-skill", result.get("name"));
            assertEquals("测试技能", result.get("description"));
        }
    }

    // ======================== 查找测试 ========================

    @Nested
    @DisplayName("技能查找")
    class LookupTests {

        @Test
        @DisplayName("getByName → 正确返回")
        void testGetByName() throws IOException {
            Path dir = Files.createTempDirectory("skills");
            try {
                createSkillFile(dir, "my-skill", "我的技能");
                SkillRegistry registry = new SkillRegistry();
                registry.scan(dir.toString());

                Optional<SkillDefinition> skill = registry.getByName("my-skill");
                assertTrue(skill.isPresent());
                assertEquals("我的技能", skill.get().getDescription());
                assertNotNull(skill.get().getFilePath());
            } finally {
                deleteRecursively(dir);
            }
        }

        @Test
        @DisplayName("getByName → 不存在返回 empty")
        void testGetByName_notFound() throws IOException {
            Path dir = Files.createTempDirectory("skills");
            try {
                createSkillFile(dir, "exists", "存在的技能");
                SkillRegistry registry = new SkillRegistry();
                registry.scan(dir.toString());

                assertTrue(registry.getByName("not-exists").isEmpty());
            } finally {
                deleteRecursively(dir);
            }
        }

        @Test
        @DisplayName("getByName → null 返回 empty")
        void testGetByName_null() {
            SkillRegistry registry = new SkillRegistry();
            assertTrue(registry.getByName(null).isEmpty());
            assertTrue(registry.getByName("").isEmpty());
            assertTrue(registry.getByName("  ").isEmpty());
        }
    }

    // ======================== 目录文本测试 ========================

    @Nested
    @DisplayName("目录文本生成")
    class CatalogTextTests {

        @Test
        @DisplayName("getCatalogText → 包含技能名和描述")
        void testGetCatalogText() throws IOException {
            Path dir = Files.createTempDirectory("skills");
            try {
                createSkillFile(dir, "code-review", "代码审查技能");
                createSkillFile(dir, "api-design", "API 设计技能");

                SkillRegistry registry = new SkillRegistry();
                registry.scan(dir.toString());

                String text = registry.getCatalogText();
                assertTrue(text.contains("[可用技能]"));
                assertTrue(text.contains("code-review: 代码审查技能"));
                assertTrue(text.contains("api-design: API 设计技能"));
                assertTrue(text.contains("skill_load"));
            } finally {
                deleteRecursively(dir);
            }
        }

        @Test
        @DisplayName("空注册表 → 返回空字符串")
        void testGetCatalogText_empty() {
            SkillRegistry registry = new SkillRegistry();
            assertEquals("", registry.getCatalogText());
        }
    }

    // ========== 辅助方法 ==========

    private void createSkillFile(Path parentDir, String name, String description) throws IOException {
        Path skillDir = parentDir.resolve(name);
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"),
                "---\nname: " + name + "\ndescription: " + description + "\n---\n\n# " + name);
    }

    private void deleteRecursively(Path dir) throws IOException {
        if (Files.exists(dir)) {
            Files.walk(dir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    });
        }
    }
}
