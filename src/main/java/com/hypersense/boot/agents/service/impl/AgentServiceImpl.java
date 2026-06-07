package com.hypersense.boot.agents.service.impl;

import cn.hutool.core.util.StrUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hypersense.boot.common.constant.RedisConstants;
import com.hypersense.boot.common.exception.BusinessException;
import com.hypersense.boot.framework.agents.engine.DeepAgentGraph;
import com.hypersense.boot.framework.agents.engine.SubAgentEventBus;
import com.hypersense.boot.framework.agents.enums.AgentEventType;
import com.hypersense.boot.framework.agents.enums.SessionStatus;
import com.hypersense.boot.framework.agents.form.AgentSessionForm;
import com.hypersense.boot.framework.agents.form.ApprovalRequest;
import com.hypersense.boot.framework.agents.model.AgentEvent;
import com.hypersense.boot.framework.agents.model.DeepAgentState;
import com.hypersense.boot.framework.agents.model.InterruptContext;
import com.hypersense.boot.framework.agents.model.TodoItem;
import com.hypersense.boot.framework.agents.sandbox.SandboxManager;
import com.hypersense.boot.agents.service.AgentService;
import com.hypersense.boot.framework.agents.skill.SkillsMiddleware;
import com.hypersense.boot.framework.agents.vo.AgentSessionVO;
import com.hypersense.boot.framework.agents.config.AgentProperties;
import com.hypersense.boot.framework.security.util.SecurityUtils;
import com.hypersense.boot.framework.tenant.TenantContextHolder;
import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.RunnableConfig;

import static org.bsc.langgraph4j.StateGraph.END;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Agent 服务实现
 * <p>
 * 会话数据通过 Redis 持久化，图实例保留在本地内存（不可序列化）。
 * 基于 userId 实现安全鉴权，防止越权访问。
 * </p>
 *
 * @author Claude
 * @since 2026/5/15
 */
@Slf4j
@Service
public class AgentServiceImpl implements AgentService {

    private static final String METADATA_USER_ID = "userId";

    private final DeepAgentGraph deepAgentGraph;
    private final AgentProperties agentProperties;
    private final RedisTemplate<String, Object> redisTemplate;
    private final org.springframework.core.task.TaskExecutor taskExecutor;
    private final SandboxManager sandboxManager;
    private final SkillsMiddleware skillsMiddleware;

    /** 编译图缓存：sessionId → CompiledGraph（Caffeine 自动过期，避免内存泄漏） */
    private final Cache<String, CompiledGraph<DeepAgentState>> graphCache;

    /** 活跃 SSE Emitter：sessionId → SseEmitter（HITL 中断时保持存活） */
    private final ConcurrentHashMap<String, SseEmitter> activeEmitters = new ConcurrentHashMap<>();

    public AgentServiceImpl(DeepAgentGraph deepAgentGraph,
                            AgentProperties agentProperties,
                            RedisTemplate<String, Object> redisTemplate,
                            @Qualifier("agentTaskExecutor") org.springframework.core.task.TaskExecutor taskExecutor,
                            SandboxManager sandboxManager,
                            @org.springframework.lang.Nullable SkillsMiddleware skillsMiddleware) {
        this.deepAgentGraph = deepAgentGraph;
        this.agentProperties = agentProperties;
        this.redisTemplate = redisTemplate;
        this.taskExecutor = taskExecutor;
        this.sandboxManager = sandboxManager;
        this.skillsMiddleware = skillsMiddleware;
        this.graphCache = Caffeine.newBuilder()
                .expireAfterAccess(agentProperties.getDeep().getSessionTtl(), TimeUnit.SECONDS)
                .maximumSize(100)
                .removalListener((key, value, cause) ->
                        log.debug("graphCache eviction: sessionId={}, cause={}", key, cause))
                .build();
    }

    // ========== 会话操作 ==========

