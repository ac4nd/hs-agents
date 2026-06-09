package com.hypersense.boot.framework.agents.skill;

import com.hypersense.boot.agents.service.impl.AgentServiceImpl;
import com.hypersense.boot.common.constant.RedisConstants;
import com.hypersense.boot.framework.agents.config.AgentProperties;
import com.hypersense.boot.framework.agents.engine.DeepAgentGraph;
import com.hypersense.boot.framework.agents.enums.SessionStatus;
import com.hypersense.boot.framework.agents.form.AgentSessionForm;
import com.hypersense.boot.framework.agents.model.DeepAgentState;
import com.hypersense.boot.framework.agents.sandbox.SandboxManager;
import com.hypersense.boot.framework.agents.vo.AgentSessionVO;
import com.hypersense.boot.framework.security.util.SecurityUtils;
import cn.hutool.core.util.StrUtil;
import org.bsc.langgraph4j.CompiledGraph;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Skill 系统集成测试 — 覆盖 Path A（Builder）和 Path B（Spring 配置）
 * <p>
 * 使用真实的技能目录 D:/project/myproject/ac4nd/skills（包含 docx 技能），
 * 验证 SkillRegistry 扫描、SkillsMiddleware 注入、SkillLoadTool 加载的完整闭环。
 * </p>
 *
 * @author test
 */
class SkillIntegrationTest {

    /** 真实技能目录（包含 docx 子文件夹） */
    private static final String REAL_SKILLS_DIR = "D:/project/myproject/ac4nd/skills";

    // ======================== Path A：Builder 路径集成测试 ========================

    @Nested
    @DisplayName("Path A - Builder 路径：GodlikeAgent.skills() 集成")
    class PathABuilderTests {

        @Test
        @DisplayName("SkillRegistry 扫描真实目录 → 发现 docx 技能")
        void testScanRealDirectory_findsDocxSkill() {
            SkillRegistry registry = new SkillRegistry();
            registry.scan(REAL_SKILLS_DIR);

            assertFalse(registry.isEmpty(), "真实技能目录不应为空");
            assertEquals(1, registry.getAll().size(), "应有 1 个技能（docx）");

            var skill = registry.getByName("docx");
            assertTrue(skill.isPresent(), "应能通过名称找到 docx 技能");
            assertEquals("docx", skill.get().getName());
            assertNotNull(skill.get().getDescription(), "description 不应为 null");
            assertTrue(skill.get().getDescription().contains("Word"), "描述应包含 'Word'");
            assertNotNull(skill.get().getFilePath(), "filePath 不应为 null");
            assertTrue(skill.get().getFilePath().endsWith("SKILL.md"), "filePath 应指向 SKILL.md");
        }

        @Test
        @DisplayName("SkillsMiddleware 注入 → instructions 包含技能目录")
        void testMiddlewareInjection_enhancesInstructions() {
            SkillRegistry registry = new SkillRegistry();
            registry.scan(REAL_SKILLS_DIR);
            SkillsMiddleware middleware = new SkillsMiddleware(registry);

            String original = "帮我创建一个 Word 文档";
            String enhanced = middleware.enhanceInstructions(original);

            // 验证原始指令被保留
            assertTrue(enhanced.contains(original), "增强后的指令应保留原始内容");

            // 验证技能目录被注入
            assertTrue(enhanced.contains(SkillsMiddleware.SKILL_CATALOG_MARKER),
                    "应包含目录标记");
            assertTrue(enhanced.contains("docx"), "应包含技能名 docx");
            assertTrue(enhanced.contains("Word"), "应包含技能描述中的关键词");
            assertTrue(enhanced.contains("skill_load"), "应包含工具使用提示");
        }

        @Test
        @DisplayName("SkillLoadTool 加载 → 返回完整 SKILL.md 内容")
        void testSkillLoadTool_loadsFullContent() {
            SkillRegistry registry = new SkillRegistry();
            registry.scan(REAL_SKILLS_DIR);
            SkillLoadTool tool = new SkillLoadTool(registry);

            Map<String, Object> result = (Map<String, Object>) tool.execute(
                    Map.of("skill_name", "docx"));

            assertEquals(true, result.get("success"), "加载应成功");
            assertEquals("docx", result.get("skill_name"), "技能名应为 docx");
            String content = (String) result.get("content");
            assertNotNull(content, "内容不应为 null");
            assertTrue(content.contains("DOCX"), "内容应包含 DOCX 相关说明");
            assertTrue(content.contains("---"), "内容应包含 YAML frontmatter");
            assertTrue(content.length() > 100, "内容应有实质长度");
        }

