package com.hypersense.boot.framework.agents.service.impl;

import cn.hutool.core.util.StrUtil;
import com.hypersense.boot.agents.service.AgentSessionService;
import com.hypersense.boot.agents.service.impl.AgentServiceImpl;
import com.hypersense.boot.common.constant.RedisConstants;
import com.hypersense.boot.common.exception.BusinessException;
import com.hypersense.boot.framework.agents.config.AgentProperties;
import com.hypersense.boot.framework.agents.engine.DeepAgentGraph;
import com.hypersense.boot.framework.agents.enums.SessionStatus;
import com.hypersense.boot.framework.agents.enums.TodoStatus;
import com.hypersense.boot.framework.agents.form.AgentSessionForm;
import com.hypersense.boot.framework.agents.model.DeepAgentState;
import com.hypersense.boot.framework.agents.model.TodoItem;
import com.hypersense.boot.framework.agents.sandbox.SandboxManager;
import com.hypersense.boot.framework.agents.vo.AgentSessionVO;
import com.hypersense.boot.framework.security.util.SecurityUtils;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.RunnableConfig;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AgentServiceImpl 单元测试
 * <p>
 * 覆盖会话创建、同步执行、SSE 流式执行、会话查询等核心方法的正常与异常场景。
 * 通过 Mockito mock 外部依赖（RedisTemplate、DeepAgentGraph、SecurityUtils）。
 *
 * @author test
 */
class AgentServiceImplTest {

    private DeepAgentGraph deepAgentGraph;
    private AgentProperties agentProperties;
    private RedisTemplate<String, Object> redisTemplate;
    @SuppressWarnings("unchecked")
    private ValueOperations<String, Object> valueOperations;
    private SandboxManager sandboxManager;
    private AgentServiceImpl agentService;

    private MockedStatic<SecurityUtils> securityUtilsMock;

    private static final Long MOCK_USER_ID = 1L;