    @Override
    public AgentSessionVO createSession(AgentSessionForm form) {
        Long currentUserId = getCurrentUserId();
        String sessionId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        // 解析 HITL 配置
        boolean hitlEnabled = resolveHitlEnabled(form);
        List<String> hitlInterruptNodes = resolveHitlInterruptNodes(form);

        // HITL 前置条件校验：必须启用 checkpoint
        if (hitlEnabled && !Boolean.TRUE.equals(agentProperties.getDeep().getCheckpointEnabled())) {
            throw new BusinessException("启用 HITL 审批需要配置 agent.deep.checkpoint-enabled=true");
        }

        AgentSessionVO session = AgentSessionVO.builder()
                .sessionId(sessionId)
                .userId(currentUserId)
                .status(SessionStatus.CREATED)
                .todos(List.of())
                .files(Map.of())
                .enabledTools(form.getEnabledTools())
                .hitlEnabled(hitlEnabled)
                .hitlInterruptNodes(hitlInterruptNodes)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        saveSession(session);

        // 预构建编译图（根据 HITL 配置）
        try {
            DeepAgentGraph.HitlBuildConfig hitlConfig = hitlEnabled
                    ? new DeepAgentGraph.HitlBuildConfig(true, hitlInterruptNodes)
                    : DeepAgentGraph.HitlBuildConfig.disabled();
            CompiledGraph<DeepAgentState> graph = deepAgentGraph.build(hitlConfig);
            graphCache.put(sessionId, graph);
        } catch (Exception e) {
            throw new BusinessException("Agent 图构建失败: " + e.getMessage());
        }

        log.info("创建 Agent 会话: sessionId={}, userId={}, hitlEnabled={}, instructions={}",
                sessionId, currentUserId, hitlEnabled, form.getInstructions());
        return session;
    }

    @Override
    public AgentSessionVO execute(String sessionId, String userInput) {
        AgentSessionVO session = getAndValidateSession(sessionId);
        CompiledGraph<DeepAgentState> graph = getGraphOrThrow(sessionId);

        // 更新会话状态
        session.setStatus(SessionStatus.RUNNING);
        session.setUpdatedAt(LocalDateTime.now());
        saveSession(session);

        try {
            // 构建初始状态
            Map<String, Object> initialState = buildInitialState(sessionId, userInput, session.getEnabledTools());

            // 配置运行时参数：sessionId 作为 threadId，userId 存入 metadata
            RunnableConfig config = buildConfig(sessionId, session.getUserId());

            // 执行图
            Optional<DeepAgentState> resultOpt = graph.invoke(initialState, config);
            DeepAgentState finalState = resultOpt.orElseThrow(
                    () -> new BusinessException("Agent 执行返回空结果"));

            // 更新会话信息
            session.setTodos(finalState.todos());
            session.setFiles(finalState.files());
            session.setStatus(SessionStatus.COMPLETED);
            session.setUpdatedAt(LocalDateTime.now());

            Optional<String> finalResponse = finalState.finalResponse();
            finalResponse.ifPresent(session::setFinalResponse);

            saveSession(session);
            graphCache.invalidate(sessionId);
            sandboxManager.destroy(sessionId);
            log.info("Agent 会话执行完成: sessionId={}, userId={}", sessionId, session.getUserId());
        } catch (Exception e) {
            session.setStatus(SessionStatus.FAILED);
            session.setUpdatedAt(LocalDateTime.now());
            saveSession(session);
            graphCache.invalidate(sessionId);
            sandboxManager.destroy(sessionId);
            log.error("Agent 会话执行失败: sessionId={}, userId={}", sessionId, session.getUserId(), e);
            throw new BusinessException("Agent 执行失败: " + e.getMessage());
        }

        return session;
    }