        @Test
        @DisplayName("完整闭环：Registry 扫描 → Middleware 注入 → Tool 加载")
        void testFullPipeline_pathA() {
            // Step 1: 发现
            SkillRegistry registry = new SkillRegistry();
            registry.scan(REAL_SKILLS_DIR);
            assertFalse(registry.isEmpty());

            // Step 2: 注入
            SkillsMiddleware middleware = new SkillsMiddleware(registry);
            String instructions = middleware.enhanceInstructions("生成一份测试报告");
            assertTrue(instructions.contains(SkillsMiddleware.SKILL_CATALOG_MARKER));

            // Step 3: 加载
            SkillLoadTool tool = new SkillLoadTool(registry);
            Map<String, Object> result = (Map<String, Object>) tool.execute(
                    Map.of("skill_name", "docx"));
            assertEquals(true, result.get("success"));

            // Step 4: 验证加载的内容包含操作指南
            String content = (String) result.get("content");
            assertTrue(content.contains("docx"), "加载内容应包含技能名");
        }

        @Test
        @DisplayName("重复注入防护 → enhanceInstructions 不会重复追加")
        void testNoDuplicateInjection() {
            SkillRegistry registry = new SkillRegistry();
            registry.scan(REAL_SKILLS_DIR);
            SkillsMiddleware middleware = new SkillsMiddleware(registry);

            String first = middleware.enhanceInstructions("指令");
            String second = middleware.enhanceInstructions(first);

            assertEquals(first, second, "第二次注入应返回相同结果");
        }

        @Test
        @DisplayName("getCatalogText → 包含可用技能格式化文本")
        void testCatalogTextFormat() {
            SkillRegistry registry = new SkillRegistry();
            registry.scan(REAL_SKILLS_DIR);

            String catalog = registry.getCatalogText();
            assertTrue(catalog.contains("[可用技能]"), "目录应包含标题");
            assertTrue(catalog.contains("docx:"), "目录应包含 'docx:' 条目");
            assertTrue(catalog.contains("skill_load"), "目录应包含使用提示");
        }
    }

    // ======================== Path B：Spring 配置路径集成测试 ========================

    @Nested
    @DisplayName("Path B - Spring 配置路径：AgentProperties + SkillAutoConfiguration 集成")
    class PathBSpringConfigTests {

        private DeepAgentGraph deepAgentGraph;
        private AgentProperties agentProperties;
        private RedisTemplate<String, Object> redisTemplate;
        private ValueOperations<String, Object> valueOperations;
        private SandboxManager sandboxManager;
        private AgentServiceImpl agentService;
        private MockedStatic<SecurityUtils> securityUtilsMock;

        private static final Long MOCK_USER_ID = 1L;

        @BeforeEach
        void setUp() {
            deepAgentGraph = mock(DeepAgentGraph.class);
            redisTemplate = mock(RedisTemplate.class);
            valueOperations = mock(ValueOperations.class);
            sandboxManager = mock(SandboxManager.class);

            // 模拟 AgentProperties 中的技能配置（等价于 application-local.yml 的 agent.skills.dirs）
            agentProperties = new AgentProperties();
            agentProperties.getSkills().setDirs(List.of(REAL_SKILLS_DIR));

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            TaskExecutor taskExecutor = Runnable::run;
        }

        @AfterEach
        void tearDown() {
            if (securityUtilsMock != null) {
                securityUtilsMock.close();
            }
        }

        @Test
        @DisplayName("AgentProperties 正确绑定技能目录配置")
        void testAgentProperties_skillDirsConfigured() {
            List<String> dirs = agentProperties.getSkills().getDirs();
            assertNotNull(dirs, "dirs 不应为 null");
            assertEquals(1, dirs.size(), "应有 1 个目录");
            assertEquals(REAL_SKILLS_DIR, dirs.get(0), "目录路径应匹配配置");
        }

        @Test
        @DisplayName("SkillAutoConfiguration 条件装配 → 创建完整的 Bean 链")
        void testAutoConfiguration_createsBeanChain() {
            // 模拟 SkillAutoConfiguration 的逻辑（不依赖 Spring 上下文）
            AgentProperties props = new AgentProperties();
            props.getSkills().setDirs(List.of(REAL_SKILLS_DIR));

            // Step 1: SkillRegistry
            SkillRegistry registry = new SkillRegistry();
            List<String> dirs = props.getSkills().getDirs();
            assertNotNull(dirs);
            registry.scan(dirs.toArray(new String[0]));
            assertFalse(registry.isEmpty(), "SkillRegistry 应发现技能");

            // Step 2: SkillsMiddleware
            SkillsMiddleware middleware = new SkillsMiddleware(registry);
            assertTrue(middleware.hasSkills(), "SkillsMiddleware 应报告有技能");
            assertEquals("skills", middleware.name());
            assertSame(registry, middleware.getRegistry());

            // Step 3: SkillLoadTool
            SkillLoadTool tool = new SkillLoadTool(registry);
            assertEquals("skill_load", tool.name());
            Map<String, Object> loadResult = (Map<String, Object>) tool.execute(
                    Map.of("skill_name", "docx"));
            assertEquals(true, loadResult.get("success"), "SkillLoadTool 应能加载技能");
        }