    @BeforeEach
    void setUp() {
        deepAgentGraph = mock(DeepAgentGraph.class);
        agentProperties = new AgentProperties();
        redisTemplate = mock(RedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        sandboxManager = mock(SandboxManager.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        TaskExecutor taskExecutor = Runnable::run; // 同步执行器（测试用）
        agentService = new AgentServiceImpl(deepAgentGraph, agentProperties, redisTemplate, taskExecutor, sandboxManager, null, null, null, null, null, null, mock(com.hypersense.boot.system.service.DesignSystemConfigService.class), mock(AgentSessionService.class));

        // Mock SecurityUtils.getUserId() 静态方法
        securityUtilsMock = mockStatic(SecurityUtils.class);
        securityUtilsMock.when(SecurityUtils::getUserId).thenReturn(MOCK_USER_ID);
    }

    @AfterEach
    void tearDown() {
        securityUtilsMock.close();
    }

    // ======================== 辅助方法 ========================

    /**
     * 构建测试用的 AgentSessionForm
     */
    private AgentSessionForm buildForm(String instructions) {
        AgentSessionForm form = new AgentSessionForm();
        form.setInstructions(instructions);
        return form;
    }

    /**
     * 模拟 Redis 中存在指定会话
     */
    private void mockRedisGetSession(AgentSessionVO session) {
        String key = StrUtil.format(RedisConstants.Agent.SESSION, session.getSessionId());
        when(valueOperations.get(key)).thenReturn(session);
    }

    /**
     * 模拟 Redis 中不存在指定会话
     */
    private void mockRedisGetSessionNull(String sessionId) {
        String key = StrUtil.format(RedisConstants.Agent.SESSION, sessionId);
        when(valueOperations.get(key)).thenReturn(null);
    }

    /**
     * 构建一个标准的测试用会话 VO
     */
    private AgentSessionVO buildTestSession(String sessionId, Long userId) {
        return AgentSessionVO.builder()
                .sessionId(sessionId)
                .userId(userId)
                .status(SessionStatus.CREATED)
                .todos(List.of())
                .files(Map.of())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Mock DeepAgentGraph.build() 返回一个 CompiledGraph
     */
    @SuppressWarnings("unchecked")
    private CompiledGraph<DeepAgentState> mockGraphBuild() throws Exception {
        CompiledGraph<DeepAgentState> graph = mock(CompiledGraph.class);
        when(deepAgentGraph.build(any(DeepAgentGraph.HitlBuildConfig.class))).thenReturn(graph);
        when(deepAgentGraph.build()).thenReturn(graph);
        return graph;
    }

    // ======================== createSession 测试 ========================

    @Nested
    @DisplayName("createSession - 创建会话")
    class CreateSessionTests {

        @Test
        @DisplayName("正常创建会话 - 返回有效 SessionVO")
        void testCreateSession_success() throws Exception {
            mockGraphBuild();
            AgentSessionForm form = buildForm("你是一个测试助手");

            AgentSessionVO result = agentService.createSession(form);

            // 验证返回值
            assertNotNull(result, "返回值不应为 null");
            assertNotNull(result.getSessionId(), "sessionId 不应为 null");
            assertEquals(16, result.getSessionId().length(), "sessionId 长度应为 16");
            assertEquals(MOCK_USER_ID, result.getUserId(), "userId 应匹配当前用户");
            assertEquals(SessionStatus.CREATED, result.getStatus(), "初始状态应为 CREATED");
            assertNotNull(result.getTodos(), "todos 不应为 null");
            assertTrue(result.getTodos().isEmpty(), "初始 todos 应为空");
            assertNotNull(result.getFiles(), "files 不应为 null");
            assertTrue(result.getFiles().isEmpty(), "初始 files 应为空");
            assertNotNull(result.getCreatedAt(), "createdAt 不应为 null");

            // 验证 Redis 写入
            verify(valueOperations).set(anyString(), any(AgentSessionVO.class),
                    eq(agentProperties.getDeep().getSessionTtl()), any());
        }

        @Test
        @DisplayName("正常创建会话 - 图构建被调用")
        void testCreateSession_graphBuilt() throws Exception {
            mockGraphBuild();

            agentService.createSession(buildForm("测试"));

            // HITL 变更后使用 build(HitlBuildConfig)
            verify(deepAgentGraph, times(1)).build(any(DeepAgentGraph.HitlBuildConfig.class));
        }

        @Test
        @DisplayName("异常 - 图构建失败抛出 BusinessException")
        void testCreateSession_graphBuildFails() throws Exception {
            when(deepAgentGraph.build()).thenThrow(new RuntimeException("图构建错误"));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> agentService.createSession(buildForm("测试")));

            assertTrue(ex.getMessage().contains("图构建失败"), "异常信息应包含 '图构建失败'");
        }

        @Test
        @DisplayName("异常 - 用户未登录")
        void testCreateSession_userNotLoggedIn() throws Exception {
            securityUtilsMock.when(SecurityUtils::getUserId).thenReturn(null);

            assertThrows(BusinessException.class,
                    () -> agentService.createSession(buildForm("测试")));
        }

        @Test
        @DisplayName("多次创建会话 - sessionId 应各不相同")
        void testCreateSession_uniqueSessionIds() throws Exception {
            mockGraphBuild();

            AgentSessionVO s1 = agentService.createSession(buildForm("助手1"));
            AgentSessionVO s2 = agentService.createSession(buildForm("助手2"));

            assertNotEquals(s1.getSessionId(), s2.getSessionId(), "两次创建的 sessionId 应不同");
        }
    }

    // ======================== execute 测试 ========================

    @Nested
    @DisplayName("execute - 同步执行")
    class ExecuteTests {

        @Test
        @DisplayName("正常执行 - 返回 COMPLETED 状态")
        @SuppressWarnings("unchecked")
        void testExecute_success() throws Exception {
            CompiledGraph<DeepAgentState> graph = mockGraphBuild();

            // 先创建会话
            AgentSessionVO session = agentService.createSession(buildForm("你是一个测试助手"));
            String sessionId = session.getSessionId();

            // 模拟 Redis 返回会话
            mockRedisGetSession(session);

            // 模拟图执行结果
            DeepAgentState finalState = mock(DeepAgentState.class);
            when(finalState.todos()).thenReturn(List.of());
            when(finalState.files()).thenReturn(Map.of("report.md", "# 测试报告"));
            when(finalState.finalResponse()).thenReturn(Optional.of("执行完成"));

            when(graph.invoke(anyMap(), any(RunnableConfig.class)))
                    .thenReturn(Optional.of(finalState));

            // 执行
            AgentSessionVO result = agentService.execute(sessionId, "写一个测试报告");

            // 验证
            assertEquals(SessionStatus.COMPLETED, result.getStatus(), "执行后状态应为 COMPLETED");
            assertEquals("执行完成", result.getFinalResponse(), "最终响应应匹配");
            assertNotNull(result.getFiles(), "files 不应为 null");
            assertTrue(result.getFiles().containsKey("report.md"), "应包含生成的文件");

            // 验证图被调用
            verify(graph).invoke(anyMap(), any(RunnableConfig.class));

            // 验证沙箱被销毁
            verify(sandboxManager).destroy(sessionId);
        }

        @Test
        @DisplayName("异常 - 会话不存在")
        void testExecute_sessionNotFound() throws Exception {
            mockGraphBuild();
            AgentSessionVO session = agentService.createSession(buildForm("测试"));
            mockRedisGetSessionNull(session.getSessionId());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> agentService.execute(session.getSessionId(), "测试"));

            assertTrue(ex.getMessage().contains("会话不存在"), "异常信息应包含 '会话不存在'");
        }

        @Test
        @DisplayName("异常 - 越权访问（不同用户）")
        void testExecute_unauthorizedUser() throws Exception {
            mockGraphBuild();
            AgentSessionVO session = agentService.createSession(buildForm("测试"));

            // 模拟另一个用户
            securityUtilsMock.when(SecurityUtils::getUserId).thenReturn(999L);
            mockRedisGetSession(session);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> agentService.execute(session.getSessionId(), "测试"));

            assertTrue(ex.getMessage().contains("无权访问"), "异常信息应包含 '无权访问'");
        }

        @Test
        @DisplayName("异常 - 图实例不存在（会话未创建图）")
        void testExecute_graphNotFound() {
            // 构建一个 session 但不创建图（直接模拟 Redis 返回）
            AgentSessionVO session = buildTestSession("fake-session-01", MOCK_USER_ID);
            mockRedisGetSession(session);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> agentService.execute("fake-session-01", "测试"));

            assertTrue(ex.getMessage().contains("图实例不存在"), "异常信息应包含 '图实例不存在'");
        }