    @Override
    public SseEmitter streamExecute(String sessionId, String userInput) {
        AgentSessionVO session = getAndValidateSession(sessionId);
        CompiledGraph<DeepAgentState> graph = getGraphOrThrow(sessionId);
        Long sessionUserId = session.getUserId();

        // SSE 超时与 session TTL 对齐，避免审批期间 emitter 提前过期
        long emitterTimeout = agentProperties.getDeep().getSessionTtl() * 1000L;
        SseEmitter emitter = new SseEmitter(emitterTimeout);
        activeEmitters.put(sessionId, emitter);

        emitter.onCompletion(() -> activeEmitters.remove(sessionId));
        emitter.onTimeout(() -> {
            activeEmitters.remove(sessionId);
            // 超时时更新 session 状态，避免状态不一致
            try {
                AgentSessionVO s = getAndValidateSession(sessionId);
                if (s.getStatus() == SessionStatus.INTERRUPTED
                        || s.getStatus() == SessionStatus.AWAITING_INPUT) {
                    s.setStatus(SessionStatus.FAILED);
                    s.setUpdatedAt(LocalDateTime.now());
                    saveSession(s);
                    graphCache.invalidate(sessionId);
                    sandboxManager.destroy(sessionId);
                    log.warn("SSE Emitter 超时，HITL 会话已失效: sessionId={}", sessionId);
                }
            } catch (Exception ignored) {
            }
        });
        emitter.onError(e -> activeEmitters.remove(sessionId));

        // 更新会话状态
        session.setStatus(SessionStatus.RUNNING);
        session.setUpdatedAt(LocalDateTime.now());
        saveSession(session);

        // 异步执行（线程池管理）
        taskExecutor.execute(() -> {
            // 设置子 Agent 事件总线：子 Agent 执行事件通过此消费者冒泡到 SSE
            SubAgentEventBus.set(event -> {
                try {
                    emitter.send(SseEmitter.event().name("agent_event").data(event));
                } catch (Exception e) {
                    log.warn("SSE 子 Agent 事件推送失败: {}", e.getMessage());
                }
            });

            try {
                Map<String, Object> initialState = buildInitialState(sessionId, userInput, session.getEnabledTools());

                // HITL 启用时注入状态标记
                if (Boolean.TRUE.equals(session.getHitlEnabled())) {
                    initialState.put(DeepAgentState.HITL_ENABLED, true);
                }

                RunnableConfig config = buildConfig(sessionId, sessionUserId);

                // 流式执行
                var generator = graph.stream(initialState, config);
                boolean hitlEnabled = Boolean.TRUE.equals(session.getHitlEnabled());
                String lastNode = null;
                boolean reachedEnd = false;

                for (var nodeOutput : generator) {
                    lastNode = nodeOutput.node();
                    // 推送每个节点执行事件
                    AgentEvent event = AgentEvent.builder()
                            .type(AgentEventType.NODE_EXECUTION)
                            .message("节点执行: " + nodeOutput.node())
                            .data(nodeOutput.state())
                            .timestamp(System.currentTimeMillis())
                            .build();
                    emitter.send(SseEmitter.event().name("agent_event").data(event));

                    // 检查是否到达 END
                    if (END.equals(nodeOutput.node())) {
                        reachedEnd = true;
                    }
                }

                // 中断检测：generator 结束但未到达 END → HITL 中断
                if (!reachedEnd && lastNode != null && hitlEnabled) {
                    handleInterrupt(sessionId, emitter, lastNode);
                    return; // 不关闭 emitter，等待审批
                }

                // 正常完成
                AgentEvent completeEvent = AgentEvent.builder()
                        .type(AgentEventType.FINAL_RESPONSE)
                        .message("执行完成")
                        .timestamp(System.currentTimeMillis())
                        .build();
                emitter.send(SseEmitter.event().name("agent_event").data(completeEvent));
                emitter.complete();

                // 更新会话状态到 Redis
                AgentSessionVO latestSession = getAndValidateSession(sessionId);
                latestSession.setStatus(SessionStatus.COMPLETED);
                latestSession.setUpdatedAt(LocalDateTime.now());
                saveSession(latestSession);
                graphCache.invalidate(sessionId);
                sandboxManager.destroy(sessionId);
                activeEmitters.remove(sessionId);
                log.info("Agent SSE 流式执行完成: sessionId={}, userId={}", sessionId, sessionUserId);
            } catch (Exception e) {
                log.error("Agent SSE 流式执行失败: sessionId={}, userId={}", sessionId, sessionUserId, e);
                try {
                    AgentEvent errorEvent = AgentEvent.builder()
                            .type(AgentEventType.ERROR)
                            .message("执行失败: " + e.getMessage())
                            .timestamp(System.currentTimeMillis())
                            .build();
                    emitter.send(SseEmitter.event().name("agent_event").data(errorEvent));
                } catch (Exception ignored) {
                }
                // 更新失败状态到 Redis
                try {
                    AgentSessionVO latestSession = getAndValidateSession(sessionId);
                    latestSession.setStatus(SessionStatus.FAILED);
                    latestSession.setUpdatedAt(LocalDateTime.now());
                    saveSession(latestSession);
                    graphCache.invalidate(sessionId);
                    sandboxManager.destroy(sessionId);
                } catch (Exception ignored) {
                }
                activeEmitters.remove(sessionId);
                emitter.completeWithError(e);
            } finally {
                SubAgentEventBus.remove();
            }
        });

        return emitter;
    }

