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
import java.util.*;
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
    /** 用于 history 滚动摘要的 ChatModel（与 PlanNode/ExecuteNode 同源，由 Spring 注入） */
    private final dev.langchain4j.model.chat.ChatModel chatModel;

    /** 编译图缓存：sessionId → CompiledGraph（Caffeine 自动过期，避免内存泄漏） */
    private final Cache<String, CompiledGraph<DeepAgentState>> graphCache;

    /** 活跃 SSE Emitter：sessionId → SseEmitter（HITL 中断时保持存活） */
    private final ConcurrentHashMap<String, SseEmitter> activeEmitters = new ConcurrentHashMap<>();

    public AgentServiceImpl(DeepAgentGraph deepAgentGraph,
                            AgentProperties agentProperties,
                            RedisTemplate<String, Object> redisTemplate,
                            @Qualifier("agentTaskExecutor") org.springframework.core.task.TaskExecutor taskExecutor,
                            SandboxManager sandboxManager,
                            @org.springframework.lang.Nullable SkillsMiddleware skillsMiddleware,
                            @org.springframework.lang.Nullable dev.langchain4j.model.chat.ChatModel chatModel) {
        this.deepAgentGraph = deepAgentGraph;
        this.agentProperties = agentProperties;
        this.redisTemplate = redisTemplate;
        this.taskExecutor = taskExecutor;
        this.sandboxManager = sandboxManager;
        this.skillsMiddleware = skillsMiddleware;
        this.chatModel = chatModel;
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
            Map<String, Object> initialState = buildInitialState(sessionId, userInput, session.getEnabledTools(), session.getHistorySummary(), session.getHistory(), getCurrentUserId(), TenantContextHolder.getTenantId());

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

            // 追加当前轮次到会话内对话历史（多轮上下文）
            appendConversationHistory(session, userInput, finalResponse.orElse(null));

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

        // 在 HTTP 请求线程预先捕获用户身份与租户上下文，
        // 避免 taskExecutor 子线程无法读取 SecurityContext（ThreadLocal）和 TenantContext
        final Long currentUserId = getCurrentUserId();
        final Long currentTenantId = TenantContextHolder.getTenantId();

        // SSE 超时与 session TTL 对齐，避免审批期间 emitter 提前过期
        long emitterTimeout = agentProperties.getDeep().getSessionTtl() * 1000L;
        SseEmitter emitter = new SseEmitter(emitterTimeout);
        activeEmitters.put(sessionId, emitter);

        emitter.onCompletion(() -> activeEmitters.remove(sessionId));
        emitter.onTimeout(() -> {
            activeEmitters.remove(sessionId);
            // 超时时更新 session 状态，避免状态不一致
            // SSE 超时回调可能在容器调度线程，用入口已捕获的 sessionUserId 做归属校验，
            // 避免依赖 SecurityContextHolder（默认 MODE_THREADLOCAL 不跨线程）
            try {
                AgentSessionVO s = loadSessionInternal(sessionId, sessionUserId);
                if (s.getStatus() == SessionStatus.INTERRUPTED
                        || s.getStatus() == SessionStatus.AWAITING_INPUT) {
                    s.setStatus(SessionStatus.FAILED);
                    s.setUpdatedAt(LocalDateTime.now());
                    saveSession(s);
                    graphCache.invalidate(sessionId);
                    sandboxManager.destroy(sessionId);
                    log.warn("SSE Emitter 超时，HITL 会话已失效: sessionId={}", sessionId);
                } else {
                    // 非中断态的超时（如正常执行超时）也需清理沙箱资源，避免泄漏
                    sandboxManager.destroy(sessionId);
                    log.warn("SSE Emitter 超时，清理沙箱资源: sessionId={}, status={}",
                            sessionId, s.getStatus());
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

            // 提到 try 外的图执行状态变量，便于 finally 中读取 finalResponse 追加 history
            boolean hitlEnabled = Boolean.TRUE.equals(session.getHitlEnabled());
            String lastNode = null;
            boolean reachedEnd = false;
            DeepAgentState lastEndState = null;
            boolean historyAppended = false; // 防止正常路径与 finally 重复追加

            try {
                Map<String, Object> initialState = buildInitialState(sessionId, userInput, session.getEnabledTools(), session.getHistorySummary(), session.getHistory(), currentUserId, currentTenantId);

                // HITL 启用时注入状态标记
                if (Boolean.TRUE.equals(session.getHitlEnabled())) {
                    initialState.put(DeepAgentState.HITL_ENABLED, true);
                }

                RunnableConfig config = buildConfig(sessionId, sessionUserId);

                // 流式执行
                var generator = graph.stream(initialState, config);

                for (var nodeOutput : generator) {
                    lastNode = nodeOutput.node();
                    lastEndState = nodeOutput.state();
                    // 推送每个节点执行事件
                    // 容错：emitter 可能因客户端断开/已完成而 send 失败，不应中断图执行主流程
                    // （后续 finally 仍会正确追加 history、保存 session）
                    try {
                        AgentEvent event = AgentEvent.builder()
                                .type(AgentEventType.NODE_EXECUTION)
                                .message("节点执行: " + nodeOutput.node())
                                .data(nodeOutput.state())
                                .timestamp(System.currentTimeMillis())
                                .build();
                        emitter.send(SseEmitter.event().name("agent_event").data(event));
                    } catch (Exception sendEx) {
                        log.debug("SSE 节点事件推送跳过（emitter 已完成或客户端断开）: node={}, err={}",
                                lastNode, sendEx.getMessage());
                    }

                    // 检查是否到达 END
                    if (END.equals(nodeOutput.node())) {
                        reachedEnd = true;
                    }
                }

                // 智能 HITL 中断检测：PlanNode/ExecuteNode 通过 HitlGate 置 NEED_CONFIRMATION
                // 此时路由器已跳到 END，generator 自然结束；通过 state 字段区分智能中断与正常完成
                if (reachedEnd && lastEndState != null && lastEndState.needConfirmation()) {
                    handleSmartInterrupt(sessionId, emitter, lastEndState);
                    return;
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
                try {
                    emitter.send(SseEmitter.event().name("agent_event").data(completeEvent));
                } catch (Exception sendEx) {
                    log.debug("SSE 完成事件推送跳过（emitter 已完成或客户端断开）: err={}", sendEx.getMessage());
                }
                emitter.complete();

                // 更新会话状态到 Redis（history 追加统一在 finally 块处理，避免路径分裂导致漏追加）
                AgentSessionVO latestSession = loadSessionInternal(sessionId, currentUserId);
                latestSession.setStatus(SessionStatus.COMPLETED);
                latestSession.setUpdatedAt(LocalDateTime.now());
                saveSession(latestSession);
                // 不再主动 invalidate graphCache：session 在 Redis 仍活 30 分钟，下次对话需复用同一图实例。
                // Caffeine 已配置 expireAfterAccess=sessionTtl + maximumSize=100，会自然过期。
                sandboxManager.destroy(sessionId);
                activeEmitters.remove(sessionId);
                log.info("Agent SSE 流式执行完成: sessionId={}, userId={}", sessionId, sessionUserId);
            } catch (Exception e) {
                // 客户端主动断开（AbortController.abort / 切换会话 / 关闭页面）：降级为 info 日志，避免误报 ERROR
                if (isClientAbort(e)) {
                    log.info("Agent SSE 客户端断开连接，停止推送: sessionId={}, userId={}", sessionId, sessionUserId);
                } else {
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
                }
                // 更新失败状态到 Redis
                try {
                    AgentSessionVO latestSession = loadSessionInternal(sessionId, currentUserId);
                    latestSession.setStatus(SessionStatus.FAILED);
                    latestSession.setUpdatedAt(LocalDateTime.now());
                    saveSession(latestSession);
                    // 不主动 invalidate graphCache：FAILED 后用户可能重试，保留图实例避免重建开销
                    sandboxManager.destroy(sessionId);
                } catch (Exception ignored) {
                }
                activeEmitters.remove(sessionId);
                // 客户端已断开时不再 completeWithError，避免二次刷错；正常 complete 让框架回收
                if (!isClientAbort(e)) {
                    emitter.completeWithError(e);
                } else {
                    emitter.complete();
                }
            } finally {
                SubAgentEventBus.remove();
                // 统一在 finally 追加 history：覆盖正常完成 / 异常 / 客户端断开 三种路径
                // 保证多轮上下文连续性，避免 historySize=0 的失忆现象
                if (!historyAppended) {
                    try {
                        // 子线程无法读 SecurityContext，用入口捕获的 currentUserId 做归属校验
                        AgentSessionVO finalSession = loadSessionInternal(sessionId, currentUserId);
                        // finalResponse 取自图终态（DIRECT_REPLY/Finalize 都会写 FINAL_RESPONSE 通道）
                        String finalResp = lastEndState != null ? lastEndState.finalResponse().orElse(null) : null;
                        appendConversationHistory(finalSession, userInput, finalResp);
                        saveSession(finalSession);
                        historyAppended = true;
                    } catch (Exception ex) {
                        log.warn("streamExecute finally: 追加 history 失败: sessionId={}, err={}",
                                sessionId, ex.getMessage());
                    }
                }
            }
        });

        return emitter;
    }

    /**
     * 判断异常是否由客户端主动断开 SSE 连接导致（避免误报 ERROR）。
     */
    private boolean isClientAbort(Throwable e) {
        Throwable cur = e;
        int depth = 0;
        while (cur != null && depth++ < 8) {
            String name = cur.getClass().getName();
            if (cur instanceof org.springframework.web.context.request.async.AsyncRequestNotUsableException
                    || name.contains("ClientAbortException")
                    || name.contains("ClosedChannelException")
                    || name.contains("EofException")) {
                return true;
            }
            // IOException "你的主机中的软件中止了一个已建立的连接" / "Broken pipe" / "An established connection was aborted"
            if (cur instanceof java.io.IOException && cur.getMessage() != null) {
                String msg = cur.getMessage().toLowerCase();
                if (msg.contains("broken pipe") || msg.contains("abort") || msg.contains("connection reset")
                        || msg.contains("connection was aborted") || msg.contains("软件中止")) {
                    return true;
                }
            }
            cur = cur.getCause();
        }
        return false;
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
        try {
            redisTemplate.opsForValue().set(key, session, ttl, TimeUnit.SECONDS);
            log.debug("saveSession OK: key={}, ttl={}s, sessionId={}", key, ttl, session.getSessionId());
        } catch (Exception e) {
            log.error("saveSession FAILED: key={}, err={}", key, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 从 Redis 获取会话并校验用户归属（安全鉴权）
     * <p>
     * 防止用户通过篡改 sessionId 窥探到别人的聊天隐私。
     * </p>
     */
    private AgentSessionVO getAndValidateSession(String sessionId) {
        // 鉴权使用当前请求线程的 SecurityContext
        return loadSessionInternal(sessionId, getCurrentUserId(), true);
    }

    /**
     * 内部 session 读取（不依赖 SecurityContextHolder，用于异步线程内的状态更新）。
     * <p>
     * 适用场景：{@code streamExecute} 异步任务的 finally/catch 块——此时线程已切换到
     * {@code agent-exec-X}，{@code SecurityContextHolder}（默认 MODE_THREADLOCAL）的上下文无法传递。
     * 鉴权由 streamExecute 入口（HTTP 主线程）已完成，{@code expectedUserId} 即入口捕获的 currentUserId。
     * </p>
     */
    private AgentSessionVO loadSessionInternal(String sessionId, Long expectedUserId) {
        return loadSessionInternal(sessionId, expectedUserId, false);
    }

    /**
     * session 读取核心实现：反序列化 + 归属校验。
     *
     * @param sessionId       会话 ID
     * @param expectedUserId  期望归属的用户 ID；为 null 时跳过归属校验
     * @param verboseLog      是否打印 valueClass / history 诊断日志（仅 HTTP 入口需要）
     */
    private AgentSessionVO loadSessionInternal(String sessionId, Long expectedUserId, boolean verboseLog) {
        String key = sessionKey(sessionId);
        Object value = redisTemplate.opsForValue().get(key);
        if (verboseLog) {
            log.debug("loadSessionInternal: key={}, valueClass={}", key, value == null ? "null" : value.getClass().getName());
        }
        if (value == null) {
            throw new BusinessException("Agent 会话不存在: " + sessionId);
        }
        // 由于 RedisConfig.disableDefaultTyping()，反序列化结果可能是 LinkedHashMap
        AgentSessionVO session;
        if (value instanceof AgentSessionVO) {
            session = (AgentSessionVO) value;
        } else if (value instanceof java.util.Map) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
                session = mapper.convertValue(value, AgentSessionVO.class);
            } catch (Exception e) {
                throw new BusinessException("Agent 会话反序列化失败: " + sessionId + ", err=" + e.getMessage());
            }
        } else {
            throw new BusinessException("Agent 会话数据类型异常: " + sessionId + ", actual=" + value.getClass().getName());
        }

        // 安全鉴权：校验会话归属（用 expectedUserId 替代 SecurityContextHolder，支持子线程调用）
        if (expectedUserId != null && session.getUserId() != null && !session.getUserId().equals(expectedUserId)) {
            log.warn("越权访问检测: userId={} 尝试访问 sessionId={}（属于 userId={}）",
                    expectedUserId, sessionId, session.getUserId());
            throw new BusinessException("无权访问该会话");
        }

        // 诊断日志：检查 history 字段反序列化情况（仅 HTTP 入口打印，避免子线程刷屏）
        if (verboseLog) {
            if (session.getHistory() != null && !session.getHistory().isEmpty()) {
                Object first = session.getHistory().get(0);
                log.debug("loadSessionInternal: sessionId={}, historySize={}, firstElemClass={}, firstRole={}",
                        sessionId, session.getHistory().size(),
                        first == null ? "null" : first.getClass().getName(),
                        first instanceof AgentSessionVO.ConversationMessage cm ? cm.getRole() : "N/A");
            } else {
                log.debug("loadSessionInternal: sessionId={}, history is empty/null", sessionId);
            }
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
    private Map<String, Object> buildInitialState(String sessionId, String userInput, List<String> enabledTools,
                                                  String historySummary,
                                                  List<AgentSessionVO.ConversationMessage> history,
                                                  Long userId, Long tenantId) {
        Map<String, Object> initialState = new HashMap<>();
        initialState.put(DeepAgentState.SESSION_ID, sessionId);
        // 把会话内历史拼接到指令前缀，让 LLM 能看到多轮上下文（同一 session 内的连续性）
        log.debug("buildInitialState: sessionId={}, historySize={}, hasSummary={}, summaryLen={}",
                sessionId,
                history == null ? 0 : history.size(),
                historySummary != null && !historySummary.isBlank(),
                historySummary == null ? 0 : historySummary.length());
        String instructions = renderInstructionsWithHistory(userInput, historySummary, history);
        log.debug("buildInitialState: rendered instructions length={}, preview={}",
                instructions.length(),
                instructions.length() > 200 ? instructions.substring(0, 200) + "..." : instructions);
        // 技能目录注入（在历史拼接之后）
        if (skillsMiddleware != null && skillsMiddleware.hasSkills()) {
            instructions = skillsMiddleware.enhanceInstructions(instructions);
        }
        initialState.put(DeepAgentState.INSTRUCTIONS, instructions);
        initialState.put(DeepAgentState.MESSAGES, List.of(UserMessage.from(userInput)));
        // 用户身份注入（供 MemoryMiddleware 长期记忆隔离使用）
        // userId/tenantId 由调用方在 HTTP 请求线程预先捕获，避免异步线程读取 ThreadLocal 失败
        initialState.put(DeepAgentState.USER_ID, userId);
        initialState.put(DeepAgentState.TENANT_ID, tenantId);
        if (enabledTools != null && !enabledTools.isEmpty()) {
            initialState.put(DeepAgentState.ENABLED_TOOLS, enabledTools);
        }
        return initialState;
    }

    /**
     * 把会话历史摘要 + 最近对话原文 + 当前用户输入渲染为 LLM instructions。
     * <p>
     * 渲染顺序：历史摘要（若有） → 最近对话原文（若有） → 当前用户指令。
     * 采用 KISS 方案：纯文本拼接，不依赖 ChatMessage 序列化。
     * </p>
     */
    private String renderInstructionsWithHistory(String userInput, String historySummary,
                                                 List<AgentSessionVO.ConversationMessage> history) {
        StringBuilder sb = new StringBuilder();
        boolean hasSummary = historySummary != null && !historySummary.isBlank();
        boolean hasHistory = history != null && !history.isEmpty();
        if (!hasSummary && !hasHistory) {
            return userInput;
        }
        if (hasSummary) {
            sb.append("【历史摘要】\n").append(historySummary).append("\n\n");
        }
        if (hasHistory) {
            sb.append("【最近对话】\n");
            for (AgentSessionVO.ConversationMessage msg : history) {
                String role = "assistant".equalsIgnoreCase(msg.getRole()) ? "Assistant" : "User";
                sb.append(role).append(": ").append(msg.getContent() == null ? "" : msg.getContent()).append("\n");
            }
            sb.append("\n");
        }
        sb.append("【当前用户指令】\n").append(userInput);
        return sb.toString();
    }

    /**
     * 把当前轮次（user 输入 + assistant 最终回复）追加到会话历史，并执行滑窗 + 滚动摘要。
     * <p>
     * 会话内多轮上下文：在同一 sessionId 范围内累积，跨 session 不共享
     * （跨 session 的长期记忆由 MemoryMiddleware + pgvector 负责）。
     * </p>
     * <p>
     * token 控制策略（避免长会话 prompt 线性膨胀）：
     * <ol>
     *   <li>追加本轮后，若 history 超过 {@code maxRecentMessages}，把最早的溢出部分转移到 pendingSummarySource</li>
     *   <li>当 pendingSummarySource 累计 ≥ {@code summaryTriggerThreshold} 且启用摘要时，
     *       调用 LLM 把 pendingSummarySource + 现有 historySummary 合并为新摘要，清空 pendingSummarySource</li>
     *   <li>禁用摘要时直接丢弃溢出部分（零 LLM 成本兜底）</li>
     * </ol>
     * </p>
     */
    private void appendConversationHistory(AgentSessionVO session, String userInput, String finalResponse) {
        if (session == null || userInput == null || userInput.isBlank()) {
            return;
        }
        int beforeSize = session.getHistory() == null ? 0 : session.getHistory().size();
        List<AgentSessionVO.ConversationMessage> history = session.getHistory() != null
                ? new ArrayList<>(session.getHistory()) : new ArrayList<>();
        history.add(AgentSessionVO.ConversationMessage.builder().role("user").content(userInput).build());
        if (finalResponse != null && !finalResponse.isBlank()) {
            history.add(AgentSessionVO.ConversationMessage.builder().role("assistant").content(finalResponse).build());
        }

        AgentProperties.HistoryConfig cfg = agentProperties.getHistory();
        int maxRecentMessages = cfg.getMaxRecentMessages() != null && cfg.getMaxRecentMessages() > 0
                ? cfg.getMaxRecentMessages() : 10;

        // 滑窗溢出：把最早的超额消息转移到 pendingSummarySource
        List<AgentSessionVO.ConversationMessage> pending = session.getPendingSummarySource() != null
                ? new ArrayList<>(session.getPendingSummarySource()) : new ArrayList<>();
        if (history.size() > maxRecentMessages) {
            int overflow = history.size() - maxRecentMessages;
            List<AgentSessionVO.ConversationMessage> overflowItems = new ArrayList<>(history.subList(0, overflow));
            pending.addAll(overflowItems);
            history = new ArrayList<>(history.subList(overflow, history.size()));
        }

        // 触发滚动摘要
        int triggerThreshold = cfg.getSummaryTriggerThreshold() != null && cfg.getSummaryTriggerThreshold() > 0
                ? cfg.getSummaryTriggerThreshold() : 4;
        boolean summaryEnabled = !Boolean.FALSE.equals(cfg.getEnableSummary());
        if (summaryEnabled && chatModel != null && pending.size() >= triggerThreshold) {
            String merged = summarizeHistory(session.getHistorySummary(), pending, cfg);
            if (merged != null) {
                session.setHistorySummary(merged);
                pending.clear();
                log.info("appendConversationHistory: 触发摘要, sessionId={}, 摘要后长度={}",
                        session.getSessionId(), merged.length());
            }
        }

        session.setHistory(history);
        session.setPendingSummarySource(pending);
        log.debug("appendConversationHistory: sessionId={}, beforeSize={}, afterSize={}, pendingSize={}, hasSummary={}, finalRespLen={}",
                session.getSessionId(), beforeSize, history.size(), pending.size(),
                session.getHistorySummary() != null && !session.getHistorySummary().isBlank(),
                finalResponse == null ? 0 : finalResponse.length());
    }

    /**
     * 调用 LLM 把旧摘要 + 待摘要消息列表合并为新的会话历史摘要。
     * <p>
     * 复用 MessageCompressionMiddleware 的 COMPRESSION_PROMPT 设计思想：
     * 保留已完成的任务/关键决策/遇到错误/当前进展，避免丢失影响多轮连贯性的信息。
     * </p>
     */
    private String summarizeHistory(String existingSummary,
                                    List<AgentSessionVO.ConversationMessage> pendingMessages,
                                    AgentProperties.HistoryConfig cfg) {
        if (pendingMessages == null || pendingMessages.isEmpty()) {
            return existingSummary;
        }
        try {
            int maxChars = cfg.getSummaryMaxChars() != null && cfg.getSummaryMaxChars() > 0
                    ? cfg.getSummaryMaxChars() : 500;
            String prompt = """
                    你是对话历史压缩器。将以下对话历史压缩为简洁的摘要，保留：
                    1. 用户的偏好、身份信息、关键约束
                    2. 已完成的任务及其结果
                    3. 关键决策和发现
                    4. 当前正在进行的任务
                    5. 遇到的错误和解决方式

                    摘要应简洁但信息完整，不超过 %d 字。
                    """.formatted(maxChars);

            StringBuilder input = new StringBuilder();
            if (existingSummary != null && !existingSummary.isBlank()) {
                input.append("【已有摘要】\n").append(existingSummary).append("\n\n");
            }
            input.append("【新增对话片段】\n");
            for (AgentSessionVO.ConversationMessage msg : pendingMessages) {
                String role = "assistant".equalsIgnoreCase(msg.getRole()) ? "Assistant" : "User";
                input.append(role).append(": ").append(msg.getContent() == null ? "" : msg.getContent()).append("\n");
            }

            dev.langchain4j.model.chat.response.ChatResponse response = chatModel.chat(
                    dev.langchain4j.data.message.SystemMessage.from(prompt),
                    dev.langchain4j.data.message.UserMessage.from("请合并生成最新的对话摘要：\n\n" + input)
            );
            return response.aiMessage().text();
        } catch (Exception e) {
            log.warn("summarizeHistory: LLM 摘要失败，回退为仅滑窗模式: {}", e.getMessage());
            return null;
        }
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

        InterruptContext context = InterruptContext.builder()
                .nodeName(lastNode)
                .sessionId(sessionId)
                .summary("图执行在节点 [" + lastNode + "] 前暂停，等待人工审批")
                .build();

        markSessionInterrupted(session, lastNode, context);

        try {
            AgentEvent interruptEvent = AgentEvent.builder()
                    .type(AgentEventType.INTERRUPT)
                    .message("执行已暂停，等待人工审批")
                    .data(context)
                    .timestamp(System.currentTimeMillis())
                    .build();
            emitter.send(SseEmitter.event().name("agent_event").data(interruptEvent));

            AgentEvent awaitEvent = AgentEvent.builder()
                    .type(AgentEventType.AWAITING_APPROVAL)
                    .message("等待审批")
                    .data(context)
                    .timestamp(System.currentTimeMillis())
                    .build();
            emitter.send(SseEmitter.event().name("agent_event").data(awaitEvent));

            markSessionAwaitingInput(session);

            log.info("HITL 中断已处理: sessionId={}, node={}", sessionId, lastNode);
            // 注意：不关闭 emitter，保持活跃等待审批
        } catch (Exception e) {
            log.error("HITL 中断事件推送失败: sessionId={}", sessionId, e);
            activeEmitters.remove(sessionId);
            emitter.completeWithError(e);
        }
    }

    /**
     * 处理智能 HITL Gate 中断
     * <p>
     * 由 PlanNode/ExecuteNode 通过 HitlGateChecker 判定需确认时触发。
     * 与 {@link #handleInterrupt} 的差异：
     * <ul>
     *   <li>中断原因来自 HitlDecision（reason/severity/dimension），而非节点名</li>
     *   <li>INTERRUPT 事件已由节点 emit，这里只补发 AWAITING_APPROVAL 并更新 session 状态</li>
     * </ul>
     * </p>
     */
    private void handleSmartInterrupt(String sessionId, SseEmitter emitter, DeepAgentState endState) {
        String reason = endState.interruptReason();
        String severity = endState.interruptSeverity();

        InterruptContext context = InterruptContext.builder()
                .nodeName("smart-gate")
                .sessionId(sessionId)
                .summary("智能门控触发，等待用户确认: " + reason)
                .build();

        AgentSessionVO session = getAndValidateSession(sessionId);
        markSessionInterrupted(session, "smart-gate", context);

        try {
            // INTERRUPT 事件已由 PlanNode/ExecuteNode emit；这里补发 AWAITING_APPROVAL
            Map<String, Object> gateData = new HashMap<>();
            gateData.put("reason", reason);
            gateData.put("severity", severity);
            gateData.put("context", context);
            AgentEvent awaitEvent = AgentEvent.builder()
                    .type(AgentEventType.AWAITING_APPROVAL)
                    .message("等待用户确认: " + reason)
                    .data(gateData)
                    .timestamp(System.currentTimeMillis())
                    .build();
            emitter.send(SseEmitter.event().name("agent_event").data(awaitEvent));

            markSessionAwaitingInput(session);

            log.info("智能 HITL 中断已处理: sessionId={}, severity={}, reason={}", sessionId, severity, reason);
            // 不关闭 emitter，等待前端调用 /approve
        } catch (Exception e) {
            log.error("智能 HITL 中断事件推送失败: sessionId={}", sessionId, e);
            activeEmitters.remove(sessionId);
            emitter.completeWithError(e);
        }
    }

    /**
     * 把 session 标记为 INTERRUPTED 状态（含 interruptedNode、context、updatedAt），并持久化。
     * <p>
     * handleInterrupt / handleSmartInterrupt 共用，消除 DRY 违反。
     * </p>
     */
    private void markSessionInterrupted(AgentSessionVO session, String nodeName, InterruptContext context) {
        session.setStatus(SessionStatus.INTERRUPTED);
        session.setInterruptedNode(nodeName);
        session.setInterruptContext(context);
        session.setUpdatedAt(LocalDateTime.now());
        saveSession(session);
    }

    /**
     * 把 session 切换为 AWAITING_INPUT 状态（emitter 推送成功后调用），并持久化。
     */
    private void markSessionAwaitingInput(AgentSessionVO session) {
        session.setStatus(SessionStatus.AWAITING_INPUT);
        session.setUpdatedAt(LocalDateTime.now());
        saveSession(session);
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
                // 清除智能 HITL Gate 标志，避免恢复后再次触发中断
                approvalState.put(DeepAgentState.NEED_CONFIRMATION, false);
                approvalState.put(DeepAgentState.INTERRUPT_REASON, "");
                approvalState.put(DeepAgentState.INTERRUPT_SEVERITY, "");

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

    @Override
    public List<AgentSessionVO> listSessions() {
        Long currentUserId = getCurrentUserId();
        String pattern = StrUtil.format(RedisConstants.Agent.SESSION, "*");

        // 注意：Redis keys 命令在生产环境不推荐，这里仅作为示例
        // 实际生产应使用 Redis Hash 结构存储 userId -> sessionIds 映射
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }

        List<AgentSessionVO> sessions = new java.util.ArrayList<>();
        for (String key : keys) {
            Object value = redisTemplate.opsForValue().get(key);
            if (value instanceof AgentSessionVO session) {
                if (currentUserId.equals(session.getUserId())) {
                    sessions.add(session);
                }
            }
        }

        return sessions.stream()
                .sorted((s1, s2) -> s2.getUpdatedAt().compareTo(s1.getUpdatedAt()))
                .toList();
    }

    @Override
    public void deleteSession(String sessionId) {
        AgentSessionVO session = getAndValidateSession(sessionId);

        // 清理资源
        graphCache.invalidate(sessionId);
        sandboxManager.destroy(sessionId);

        // 从 Redis 删除
        String key = sessionKey(sessionId);
        redisTemplate.delete(key);

        // 关闭 SSE 连接（如果存在）
        SseEmitter emitter = activeEmitters.remove(sessionId);
        if (emitter != null) {
            try {
                emitter.complete();
            } catch (Exception e) {
                log.warn("关闭 SSE 连接失败: sessionId={}", sessionId, e);
            }
        }

        log.info("Agent 会话已删除: sessionId={}, userId={}", sessionId, session.getUserId());
    }
}