        @Test
        @DisplayName("异常 - 图执行失败，状态标记为 FAILED")
        @SuppressWarnings("unchecked")
        void testExecute_graphExecutionFails() throws Exception {
            CompiledGraph<DeepAgentState> graph = mockGraphBuild();
            AgentSessionVO session = agentService.createSession(buildForm("测试"));
            String sessionId = session.getSessionId();

            // 第一次 getSession（validate）返回 session，execute 中更新后再次 getSession 也需要返回
            mockRedisGetSession(session);

            // 模拟图执行异常
            when(graph.invoke(anyMap(), any(RunnableConfig.class)))
                    .thenThrow(new RuntimeException("LLM API 超时"));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> agentService.execute(sessionId, "测试"));

            assertTrue(ex.getMessage().contains("执行失败"), "异常信息应包含 '执行失败'");

            // 验证状态被标记为 FAILED（通过 Redis set 的 ArgumentCaptor）
            ArgumentCaptor<AgentSessionVO> captor = ArgumentCaptor.forClass(AgentSessionVO.class);
            verify(valueOperations, atLeast(2)).set(eq(StrUtil.format(RedisConstants.Agent.SESSION, sessionId)),
                    captor.capture(), anyLong(), any());

            AgentSessionVO lastSaved = captor.getAllValues().get(captor.getAllValues().size() - 1);
            assertEquals(SessionStatus.FAILED, lastSaved.getStatus(), "失败后状态应标记为 FAILED");

