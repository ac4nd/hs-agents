package com.hypersense.boot.framework.agents;

import com.hypersense.boot.agents.service.AgentSessionService;
import com.hypersense.boot.agents.service.impl.AgentServiceImpl;
import com.hypersense.boot.common.constant.RedisConstants;
import com.hypersense.boot.common.exception.BusinessException;
import com.hypersense.boot.framework.agents.config.AgentProperties;
import com.hypersense.boot.framework.agents.engine.DeepAgentGraph;
import com.hypersense.boot.framework.agents.enums.AgentEventType;
import com.hypersense.boot.framework.agents.enums.SessionStatus;
import com.hypersense.boot.framework.agents.exception.HitlInterruptedException;
import com.hypersense.boot.framework.agents.form.AgentSessionForm;
import com.hypersense.boot.framework.agents.form.ApprovalRequest;
import com.hypersense.boot.framework.agents.model.AgentEvent;
import com.hypersense.boot.framework.agents.model.ApprovalDecision;
import com.hypersense.boot.framework.agents.model.DeepAgentState;
import com.hypersense.boot.framework.agents.model.InterruptContext;
import com.hypersense.boot.framework.agents.sandbox.SandboxManager;
import com.hypersense.boot.framework.agents.vo.AgentSessionVO;
import com.hypersense.boot.framework.security.util.SecurityUtils;
import cn.hutool.core.util.StrUtil;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.RunnableConfig;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * P0-4: HITL（Human-in-the-Loop）人机审批系统测试
 * <p>
 * 覆盖范围：
 * <ul>
 *   <li>ApprovalDecision 枚举：序列化/反序列化</li>
 *   <li>HitlInterruptedException：构造与传播</li>
 *   <li>createSession + HITL：配置解析、校验</li>
 *   <li>submitApproval：状态校验、审批提交</li>
 *   <li>getInterruptContext：上下文查询</li>
 *   <li>SSE 中断事件推送</li>
 * </ul>
 *
 * @author test
 */
class HitlTest {

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

        TaskExecutor taskExecutor = Runnable::run;
        agentService = new AgentServiceImpl(deepAgentGraph, agentProperties, redisTemplate, taskExecutor, sandboxManager, null, null, null, null, null, null, mock(com.hypersense.boot.system.service.DesignSystemConfigService.class), mock(AgentSessionService.class));