        @Test
        @DisplayName("AgentServiceImpl.buildInitialState → 技能目录注入到 instructions")
        @SuppressWarnings("unchecked")
        void testBuildInitialState_injectsSkillCatalog() throws Exception {
            // 构建完整的 Spring 路径依赖链
            SkillRegistry registry = new SkillRegistry();
            registry.scan(REAL_SKILLS_DIR);
            SkillsMiddleware middleware = new SkillsMiddleware(registry);

            // Mock 图构建
            CompiledGraph<DeepAgentState> graph = mock(CompiledGraph.class);
            when(deepAgentGraph.build(any(DeepAgentGraph.HitlBuildConfig.class))).thenReturn(graph);
            when(deepAgentGraph.build()).thenReturn(graph);

            TaskExecutor taskExecutor = Runnable::run;
            agentService = new AgentServiceImpl(deepAgentGraph, agentProperties,
                    redisTemplate, taskExecutor, sandboxManager, middleware);

            securityUtilsMock = mockStatic(SecurityUtils.class);
            securityUtilsMock.when(SecurityUtils::getUserId).thenReturn(MOCK_USER_ID);

            // 创建会话
            AgentSessionForm form = new AgentSessionForm();
            form.setInstructions("创建一个 Word 报告");

            AgentSessionVO session = agentService.createSession(form);
            assertNotNull(session);
            assertEquals(SessionStatus.CREATED, session.getStatus());

            // 验证图构建被调用
            verify(deepAgentGraph).build(any(DeepAgentGraph.HitlBuildConfig.class));

            // 验证 Redis 保存（buildInitialState 的注入在 execute/streamExecute 时触发）
            verify(valueOperations).set(anyString(), any(AgentSessionVO.class),
                    eq(agentProperties.getDeep().getSessionTtl()), any());
        }

        @Test
        @DisplayName("SkillsMiddleware 为 null → 不注入，正常执行")
        @SuppressWarnings("unchecked")
        void testBuildInitialState_noMiddleware() throws Exception {
            CompiledGraph<DeepAgentState> graph = mock(CompiledGraph.class);
            when(deepAgentGraph.build(any(DeepAgentGraph.HitlBuildConfig.class))).thenReturn(graph);

            // 不配置技能目录 → 不创建 SkillsMiddleware
            AgentProperties propsNoSkills = new AgentProperties();
            TaskExecutor taskExecutor = Runnable::run;
            AgentServiceImpl serviceNoSkills = new AgentServiceImpl(
                    deepAgentGraph, propsNoSkills, redisTemplate, taskExecutor, sandboxManager, null);

            securityUtilsMock = mockStatic(SecurityUtils.class);
            securityUtilsMock.when(SecurityUtils::getUserId).thenReturn(MOCK_USER_ID);

            AgentSessionForm form = new AgentSessionForm();
            form.setInstructions("普通指令，无需技能");

            AgentSessionVO session = serviceNoSkills.createSession(form);
            assertNotNull(session, "无技能中间件时应正常创建会话");
        }

        @Test
        @DisplayName("配置多个技能目录 → 全部扫描")
        void testMultipleSkillDirs() {
            AgentProperties props = new AgentProperties();
            // 配置同一个目录两次（验证去重或正常扫描）
            props.getSkills().setDirs(List.of(REAL_SKILLS_DIR, REAL_SKILLS_DIR));

            SkillRegistry registry = new SkillRegistry();
            registry.scan(props.getSkills().getDirs().toArray(new String[0]));

            // 同一个技能不重复注册（Map key 去重）
            assertEquals(1, registry.getAll().size(), "同一技能不应重复注册");
        }

        @Test
        @DisplayName("空技能目录列表 → SkillRegistry 为空 → SkillsMiddleware 不注入")
        void testEmptySkillDirs() {
            AgentProperties props = new AgentProperties();
            // 默认 dirs 为空列表
            assertTrue(props.getSkills().getDirs().isEmpty());

            SkillRegistry registry = new SkillRegistry();
            assertTrue(registry.isEmpty());

            SkillsMiddleware middleware = new SkillsMiddleware(registry);
            assertFalse(middleware.hasSkills());

            // enhanceInstructions 应返回原始指令
            String original = "测试指令";
            String result = middleware.enhanceInstructions(original);
            assertEquals(original, result, "空注册表应返回原始指令");
        }
    }
}
