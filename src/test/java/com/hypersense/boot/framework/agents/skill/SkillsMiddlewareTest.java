package com.hypersense.boot.framework.agents.skill;

import com.hypersense.boot.framework.agents.model.DeepAgentState;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SkillsMiddleware 单元测试
 * <p>
 * 覆盖：enhanceInstructions、hasSkills、after 透传。
 *
 * @author test
 */
class SkillsMiddlewareTest {

    private SkillRegistry createPopulatedRegistry() throws IOException {
        Path dir = Files.createTempDirectory("test-skills");
        Path skillDir = dir.resolve("test-skill");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"),
                "---\nname: test-skill\ndescription: 测试技能\n---\n\n# Test");

        SkillRegistry reg = new SkillRegistry();
        reg.scan(dir.toString());
        deleteRecursively(dir);
        return reg;
    }

    // ======================== 基础属性测试 ========================

    @Nested
    @DisplayName("基础属性")
    class BasicTests {

        @Test
        @DisplayName("name → 返回 'skills'")
        void testName() {
            SkillRegistry emptyRegistry = new SkillRegistry();
            SkillsMiddleware mw = new SkillsMiddleware(emptyRegistry);
            assertEquals("skills", mw.name());
        }

        @Test
        @DisplayName("getRegistry → 返回底层注册表")
        void testGetRegistry() throws Exception {
            SkillRegistry registry = createPopulatedRegistry();
            SkillsMiddleware mw = new SkillsMiddleware(registry);
            assertSame(registry, mw.getRegistry());
        }
    }

    // ======================== hasSkills 测试 ========================

    @Nested
    @DisplayName("hasSkills")
    class HasSkillsTests {

        @Test
        @DisplayName("空注册表 → hasSkills() = false")
        void testHasSkills_empty() {
            SkillsMiddleware mw = new SkillsMiddleware(new SkillRegistry());
            assertFalse(mw.hasSkills());
        }

        @Test
        @DisplayName("有技能 → hasSkills() = true")
        void testHasSkills_notEmpty() throws Exception {
            SkillRegistry registry = createPopulatedRegistry();
            SkillsMiddleware mw = new SkillsMiddleware(registry);
            assertTrue(mw.hasSkills());
        }
    }

    // ======================== enhanceInstructions 测试 ========================

    @Nested
    @DisplayName("enhanceInstructions - 技能目录注入")
    class EnhanceInstructionsTests {

        @Test
        @DisplayName("有技能 → instructions 追加目录文本")
        void testEnhance_withSkills() throws Exception {
            SkillRegistry registry = createPopulatedRegistry();
            SkillsMiddleware mw = new SkillsMiddleware(registry);

            String result = mw.enhanceInstructions("用户指令");

            assertTrue(result.contains("用户指令"), "应保留原始指令");
            assertTrue(result.contains(SkillsMiddleware.SKILL_CATALOG_MARKER), "应包含 marker");
            assertTrue(result.contains("test-skill"), "应包含技能名");
            assertTrue(result.contains("测试技能"), "应包含技能描述");
            assertTrue(result.contains("skill_load"), "应包含工具使用提示");
        }

        @Test
        @DisplayName("重复调用 → 不重复注入")
        void testEnhance_noDuplicate() throws Exception {
            SkillRegistry registry = createPopulatedRegistry();
            SkillsMiddleware mw = new SkillsMiddleware(registry);

            String first = mw.enhanceInstructions("用户指令");
            String second = mw.enhanceInstructions(first);

            assertEquals(first, second, "第二次调用不应重复注入");
        }

        @Test
        @DisplayName("空注册表 → 返回原始指令")
        void testEnhance_emptyRegistry() {
            SkillsMiddleware mw = new SkillsMiddleware(new SkillRegistry());
            String result = mw.enhanceInstructions("原始指令");
            assertEquals("原始指令", result);
        }

        @Test
        @DisplayName("null instructions → 不抛异常")
        void testEnhance_nullInstructions() throws Exception {
            SkillRegistry registry = createPopulatedRegistry();
            SkillsMiddleware mw = new SkillsMiddleware(registry);

            String result = mw.enhanceInstructions(null);
            assertTrue(result.contains(SkillsMiddleware.SKILL_CATALOG_MARKER));
        }
    }

    // ======================== after 测试 ========================

    @Nested
    @DisplayName("after - 透传")
    class AfterTests {

        @Test
        @DisplayName("after → 返回原始 output")
        void testAfter_returnsOriginal() throws Exception {
            SkillRegistry registry = createPopulatedRegistry();
            SkillsMiddleware mw = new SkillsMiddleware(registry);

            Map<String, Object> state = new HashMap<>();
            state.put(DeepAgentState.SESSION_ID, "test");
            state.put(DeepAgentState.INSTRUCTIONS, "指令");
            state.put(DeepAgentState.MESSAGES, new ArrayList<>());
            state.put(DeepAgentState.TODOS, new ArrayList<>());
            state.put(DeepAgentState.FILES, new HashMap<>());
            state.put(DeepAgentState.SUB_AGENT_RESULTS, new HashMap<>());
            state.put(DeepAgentState.ENABLED_TOOLS, new ArrayList<>());
            DeepAgentState deepState = new DeepAgentState(state);

            Map<String, Object> output = Map.of("key", "value");
            Map<String, Object> result = mw.after("plan", deepState, output);
            assertSame(output, result);
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