    @Override
    public AgentSessionVO getSession(String sessionId) {
        return getAndValidateSession(sessionId);
    }

    @Override
    public List<TodoItem> getTodos(String sessionId) {
        return getAndValidateSession(sessionId).getTodos();
    }

    @Override
    public Map<String, String> getFiles(String sessionId) {
        return getAndValidateSession(sessionId).getFiles();
    }

    // ========== HITL 审批方法 ==========

    @Override
    public AgentSessionVO submitApproval(String sessionId, ApprovalRequest request) {
        AgentSessionVO session = getAndValidateSession(sessionId);

        // 校验：会话必须处于等待审批状态
        if (session.getStatus() != SessionStatus.INTERRUPTED
                && session.getStatus() != SessionStatus.AWAITING_INPUT) {
            throw new BusinessException("会话当前不在等待审批状态，无法提交审批");
        }

        // 校验：HITL 必须启用
        if (!Boolean.TRUE.equals(session.getHitlEnabled())) {
            throw new BusinessException("该会话未启用 HITL 审批");
        }

        // 记录审批请求
        session.setPendingApproval(request);
        session.setUpdatedAt(LocalDateTime.now());
        saveSession(session);

        log.info("HITL 审批已接收: sessionId={}, decision={}", sessionId, request.getDecision());

        // 恢复执行
        resumeExecution(sessionId, session, request);

        return getAndValidateSession(sessionId);
    }

    @Override
    public InterruptContext getInterruptContext(String sessionId) {
        AgentSessionVO session = getAndValidateSession(sessionId);

        if (!Boolean.TRUE.equals(session.getHitlEnabled())) {
            throw new BusinessException("该会话未启用 HITL");
        }

        return session.getInterruptContext();
    }

    // ========== 私有方法 ==========

    /**
     * 获取当前登录用户 ID
     */
    private Long getCurrentUserId() {
        Long userId = SecurityUtils.getUserId();
        if (userId == null) {
            throw new BusinessException("未获取到当前用户信息，请先登录");
        }
        return userId;
    }

    /**
     * 构建 RunnableConfig：sessionId 作为 threadId，userId 存入 metadata
     *
     * @param sessionId 会话 ID（同时作为 threadId）
     * @param userId    用户 ID（存入 metadata，供节点访问）
     */
    private RunnableConfig buildConfig(String sessionId, Long userId) {
        return RunnableConfig.builder()
                .threadId(sessionId)
                .putMetadata(METADATA_USER_ID, userId)
                .build();
    }

    /**
     * 构建 Redis Key
     */
    private String sessionKey(String sessionId) {
        return StrUtil.format(RedisConstants.Agent.SESSION, sessionId);
    }

    /**
     * 保存会话到 Redis（带 TTL）
     */
    private void saveSession(AgentSessionVO session) {
        String key = sessionKey(session.getSessionId());
        long ttl = agentProperties.getDeep().getSessionTtl();
        redisTemplate.opsForValue().set(key, session, ttl, TimeUnit.SECONDS);
    }