            // 验证沙箱被销毁
            verify(sandboxManager).destroy(sessionId);
        }
    }

    // ======================== getSession 测试 ========================

    @Nested
    @DisplayName("getSession - 查询会话")
    class GetSessionTests {

        @Test
        @DisplayName("正常查询 - 返回会话信息")
        void testGetSession_success() throws Exception {
            mockGraphBuild();
            AgentSessionVO session = agentService.createSession(buildForm("测试"));
            mockRedisGetSession(session);

            AgentSessionVO result = agentService.getSession(session.getSessionId());

            assertNotNull(result, "返回值不应为 null");
            assertEquals(session.getSessionId(), result.getSessionId(), "sessionId 应匹配");
        }

        @Test
        @DisplayName("异常 - 会话不存在")
        void testGetSession_notFound() {
            mockRedisGetSessionNull("nonexistent-id");

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> agentService.getSession("nonexistent-id"));

            assertTrue(ex.getMessage().contains("会话不存在"), "异常信息应包含 '会话不存在'");
        }

        @Test
        @DisplayName("异常 - 越权访问")
        void testGetSession_unauthorized() throws Exception {
            mockGraphBuild();
            AgentSessionVO session = agentService.createSession(buildForm("测试"));

            // 切换到另一个用户
            securityUtilsMock.when(SecurityUtils::getUserId).thenReturn(999L);
            mockRedisGetSession(session);

            assertThrows(BusinessException.class,
                    () -> agentService.getSession(session.getSessionId()));
        }

        @Test
        @DisplayName("异常 - Redis 返回非 AgentSessionVO 类型")
        void testGetSession_invalidType() {
            String sessionId = "bad-type-session";
            String key = StrUtil.format(RedisConstants.Agent.SESSION, sessionId);
            when(valueOperations.get(key)).thenReturn("invalid data");

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> agentService.getSession(sessionId));

            assertTrue(ex.getMessage().contains("数据异常"), "异常信息应包含 '数据异常'");
        }
    }

    // ======================== getTodos 测试 ========================

    @Nested
    @DisplayName("getTodos - 查询 TODO 列表")
    class GetTodosTests {

        @Test
        @DisplayName("正常查询 - 返回 TODO 列表")
        void testGetTodos_success() throws Exception {
            mockGraphBuild();
            AgentSessionVO session = agentService.createSession(buildForm("测试"));

            List<TodoItem> todos = List.of(
                    TodoItem.builder().id("1").description("任务1").status(TodoStatus.COMPLETED).build(),
                    TodoItem.builder().id("2").description("任务2").status(TodoStatus.PENDING).build()
            );
            session.setTodos(todos);
            mockRedisGetSession(session);

            List<TodoItem> result = agentService.getTodos(session.getSessionId());

            assertNotNull(result, "返回值不应为 null");
            assertEquals(2, result.size(), "TODO 列表大小应为 2");
            assertEquals("任务1", result.get(0).getDescription());
            assertEquals(TodoStatus.COMPLETED, result.get(0).getStatus());
            assertEquals(TodoStatus.PENDING, result.get(1).getStatus());
        }

        @Test
        @DisplayName("空会话 - 返回空列表")
        void testGetTodos_empty() throws Exception {
            mockGraphBuild();
            AgentSessionVO session = agentService.createSession(buildForm("测试"));
            mockRedisGetSession(session);

            List<TodoItem> result = agentService.getTodos(session.getSessionId());

            assertNotNull(result, "返回值不应为 null");
            assertTrue(result.isEmpty(), "新会话的 TODO 列表应为空");
        }

        @Test
        @DisplayName("异常 - 会话不存在")
        void testGetTodos_sessionNotFound() {
            mockRedisGetSessionNull("nonexistent");

            assertThrows(BusinessException.class,
                    () -> agentService.getTodos("nonexistent"));
        }
    }

    // ======================== getFiles 测试 ========================

    @Nested
    @DisplayName("getFiles - 查询产物文件")
    class GetFilesTests {

        @Test
        @DisplayName("正常查询 - 返回文件映射")
        void testGetFiles_success() throws Exception {
            mockGraphBuild();
            AgentSessionVO session = agentService.createSession(buildForm("测试"));

            Map<String, String> files = Map.of(
                    "report.md", "# 测试报告",
                    "data.json", "{\"key\": \"value\"}"
            );
            session.setFiles(files);
            mockRedisGetSession(session);

            Map<String, String> result = agentService.getFiles(session.getSessionId());

            assertNotNull(result, "返回值不应为 null");
            assertEquals(2, result.size(), "文件数量应为 2");
            assertTrue(result.containsKey("report.md"), "应包含 report.md");
            assertTrue(result.containsKey("data.json"), "应包含 data.json");
        }

        @Test
        @DisplayName("空会话 - 返回空映射")
        void testGetFiles_empty() throws Exception {
            mockGraphBuild();
            AgentSessionVO session = agentService.createSession(buildForm("测试"));
            mockRedisGetSession(session);

            Map<String, String> result = agentService.getFiles(session.getSessionId());

            assertNotNull(result, "返回值不应为 null");
            assertTrue(result.isEmpty(), "新会话的文件映射应为空");
        }

        @Test
        @DisplayName("异常 - 会话不存在")
        void testGetFiles_sessionNotFound() {
            mockRedisGetSessionNull("nonexistent");

            assertThrows(BusinessException.class,
                    () -> agentService.getFiles("nonexistent"));
        }
    }

    // ======================== streamExecute 测试 ========================

    @Nested
    @DisplayName("streamExecute - SSE 流式执行")
    class StreamExecuteTests {

        @Test
        @DisplayName("正常返回 SseEmitter")
        @SuppressWarnings("unchecked")
        void testStreamExecute_returnsEmitter() throws Exception {
            CompiledGraph<DeepAgentState> graph = mockGraphBuild();
            AgentSessionVO session = agentService.createSession(buildForm("测试"));
            String sessionId = session.getSessionId();
            mockRedisGetSession(session);

            // 不 mock graph.stream()，异步线程中会失败但不影响主线程断言
            // 仅验证 SseEmitter 被创建并返回
            var emitter = agentService.streamExecute(sessionId, "测试");

            assertNotNull(emitter, "SseEmitter 不应为 null");

            // 等待异步线程执行（失败不影响测试）
            Thread.sleep(1000);
        }

        @Test
        @DisplayName("异常 - 会话不存在")
        void testStreamExecute_sessionNotFound() throws Exception {
            mockGraphBuild();
            AgentSessionVO session = agentService.createSession(buildForm("测试"));
            mockRedisGetSessionNull(session.getSessionId());

            assertThrows(BusinessException.class,
                    () -> agentService.streamExecute(session.getSessionId(), "测试"));
        }

        @Test
        @DisplayName("异常 - 图实例不存在")
        void testStreamExecute_graphNotFound() {
            AgentSessionVO session = buildTestSession("fake-session-02", MOCK_USER_ID);
            mockRedisGetSession(session);

            assertThrows(BusinessException.class,
                    () -> agentService.streamExecute("fake-session-02", "测试"));
        }
    }

    // ======================== Redis TTL 配置测试 ========================

    @Nested
    @DisplayName("TTL 配置 - 验证会话过期时间")
    class TtlConfigTests {

        @Test
        @DisplayName("创建会话时使用配置的 TTL")
        void testCreateSession_usesConfiguredTtl() throws Exception {
            mockGraphBuild();
            agentProperties.getDeep().setSessionTtl(3600L); // 1 小时

            agentService.createSession(buildForm("测试"));

            verify(valueOperations).set(anyString(), any(AgentSessionVO.class),
                    eq(3600L), any());
        }

        @Test
        @DisplayName("默认 TTL 为 1800 秒（30 分钟）")
        void testDefaultTtl() {
            assertEquals(1800L, agentProperties.getDeep().getSessionTtl(),
                    "默认 TTL 应为 1800 秒");
        }
    }
}
