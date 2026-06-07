package com.hypersense.boot.framework.agents.skill;

import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SkillLoadTool 单元测试
 * <p>
 * 覆盖：加载有效技能、未知技能、参数缺失、边界情况。
 *
 * @author test
 */
class SkillLoadToolTest {

    private Path skillDir;
    private SkillRegistry registry;
    private SkillLoadTool tool;

    @BeforeEach
    void setUp() throws IOException {
        skillDir = Files.createTempDirectory("test-skills");
        tool = new SkillLoadTool(registry);
    }

    @AfterEach
    void tearDown() throws IOException {
        deleteRecursively(skillDir);
    }

    private SkillRegistry createAndScanRegistry() throws IOException {
        SkillRegistry reg = new SkillRegistry();
        reg.scan(skillDir.toString());
        return reg;
    }

    private void createSkill(String name, String description, String body) throws IOException {
        Path skillSubDir = skillDir.resolve(name);
        Files.createDirectories(skillSubDir);
        Files.writeString(skillSubDir.resolve("SKILL.md"),
                "---\nname: " + name + "\ndescription: " + description + "\n---\n\n" + body);
    }

    // ======================== 基础属性 ========================

    @Nested
    @DisplayName("基础属性")
    class BasicTests {

        @Test
        @DisplayName("name → 'skill_load'")
        void testName() {
            tool = new SkillLoadTool(new SkillRegistry());
            assertEquals("skill_load", tool.name());
        }

        @Test
        @DisplayName("description → 包含 skill_name")
        void testDescription() {
            tool = new SkillLoadTool(new SkillRegistry());
            assertTrue(tool.description().contains("skill_name"));
        }
    }

    // ======================== 正常加载 ========================

    @Nested
    @DisplayName("正常加载")
    class LoadTests {

        @Test
        @DisplayName("有效 skill_name → 返回完整 SKILL.md 内容")
        void testExecute_validSkill() throws Exception {
            createSkill("code-review", "代码审查", "# Code Review\n\n审查流程...");
            registry = createAndScanRegistry();
            tool = new SkillLoadTool(registry);

            Object result = tool.execute(Map.of("skill_name", "code-review"));

            assertInstanceOf(Map.class, result);
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) result;
            assertEquals(true, map.get("success"));
            assertEquals("code-review", map.get("skill_name"));
            String content = (String) map.get("content");
            assertTrue(content.contains("# Code Review"));
            assertTrue(content.contains("审查流程"));
        }

        @Test
        @DisplayName("内容包含 frontmatter + 正文")
        void testExecute_containsFrontmatter() throws Exception {
            createSkill("api-design", "API 设计", "## API 规范");
            registry = createAndScanRegistry();
            tool = new SkillLoadTool(registry);

            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) tool.execute(Map.of("skill_name", "api-design"));

            String content = (String) result.get("content");
            assertTrue(content.contains("name: api-design"), "应包含 frontmatter");
            assertTrue(content.contains("## API 规范"), "应包含正文");
        }
    }

    // ======================== 错误处理 ========================

    @Nested
    @DisplayName("错误处理")
    class ErrorTests {

        @Test
        @DisplayName("不存在的 skill_name → 返回错误 + 可用列表")
        void testExecute_unknownSkill() throws Exception {
            createSkill("code-review", "代码审查", "内容");
            registry = createAndScanRegistry();
            tool = new SkillLoadTool(registry);

            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) tool.execute(Map.of("skill_name", "nonexistent"));

            assertEquals(false, result.get("success"));
            assertTrue(result.get("error").toString().contains("nonexistent"));
            assertNotNull(result.get("available_skills"));
        }

        @Test
        @DisplayName("缺少 skill_name 参数 → 返回参数错误")
        void testExecute_missingParam() throws Exception {
            registry = createAndScanRegistry();
            tool = new SkillLoadTool(registry);

            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) tool.execute(Map.of());

            assertEquals(false, result.get("success"));
            assertTrue(result.get("error").toString().contains("skill_name"));
        }

        @Test
        @DisplayName("null params → 返回参数错误")
        void testExecute_nullParams() {
            tool = new SkillLoadTool(new SkillRegistry());

            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) tool.execute(null);

            assertEquals(false, result.get("success"));
        }

        @Test
        @DisplayName("空注册表 → 任何 skill_name 都返回错误")
        void testExecute_emptyRegistry() {
            registry = new SkillRegistry();
            tool = new SkillLoadTool(registry);

            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) tool.execute(Map.of("skill_name", "anything"));

            assertEquals(false, result.get("success"));
        }
    }

    // ======================== 回退匹配 ========================

    @Nested
    @DisplayName("参数回退")
    class FallbackTests {

        @Test
        @DisplayName("todo_description 包含技能名 → 自动匹配")
        void testExecute_todoDescriptionFallback() throws Exception {
            createSkill("code-review", "代码审查", "# Review");
            registry = createAndScanRegistry();
            tool = new SkillLoadTool(registry);

            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) tool.execute(
                    Map.of("todo_description", "使用 code-review 技能审查代码"));

            assertEquals(true, result.get("success"));
            assertEquals("code-review", result.get("skill_name"));
        }
    }

    // ========== 辅助 ==========

    private void deleteRecursively(Path dir) throws IOException {
        if (Files.exists(dir)) {
            Files.walk(dir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                    });
        }
    }
}