    /**
     * 从 Redis 获取会话并校验用户归属（安全鉴权）
     * <p>
     * 防止用户通过篡改 sessionId 窥探到别人的聊天隐私。
     * </p>
     */
    private AgentSessionVO getAndValidateSession(String sessionId) {
        String key = sessionKey(sessionId);
        Object value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            throw new BusinessException("Agent 会话不存在: " + sessionId);
        }
        if (!(value instanceof AgentSessionVO session)) {
            throw new BusinessException("Agent 会话数据异常: " + sessionId);
        }

        // 安全鉴权：校验会话归属
        Long currentUserId = getCurrentUserId();
        if (session.getUserId() != null && !session.getUserId().equals(currentUserId)) {
            log.warn("越权访问检测: userId={} 尝试访问 sessionId={}（属于 userId={}）",
                    currentUserId, sessionId, session.getUserId());
            throw new BusinessException("无权访问该会话");
        }

        return session;
    }

    /**
     * 构建图执行的初始状态（包含会话 ID、消息列表和启用的工具）
     * <p>
     * 如果 SkillsMiddleware 已注入（通过 SkillAutoConfiguration 条件装配），
     * 会将技能目录追加到 instructions 中。
     * </p>
     */
    private Map<String, Object> buildInitialState(String sessionId, String userInput, List<String> enabledTools) {
        Map<String, Object> initialState = new HashMap<>();
        initialState.put(DeepAgentState.SESSION_ID, sessionId);
        // 技能目录注入
        String instructions = (skillsMiddleware != null && skillsMiddleware.hasSkills())
                ? skillsMiddleware.enhanceInstructions(userInput)
                : userInput;
        initialState.put(DeepAgentState.INSTRUCTIONS, instructions);
        initialState.put(DeepAgentState.MESSAGES, List.of(UserMessage.from(userInput)));
        // 用户身份注入（供 MemoryMiddleware 长期记忆隔离使用）
        initialState.put(DeepAgentState.USER_ID, getCurrentUserId());
        initialState.put(DeepAgentState.TENANT_ID, TenantContextHolder.getTenantId());
        if (enabledTools != null && !enabledTools.isEmpty()) {
            initialState.put(DeepAgentState.ENABLED_TOOLS, enabledTools);
        }
        return initialState;
    }

    /**
     * 获取图实例（Caffeine 本地缓存，自动过期）
     */
    private CompiledGraph<DeepAgentState> getGraphOrThrow(String sessionId) {
        CompiledGraph<DeepAgentState> graph = graphCache.getIfPresent(sessionId);
        if (graph == null) {
            throw new BusinessException("Agent 图实例不存在，请先创建会话: " + sessionId);
        }
        return graph;
    }

    // ========== HITL 辅助方法 ==========

    /**
     * 解析 HITL 是否启用
     * <p>
     * 优先级：会话级 form.hitlEnabled → 全局 agent.hitl.enabled
     * </p>
     */
    private boolean resolveHitlEnabled(AgentSessionForm form) {
        if (form.getHitlEnabled() != null) {
            return form.getHitlEnabled();
        }
        return Boolean.TRUE.equals(agentProperties.getHitl().getEnabled());
    }

    /**
     * 解析 HITL 中断节点列表
     * <p>
     * 优先级：会话级 form.hitlInterruptNodes → 全局 agent.hitl.interruptNodes → 默认 ["tool"]
     * </p>
     */
    private List<String> resolveHitlInterruptNodes(AgentSessionForm form) {
        if (form.getHitlInterruptNodes() != null && !form.getHitlInterruptNodes().isEmpty()) {
            return form.getHitlInterruptNodes();
        }
        List<String> globalNodes = agentProperties.getHitl().getInterruptNodes();
        if (globalNodes != null && !globalNodes.isEmpty()) {
            return globalNodes;
        }
        return List.of("tool");
    }

    /**
     * 处理 HITL 中断
     * <p>
     * 图被中断后：更新会话状态为 INTERRUPTED → 构建中断上下文 → SSE 推送 INTERRUPT 事件。
     * Emitter 保持存活，等待前端调用 /approve 恢复。
     * </p>
     */
    private void handleInterrupt(String sessionId, SseEmitter emitter, String lastNode) {
        AgentSessionVO session = getAndValidateSession(sessionId);

        // 构建中断上下文
        InterruptContext context = InterruptContext.builder()
                .nodeName(lastNode)
                .sessionId(sessionId)
                .summary("图执行在节点 [" + lastNode + "] 前暂停，等待人工审批")
                .build();

        // 更新会话状态
        session.setStatus(SessionStatus.INTERRUPTED);
        session.setInterruptedNode(lastNode);
        session.setInterruptContext(context);
        session.setUpdatedAt(LocalDateTime.now());
        saveSession(session);

        // SSE 推送中断事件
        try {
            AgentEvent interruptEvent = AgentEvent.builder()
                    .type(AgentEventType.INTERRUPT)
                    .message("执行已暂停，等待人工审批")
                    .data(context)
                    .timestamp(System.currentTimeMillis())
                    .build();
            emitter.send(SseEmitter.event().name("agent_event").data(interruptEvent));

            // 推送等待审批事件
            AgentEvent awaitEvent = AgentEvent.builder()
                    .type(AgentEventType.AWAITING_APPROVAL)
                    .message("等待审批")
                    .data(context)
                    .timestamp(System.currentTimeMillis())
                    .build();
            emitter.send(SseEmitter.event().name("agent_event").data(awaitEvent));

            // 更新状态为 AWAITING_INPUT
            session.setStatus(SessionStatus.AWAITING_INPUT);
            session.setUpdatedAt(LocalDateTime.now());
            saveSession(session);

            log.info("HITL 中断已处理: sessionId={}, node={}", sessionId, lastNode);
            // 注意：不关闭 emitter，保持活跃等待审批
        } catch (Exception e) {
            log.error("HITL 中断事件推送失败: sessionId={}", sessionId, e);
            activeEmitters.remove(sessionId);
            emitter.completeWithError(e);
        }
    }

    /**
     * 恢复被中断的图执行
     * <p>
     * 路径 1（有 checkpoint）：通过 updateState + stream(null, config) 从断点恢复。
     * 路径 2（无 checkpoint）：全量重执行（降级方案）。
     * </p>
     */
    private void resumeExecution(String sessionId, AgentSessionVO session, ApprovalRequest request) {
        CompiledGraph<DeepAgentState> graph = getGraphOrThrow(sessionId);
        SseEmitter emitter = activeEmitters.get(sessionId);

        if (emitter == null) {
            throw new BusinessException("SSE 连接已断开，无法恢复执行。请重新建立 SSE 连接");
        }

        Long sessionUserId = session.getUserId();

        taskExecutor.execute(() -> {
            // 恢复执行也需要设置 EventBus（新线程，ThreadLocal 不跨线程传播）
            SubAgentEventBus.set(event -> {
                try {
                    emitter.send(SseEmitter.event().name("agent_event").data(event));
                } catch (Exception ex) {
                    log.warn("SSE 子 Agent 事件推送失败（恢复）: {}", ex.getMessage());
                }
            });

            try {
                // 构建审批状态更新
                Map<String, Object> approvalState = new HashMap<>();
                approvalState.put(DeepAgentState.APPROVAL_STATUS, request.getDecision().getValue());
                if (request.getFeedback() != null) {
                    approvalState.put(DeepAgentState.HUMAN_FEEDBACK, request.getFeedback());
                }
                approvalState.put(DeepAgentState.INTERRUPTED_NODE, "");

                RunnableConfig config = buildConfig(sessionId, sessionUserId);

                // 推送审批已接收事件
                Map<String, Object> approvalEventData = new HashMap<>();
                approvalEventData.put("decision", request.getDecision().getValue());
                if (request.getFeedback() != null) {
                    approvalEventData.put("feedback", request.getFeedback());
                }
                AgentEvent receivedEvent = AgentEvent.builder()
                        .type(AgentEventType.APPROVAL_RECEIVED)
                        .message("审批决策: " + request.getDecision().getValue())
                        .data(approvalEventData)
                        .timestamp(System.currentTimeMillis())
                        .build();
                emitter.send(SseEmitter.event().name("agent_event").data(receivedEvent));

                // 更新会话状态为运行中
                AgentSessionVO latestSession = getAndValidateSession(sessionId);
                latestSession.setStatus(SessionStatus.RUNNING);
                latestSession.setUpdatedAt(LocalDateTime.now());
                latestSession.setPendingApproval(null);
                saveSession(latestSession);

                // 路径 1：Checkpoint 恢复
                boolean checkpointEnabled = Boolean.TRUE.equals(
                        agentProperties.getDeep().getCheckpointEnabled());

                if (checkpointEnabled) {
                    // 注入审批结果到状态
                    graph.updateState(config, approvalState);
                    // 从 checkpoint 恢复执行
                    var generator = graph.stream(GraphInput.resume(), config);
                    streamGraphOutput(sessionId, emitter, generator, sessionUserId, true);
                } else {
                    // HITL 恢复必须依赖 checkpoint，否则无法从中断点恢复
                    throw new BusinessException(
                            "HITL 审批恢复需要启用 checkpoint (agent.deep.checkpoint-enabled=true)");
                }
            } catch (Exception e) {
                log.error("HITL 恢复执行失败: sessionId={}", sessionId, e);
                try {
                    AgentEvent errorEvent = AgentEvent.builder()
                            .type(AgentEventType.ERROR)
                            .message("恢复执行失败: " + e.getMessage())
                            .timestamp(System.currentTimeMillis())
                            .build();
                    emitter.send(SseEmitter.event().name("agent_event").data(errorEvent));
                } catch (Exception ignored) {
                }
                try {
                    AgentSessionVO latestSession = getAndValidateSession(sessionId);
                    latestSession.setStatus(SessionStatus.FAILED);
                    latestSession.setUpdatedAt(LocalDateTime.now());
                    saveSession(latestSession);
                    graphCache.invalidate(sessionId);
                    sandboxManager.destroy(sessionId);
                } catch (Exception ignored) {
                }
                activeEmitters.remove(sessionId);
                emitter.completeWithError(e);
            } finally {
                SubAgentEventBus.remove();
            }
        });
    }

    /**
     * 流式推送图输出到 SSE Emitter（抽取公共逻辑）
     *
     * @param hitlEnabled 是否启用 HITL（用于中断检测守卫）
     */
    private void streamGraphOutput(String sessionId, SseEmitter emitter,
                                    Iterable<org.bsc.langgraph4j.NodeOutput<DeepAgentState>> generator,
                                    Long sessionUserId,
                                    boolean hitlEnabled) throws Exception {
        String lastNode = null;
        boolean reachedEnd = false;

        for (var nodeOutput : generator) {
            lastNode = nodeOutput.node();
            AgentEvent event = AgentEvent.builder()
                    .type(AgentEventType.NODE_EXECUTION)
                    .message("节点执行: " + nodeOutput.node())
                    .data(nodeOutput.state())
                    .timestamp(System.currentTimeMillis())
                    .build();
            emitter.send(SseEmitter.event().name("agent_event").data(event));

            if (END.equals(nodeOutput.node())) {
                reachedEnd = true;
            }
        }

        if (!reachedEnd && lastNode != null && hitlEnabled) {
            // 又触发了中断
            handleInterrupt(sessionId, emitter, lastNode);
            return;
        }

        // 正常完成
        AgentEvent completeEvent = AgentEvent.builder()
                .type(AgentEventType.FINAL_RESPONSE)
                .message("执行完成")
                .timestamp(System.currentTimeMillis())
                .build();
        emitter.send(SseEmitter.event().name("agent_event").data(completeEvent));
        emitter.complete();

        AgentSessionVO latestSession = getAndValidateSession(sessionId);
        latestSession.setStatus(SessionStatus.COMPLETED);
        latestSession.setUpdatedAt(LocalDateTime.now());
        saveSession(latestSession);
        graphCache.invalidate(sessionId);
        sandboxManager.destroy(sessionId);
        activeEmitters.remove(sessionId);
        log.info("Agent SSE 恢复执行完成: sessionId={}, userId={}", sessionId, sessionUserId);
    }
}