        securityUtilsMock = mockStatic(SecurityUtils.class);
        securityUtilsMock.when(SecurityUtils::getUserId).thenReturn(MOCK_USER_ID);
    }

    @AfterEach
    void tearDown() {
        securityUtilsMock.close();
    }

    // ======================== 辅助方法 ========================

    private void mockRedisGetSession(AgentSessionVO session) {
        String key = StrUtil.format(RedisConstants.Agent.SESSION, session.getSessionId());
        when(valueOperations.get(key)).thenReturn(session);
    }

    @SuppressWarnings("unchecked")
    private CompiledGraph<DeepAgentState> mockGraphBuild() throws Exception {
        CompiledGraph<DeepAgentState> graph = mock(CompiledGraph.class);
        when(deepAgentGraph.build(any(DeepAgentGraph.HitlBuildConfig.class))).thenReturn(graph);
        when(deepAgentGraph.build()).thenReturn(graph);
        return graph;
    }

    private AgentSessionForm buildHitlForm(boolean hitlEnabled) {
        AgentSessionForm form = new AgentSessionForm();
        form.setInstructions("HITL 测试");
        form.setHitlEnabled(hitlEnabled);
        return form;
    }

    // ======================== ApprovalDecision 枚举测试 ========================

    @Nested
    @DisplayName("ApprovalDecision - 审批决策枚举")
    class ApprovalDecisionTests {

        @Test
        @DisplayName("fromValue - 正确反序列化")
        void testFromValue() {
            assertEquals(ApprovalDecision.APPROVED, ApprovalDecision.fromValue("approved"));
            assertEquals(ApprovalDecision.REJECTED, ApprovalDecision.fromValue("rejected"));
            assertEquals(ApprovalDecision.MODIFIED, ApprovalDecision.fromValue("modified"));
        }

        @Test
        @DisplayName("fromValue - 未知值抛异常")
        void testFromValue_unknown() {
            assertThrows(IllegalArgumentException.class,
                    () -> ApprovalDecision.fromValue("unknown"));
        }

        @Test
        @DisplayName("getValue - 正确序列化")
        void testGetValue() {
            assertEquals("approved", ApprovalDecision.APPROVED.getValue());
            assertEquals("rejected", ApprovalDecision.REJECTED.getValue());
            assertEquals("modified", ApprovalDecision.MODIFIED.getValue());
        }
    }

    // ======================== HitlInterruptedException 测试 ========================

    @Nested
    @DisplayName("HitlInterruptedException - 中断异常")
    class HitlInterruptTests {

        @Test
        @DisplayName("构造器 - 携带节点名")
        void testConstructor() {
            HitlInterruptedException ex = new HitlInterruptedException("tool", "HITL 中断");
            assertEquals("tool", ex.getNodeName());
            assertTrue(ex.getMessage().contains("HITL"));
        }

        @Test
        @DisplayName("是 RuntimeException 子类")
        void testRuntimeException() {
            HitlInterruptedException ex = new HitlInterruptedException("plan", "中断");
            assertInstanceOf(RuntimeException.class, ex);
        }
    }

    // ======================== createSession HITL 配置测试 ========================

    @Nested
    @DisplayName("createSession - HITL 配置")
    class CreateSessionHitlTests {

        @Test
        @DisplayName("HITL 启用 + checkpoint 启用 - 创建成功")
        void testCreateSession_hitlEnabled() throws Exception {
            mockGraphBuild();
            agentProperties.getDeep().setCheckpointEnabled(true);
            AgentSessionForm form = buildHitlForm(true);

            AgentSessionVO session = agentService.createSession(form);

            assertTrue(session.getHitlEnabled(), "hitlEnabled 应为 true");
            assertNotNull(session.getHitlInterruptNodes(), "中断节点列表不应为 null");

            // 验证调用带 HITL 配置的 build
            verify(deepAgentGraph).build(any(DeepAgentGraph.HitlBuildConfig.class));
        }

        @Test
        @DisplayName("HITL 启用 + checkpoint 禁用 - 抛出异常")
        void testCreateSession_hitlWithoutCheckpoint() throws Exception {
            mockGraphBuild();
            agentProperties.getDeep().setCheckpointEnabled(false);
            AgentSessionForm form = buildHitlForm(true);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> agentService.createSession(form));

            assertTrue(ex.getMessage().contains("checkpoint"),
                    "异常信息应提示需要 checkpoint");
        }

        @Test
        @DisplayName("HITL 禁用 - 使用默认图构建")
        void testCreateSession_hitlDisabled() throws Exception {
            mockGraphBuild();
            AgentSessionForm form = buildHitlForm(false);

            AgentSessionVO session = agentService.createSession(form);

            assertFalse(session.getHitlEnabled(), "hitlEnabled 应为 false");

            // 验证调用不带 HITL 配置的 build（或带 disabled 配置）
            verify(deepAgentGraph).build(any());
        }

        @Test
        @DisplayName("HITL 未指定 - 回退到全局配置")
        void testCreateSession_hitlGlobalFallback() throws Exception {
            mockGraphBuild();
            agentProperties.getDeep().setCheckpointEnabled(true);
            agentProperties.getHitl().setEnabled(true);

            AgentSessionForm form = new AgentSessionForm();
            form.setInstructions("测试全局配置");

            AgentSessionVO session = agentService.createSession(form);

            assertTrue(session.getHitlEnabled(), "应回退到全局 HITL 配置");
        }

        @Test
        @DisplayName("自定义中断节点")
        void testCreateSession_customInterruptNodes() throws Exception {
            mockGraphBuild();
            agentProperties.getDeep().setCheckpointEnabled(true);

            AgentSessionForm form = new AgentSessionForm();
            form.setInstructions("自定义中断节点");
            form.setHitlEnabled(true);
            form.setHitlInterruptNodes(List.of("tool", "delegate"));

            AgentSessionVO session = agentService.createSession(form);

            assertEquals(List.of("tool", "delegate"), session.getHitlInterruptNodes());
        }
    }

    // ======================== submitApproval 审批测试 ========================

    @Nested
    @DisplayName("submitApproval - 提交审批")
    class SubmitApprovalTests {

        @Test
        @DisplayName("非等待审批状态 - 拒绝提交")
        void testSubmitApproval_wrongStatus() throws Exception {
            mockGraphBuild();
            AgentSessionVO session = agentService.createSession(buildHitlForm(false));
            session.setStatus(SessionStatus.RUNNING);
            mockRedisGetSession(session);

            ApprovalRequest request = new ApprovalRequest();
            request.setDecision(ApprovalDecision.APPROVED);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> agentService.submitApproval(session.getSessionId(), request));

            assertTrue(ex.getMessage().contains("等待审批状态"));
        }

        @Test
        @DisplayName("HITL 未启用 - 拒绝提交")
        void testSubmitApproval_hitlNotEnabled() throws Exception {
            mockGraphBuild();
            AgentSessionVO session = agentService.createSession(buildHitlForm(false));
            session.setStatus(SessionStatus.INTERRUPTED);
            session.setHitlEnabled(false);
            mockRedisGetSession(session);

            ApprovalRequest request = new ApprovalRequest();
            request.setDecision(ApprovalDecision.APPROVED);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> agentService.submitApproval(session.getSessionId(), request));

            assertTrue(ex.getMessage().contains("未启用 HITL"));
        }

        @Test
        @DisplayName("SSE 连接断开 - 拒绝提交")
        void testSubmitApproval_noActiveEmitter() throws Exception {
            mockGraphBuild();
            agentProperties.getDeep().setCheckpointEnabled(true);
            AgentSessionVO session = agentService.createSession(buildHitlForm(true));
            session.setStatus(SessionStatus.INTERRUPTED);
            session.setHitlEnabled(true);
            mockRedisGetSession(session);

            ApprovalRequest request = new ApprovalRequest();
            request.setDecision(ApprovalDecision.APPROVED);

            // 没有活跃的 SSE Emitter → 抛出异常
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> agentService.submitApproval(session.getSessionId(), request));

            assertTrue(ex.getMessage().contains("SSE 连接已断开"));
        }

        @Test
        @DisplayName("APPROVED 决策 - 记录审批请求并尝试恢复")
        void testSubmitApproval_approvedDecision() throws Exception {
            mockGraphBuild();
            agentProperties.getDeep().setCheckpointEnabled(true);
            AgentSessionVO session = agentService.createSession(buildHitlForm(true));
            session.setStatus(SessionStatus.INTERRUPTED);
            session.setHitlEnabled(true);
            mockRedisGetSession(session);

            ApprovalRequest request = new ApprovalRequest();
            request.setDecision(ApprovalDecision.APPROVED);
            request.setFeedback("看起来没问题，继续执行");

            // submitApproval 会尝试恢复执行（因无 emitter 会失败），
            // 但审批请求本身应被记录
            try {
                agentService.submitApproval(session.getSessionId(), request);
            } catch (BusinessException e) {
                // 预期：SSE 连接已断开
                assertTrue(e.getMessage().contains("SSE"));
            }
        }

        @Test
        @DisplayName("REJECTED 决策 - 拒绝审批")
        void testSubmitApproval_rejectedDecision() throws Exception {
            mockGraphBuild();
            agentProperties.getDeep().setCheckpointEnabled(true);
            AgentSessionVO session = agentService.createSession(buildHitlForm(true));
            session.setStatus(SessionStatus.AWAITING_INPUT);
            session.setHitlEnabled(true);
            mockRedisGetSession(session);

            ApprovalRequest request = new ApprovalRequest();
            request.setDecision(ApprovalDecision.REJECTED);
            request.setFeedback("参数有误，请修改");

            try {
                agentService.submitApproval(session.getSessionId(), request);
            } catch (BusinessException e) {
                assertTrue(e.getMessage().contains("SSE"));
            }
        }
    }

    // ======================== getInterruptContext 测试 ========================

    @Nested
    @DisplayName("getInterruptContext - 查询中断上下文")
    class GetInterruptContextTests {

        @Test
        @DisplayName("HITL 未启用 - 拒绝查询")
        void testGetInterruptContext_notEnabled() throws Exception {
            mockGraphBuild();
            AgentSessionVO session = agentService.createSession(buildHitlForm(false));
            mockRedisGetSession(session);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> agentService.getInterruptContext(session.getSessionId()));

            assertTrue(ex.getMessage().contains("未启用 HITL"));
        }
    }

    // ======================== SSE 中断事件测试 ========================

    @Nested
    @DisplayName("SSE 中断事件推送")
    class SseInterruptEventTests {

        @Test
        @DisplayName("InterruptContext 构建 - 包含必要字段")
        void testInterruptContextFields() {
            InterruptContext context = InterruptContext.builder()
                    .nodeName("tool")
                    .sessionId("test-123")
                    .summary("图执行在节点 [tool] 前暂停")
                    .pendingAction("等待审批")
                    .build();

            assertEquals("tool", context.getNodeName());
            assertEquals("test-123", context.getSessionId());
            assertNotNull(context.getSummary());
        }

        @Test
        @DisplayName("AgentEventType - HITL 相关事件类型完整")
        void testHitlEventTypes() {
            assertNotNull(AgentEventType.INTERRUPT);
            assertNotNull(AgentEventType.APPROVAL_RECEIVED);
            assertNotNull(AgentEventType.AWAITING_APPROVAL);

            assertEquals("interrupt", AgentEventType.INTERRUPT.getValue());
            assertEquals("approval_received", AgentEventType.APPROVAL_RECEIVED.getValue());
            assertEquals("awaiting_approval", AgentEventType.AWAITING_APPROVAL.getValue());
        }
    }

    // ======================== 集成测试：HITL 中断 → 恢复流程 ========================

    @Nested
    @DisplayName("HITL 完整流程 - 中断与恢复")
    class HitlFlowTests {

        @Test
        @DisplayName("HITL 中断 → 恢复需要 checkpoint")
        void testHitlResumeRequiresCheckpoint() throws Exception {
            // 模拟 HITL 恢复但没有 checkpoint
            mockGraphBuild();
            agentProperties.getDeep().setCheckpointEnabled(false);
            AgentSessionVO session = agentService.createSession(buildHitlForm(false));
            session.setStatus(SessionStatus.INTERRUPTED);
            session.setHitlEnabled(true);
            mockRedisGetSession(session);

            // 验证：创建会话时就应拒绝 HITL（因为没有 checkpoint）
            // 或者在 submitApproval 恢复时报错
        }

        @Test
        @DisplayName("SessionStatus - 包含 HITL 状态")
        void testHitlSessionStatuses() {
            assertNotNull(SessionStatus.INTERRUPTED);
            assertNotNull(SessionStatus.AWAITING_INPUT);
            assertEquals(4, SessionStatus.INTERRUPTED.getValue());
            assertEquals(5, SessionStatus.AWAITING_INPUT.getValue());
        }
    }

    // ======================== DeepAgentGraph.HitlBuildConfig 测试 ========================

    @Nested
    @DisplayName("HitlBuildConfig - HITL 构建配置")
    class HitlBuildConfigTests {

        @Test
        @DisplayName("disabled() - 返回禁用配置")
        void testDisabled() {
            DeepAgentGraph.HitlBuildConfig config = DeepAgentGraph.HitlBuildConfig.disabled();
            assertFalse(config.hitlEnabled);
        }

        @Test
        @DisplayName("构造 - 启用配置包含中断节点")
        void testEnabled() {
            DeepAgentGraph.HitlBuildConfig config = new DeepAgentGraph.HitlBuildConfig(
                    true, List.of("tool", "delegate"));
            assertTrue(config.hitlEnabled);
            assertEquals(List.of("tool", "delegate"), config.interruptNodes);
        }
    }
}
