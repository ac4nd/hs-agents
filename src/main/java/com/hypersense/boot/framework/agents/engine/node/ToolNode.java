package com.hypersense.boot.framework.agents.engine.node;

import com.hypersense.boot.framework.agents.config.AgentProperties;
import com.hypersense.boot.framework.agents.config.ToolRetryConfig;
import com.hypersense.boot.framework.agents.engine.SubAgentEventBus;
import com.hypersense.boot.framework.agents.enums.AgentEventType;
import com.hypersense.boot.framework.agents.enums.TodoStatus;
import com.hypersense.boot.framework.agents.model.AgentEvent;
import com.hypersense.boot.framework.agents.model.DeepAgentState;
import com.hypersense.boot.framework.agents.model.TodoItem;
import com.hypersense.boot.framework.agents.tool.ToolProvider;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 工具调用节点
 * <p>
 * 根据 TODO 描述，先按 {@link #shouldInvoke} 关键词筛选候选工具，
 * 再通过 LangChain4j function-call 协议让 LLM 选定具体工具 + 参数。
 * LLM 决策失败或 chatModel 未注入时，回退到旧的"遍历执行"路径。
 * </p>
 *
 * @author Claude
 * @since 2026/5/15
 */
@Slf4j
@Component
public class ToolNode implements NodeAction<DeepAgentState> {

    /**
     * 系统内置「始终启用」工具白名单。
     * <p>
     * 这些工具不依赖前端 enabledTools 显式声明，确保核心链路（如纯文本回复）在任何 Agent 配置下都可用。
     * 废除 ExecuteNode.direct 策略后，reply_text 必须在所有场景下都能被命中。
     * </p>
     */
    private static final java.util.Set<String> BUILT_IN_ALWAYS_ON_TOOLS =
            java.util.Set.of("reply_text");

    /**
     * LLM 工具决策 / 纯文本回退路径的最大输出 token 数。
     * <p>历史值 8192 在 design profile 生成完整 HTML 时被频繁截断（HTML 仅到 {@code <body>}
     * 标签就被砍掉，整页黑屏）。但 32K 会让 LLM 倾向生成 12-18K tokens 的臃肿 HTML
     * （design profile systemPrompt 已写死「6K tokens / 24KB」铁律），导致生成时间从 8s 膨胀到 25s。
     * 16K 上限足够覆盖：6K HTML + 思考链 + 工具参数 + 余量，且能抑制 LLM 在 CSS 里铺陈 design token。
     * 截断兜底由 {@link #rescueContentFromText} + {@link #tryPlainTextFallbackForFileWrite} 负责。</p>
     */
    private static final int LLM_MAX_OUTPUT_TOKENS = 32768;

    private final List<ToolProvider> toolProviders;
    private final ToolRetryConfig retryConfig;
    /** 可选：LangChain4j function-call 决策模型；为 null 时走旧遍历逻辑 */
    private final ChatModel chatModel;
    /**
     * 可选：流式 ChatModel，用于长输出场景（file_write content 生成完整 HTML 等）。
     * <p>注入后 {@link #decideByLlm} 会优先走流式分支，避免同步 8K+ token 整体超时。</p>
     */
    private final dev.langchain4j.model.chat.StreamingChatModel streamingChatModel;
    /**
     * Capability Profile 注册表：用于在 active_profile 已设置时，对 LLM 请求的工具进行白名单校验。
     * <p>
     * Plan A 框架：仅当 {@link DeepAgentState#ACTIVE_PROFILE} 在 state 中已设置且能成功加载 profile 时，
     * 才启用白名单校验；否则（profile 未设置 / 加载失败）降级为「不限制」，保留原有行为。
     * 测试与不通过 Spring 创建的实例可传入 null。
     * </p>
     */
    private final com.hypersense.boot.framework.agents.profile.CapabilityProfileRegistry profileRegistry;

    /**
     * 可选：TDD 状态机管理器。code-profile 下，工具执行后调用其推进 phase。
     * <p> nullable：测试 / 非 Spring 路径可不注入。 </p>
     */
    private final com.hypersense.boot.framework.agents.profile.impl.TddPhaseManager tddPhaseManager;

    /**
     * 可选：API 符号白名单。code-profile 下，file_write 后抽取源码 import 注册到本表，
     * 供后续 {@code no_phantom_api} lint 校验。
     * <p> nullable：同上。 </p>
     */
    private final com.hypersense.boot.framework.agents.profile.lint.SymbolRegistry symbolRegistry;

    /**
     * 可选：lint 违规计数器。file_write/file_render 后扫描 profile.lintRules() 命中时累加，
     * 超 {@code HitlPolicy.maxLintRetriesBeforeInterrupt} 时触发 INTERRUPT。
     * <p> nullable：测试 / 非 Spring 路径可不注入。 </p>
     */
    private final com.hypersense.boot.framework.agents.profile.lint.LintStatsManager lintStatsManager;

    @Autowired
    public ToolNode(@Nullable List<ToolProvider> toolProviders,
                    @Nullable AgentProperties agentProperties,
                    @Nullable ChatModel chatModel,
                    @Nullable com.hypersense.boot.framework.agents.profile.CapabilityProfileRegistry profileRegistry) {
        this(toolProviders,
                resolveRetryConfig(agentProperties),
                chatModel, null, profileRegistry, null, null, null);
    }

    /** 旧签名兼容：Spring 自动注入时如未显式注入 ChatModel 仍可工作（走旧遍历逻辑）。 */
    public ToolNode(@Nullable List<ToolProvider> toolProviders,
                    @Nullable AgentProperties agentProperties) {
        this(toolProviders,
                resolveRetryConfig(agentProperties),
                null, null, null, null, null, null);
    }

    /**
     * Builder 路径：创建带自定义重试配置的 ToolNode（不经过 Spring）。
     * 旧二参版本，保留以兼容既有调用方（如 ToolRetryTest）。
     */
    public static ToolNode create(List<ToolProvider> toolProviders, ToolRetryConfig retryConfig) {
        return new ToolNode(toolProviders, retryConfig != null ? retryConfig : ToolRetryConfig.disabled(), null, null, null, null, null, null);
    }

    /**
     * Builder 路径：创建带 ChatModel 的 ToolNode，启用 LangChain4j function-call 决策。
     */
    public static ToolNode create(List<ToolProvider> toolProviders, ToolRetryConfig retryConfig, ChatModel chatModel) {
        return new ToolNode(toolProviders,
                retryConfig != null ? retryConfig : ToolRetryConfig.disabled(),
                chatModel, null, null, null, null, null);
    }

    /**
     * Builder 路径：同时注入同步与流式 ChatModel。
     * <p>推荐路径：流式模型非空时优先流式，避免长输出整体超时。</p>
     */
    public static ToolNode create(List<ToolProvider> toolProviders, ToolRetryConfig retryConfig,
                                  ChatModel chatModel,
                                  dev.langchain4j.model.chat.StreamingChatModel streamingChatModel) {
        return new ToolNode(toolProviders,
                retryConfig != null ? retryConfig : ToolRetryConfig.disabled(),
                chatModel, streamingChatModel, null, null, null, null);
    }

    /**
     * Builder 路径（含 profileRegistry）：用于 Plan A 主链路，使 ToolNode 能进行 profile 工具白名单校验。
     * <p>主链路 NodeFactory / GodlikeAgent 应使用此重载注入 registry。</p>
     */
    public static ToolNode create(List<ToolProvider> toolProviders, ToolRetryConfig retryConfig,
                                  ChatModel chatModel,
                                  dev.langchain4j.model.chat.StreamingChatModel streamingChatModel,
                                  com.hypersense.boot.framework.agents.profile.CapabilityProfileRegistry profileRegistry) {
        return new ToolNode(toolProviders,
                retryConfig != null ? retryConfig : ToolRetryConfig.disabled(),
                chatModel, streamingChatModel, profileRegistry, null, null, null);
    }

    /**
     * Builder 路径（含 Plan C code-profile 资产）：在 profileRegistry 之上追加 TddPhaseManager + SymbolRegistry，
     * 使 ToolNode 能在 file_write / sandbox_exec 后推进 TDD 状态机并注册 import。
     * <p>主链路 NodeFactory 应使用此重载注入全部依赖。</p>
     */
    public static ToolNode create(List<ToolProvider> toolProviders, ToolRetryConfig retryConfig,
                                  ChatModel chatModel,
                                  dev.langchain4j.model.chat.StreamingChatModel streamingChatModel,
                                  com.hypersense.boot.framework.agents.profile.CapabilityProfileRegistry profileRegistry,
                                  com.hypersense.boot.framework.agents.profile.impl.TddPhaseManager tddPhaseManager,
                                  com.hypersense.boot.framework.agents.profile.lint.SymbolRegistry symbolRegistry) {
        return new ToolNode(toolProviders,
                retryConfig != null ? retryConfig : ToolRetryConfig.disabled(),
                chatModel, streamingChatModel, profileRegistry, tddPhaseManager, symbolRegistry, null);
    }

    /**
     * Builder 路径（含 Plan C lint 计数器，P0#2 完整版）：在 7 参基础上追加 LintStatsManager，
     * 使 ToolNode 能在 lint 违规累计达阈值时触发 INTERRUPT。
     * <p>主链路 NodeFactory 应使用此重载注入全部依赖。</p>
     */
    public static ToolNode create(List<ToolProvider> toolProviders, ToolRetryConfig retryConfig,
                                  ChatModel chatModel,
                                  dev.langchain4j.model.chat.StreamingChatModel streamingChatModel,
                                  com.hypersense.boot.framework.agents.profile.CapabilityProfileRegistry profileRegistry,
                                  com.hypersense.boot.framework.agents.profile.impl.TddPhaseManager tddPhaseManager,
                                  com.hypersense.boot.framework.agents.profile.lint.SymbolRegistry symbolRegistry,
                                  com.hypersense.boot.framework.agents.profile.lint.LintStatsManager lintStatsManager) {
        return new ToolNode(toolProviders,
                retryConfig != null ? retryConfig : ToolRetryConfig.disabled(),
                chatModel, streamingChatModel, profileRegistry, tddPhaseManager, symbolRegistry, lintStatsManager);
    }

    private ToolNode(List<ToolProvider> toolProviders, ToolRetryConfig retryConfig, ChatModel chatModel,
                     dev.langchain4j.model.chat.StreamingChatModel streamingChatModel,
                     com.hypersense.boot.framework.agents.profile.CapabilityProfileRegistry profileRegistry,
                     com.hypersense.boot.framework.agents.profile.impl.TddPhaseManager tddPhaseManager,
                     com.hypersense.boot.framework.agents.profile.lint.SymbolRegistry symbolRegistry,
                     com.hypersense.boot.framework.agents.profile.lint.LintStatsManager lintStatsManager) {
        this.toolProviders = toolProviders;
        this.retryConfig = retryConfig;
        this.chatModel = chatModel;
        this.streamingChatModel = streamingChatModel;
        this.profileRegistry = profileRegistry;
        this.tddPhaseManager = tddPhaseManager;
        this.symbolRegistry = symbolRegistry;
        this.lintStatsManager = lintStatsManager;
    }

    private static ToolRetryConfig resolveRetryConfig(AgentProperties props) {
        if (props == null) {
            return ToolRetryConfig.disabled();
        }
        return ToolRetryConfig.fromProperties(props.getTools().getToolRetry());
    }

    /**
     * 返回当前 active profile 的工具白名单。profile 未设置 / registry 未注入 / 加载失败时返回 null（表示不限制）。
     * <p>
     * Plan A 框架：仅在 active_profile 已设置且加载成功时启用白名单校验。
     * 这样在 HITL 切换过渡、profile 未配置、或 registry 不可用（测试场景）时，
     * ToolNode 完全保留原有行为，不引入额外限制。
     * </p>
     *
     * @param state 当前 Agent 状态
     * @return 允许的工具名列表；null 表示不应用白名单
     */
    private java.util.List<String> getActiveAllowedTools(DeepAgentState state) {
        if (state == null || profileRegistry == null) return null;
        String activeProfileId = state.<String>value(com.hypersense.boot.framework.agents.model.DeepAgentState.ACTIVE_PROFILE).orElse(null);
        if (activeProfileId == null || activeProfileId.isBlank()) return null;
        try {
            String sessionId = state.<String>value(DeepAgentState.SESSION_ID).orElse(null);
            java.util.Map<String, Object> hints = state.<java.util.Map<String, Object>>value(DeepAgentState.PROFILE_HINTS).orElse(java.util.Map.of());
            com.hypersense.boot.framework.agents.profile.CapabilityProfile profile = profileRegistry.get(activeProfileId, sessionId, hints);
            if (profile == null) return null;
            return profile.allowedTools();
        } catch (Exception e) {
            // profile 加载失败：不阻塞主链路，降级为不限制（保留原行为）
            log.warn("ToolNode: 加载 active profile [{}] 失败，跳过工具白名单校验: {}", activeProfileId, e.getMessage());
            return null;
        }
    }

    /**
     * 校验 LLM 请求的工具是否在 active profile 的白名单内。
     * <p>非白名单时返回带 failReason 的 outcome；白名单通过或未启用校验时返回 null。</p>
     *
     * @param requestedToolName LLM 通过 function-call 请求的工具名
     * @param state             当前 Agent 状态
     * @return 拒绝结果 outcome，或 null 表示允许继续
     */
    private LlmDecisionOutcome rejectIfNotInProfile(String requestedToolName, DeepAgentState state) {
        java.util.List<String> allowed = getActiveAllowedTools(state);
        if (allowed == null || allowed.isEmpty()) {
            // 未启用白名单（profile 未设置 / 加载失败 / registry 未注入）：放行
            return null;
        }
        if (allowed.contains(requestedToolName)) {
            return null;  // 命中白名单，放行
        }
        // 不在白名单：构造与「LLM 调用了未注册的工具」一致风格的 failReason
        String activeProfileId = state.<String>value(com.hypersense.boot.framework.agents.model.DeepAgentState.ACTIVE_PROFILE).orElse("unknown");
        LlmDecisionOutcome o = new LlmDecisionOutcome();
        o.failReason = "工具 " + requestedToolName + " 不属于当前 profile ["
                + activeProfileId + "] 的 allowedTools 白名单，允许的工具：" + allowed;
        log.warn("ToolNode: LLM 请求的工具 [{}] 被 profile [{}] 白名单拒绝", requestedToolName, activeProfileId);
        return o;
    }

    /**
     * LLM 工具名别名规范化：把 LLM 常用的「直觉名」映射到当前 profile 白名单内的等价工具。
     * <p>背景：design profile 严禁 {@code file_write}（不在白名单），但 LLM 训练语料里
     * {@code file_write} 是最常见文件写入名，强 prompt 也压不住——直接由代码层别名兜底。</p>
     * <p>当前规则（按 activeProfileId + requestedName 匹配）：</p>
     * <ul>
     *   <li>design profile：{@code file_write} / {@code file_save} / {@code save_file} → {@code file_write_chunk}</li>
     * </ul>
     * <p>命中别名时返回规范名，否则原样返回。</p>
     */
    private String normalizeToolAlias(String requestedName, DeepAgentState state) {
        if (requestedName == null) {
            return null;
        }
        String profileId = state.<String>value(com.hypersense.boot.framework.agents.model.DeepAgentState.ACTIVE_PROFILE).orElse("");
        if ("design".equals(profileId)) {
            String lower = requestedName.toLowerCase();
            if (lower.equals("file_write") || lower.equals("file_save") || lower.equals("save_file")) {
                log.warn("ToolNode: design profile 工具别名 [{}] → [file_write_chunk]", requestedName);
                return "file_write_chunk";
            }
        }
        return requestedName;
    }

    @Override
    public Map<String, Object> apply(DeepAgentState state) {
        Optional<TodoItem> currentTodoOpt = state.currentTodo();
        if (currentTodoOpt.isEmpty()) {
            log.warn("ToolNode: 无当前 TODO，跳过工具调用");
            return Map.of();
        }

        TodoItem todo = currentTodoOpt.get();
        log.info("ToolNode: 为 TODO [{}] 查找工具", todo.getDescription());

        // 候选工具初筛：按 enabledTools + shouldInvoke 关键词过滤
        // 注意：系统内置工具（reply_text 等）始终放行，不受 enabledTools 白名单约束，
        // 保证「废除 direct 后所有回复场景都能命中 reply_text」不会被前端 enabledTools 配置误屏蔽。
        List<String> enabledTools = state.enabledTools();
        List<ToolProvider> candidates = new ArrayList<>();
        for (ToolProvider tool : toolProviders) {
            if (!enabledTools.isEmpty() && !enabledTools.contains(tool.name())
                    && !BUILT_IN_ALWAYS_ON_TOOLS.contains(tool.name())) {
                log.debug("ToolNode: 工具 [{}] 未启用，跳过", tool.name());
                continue;
            }
            if (!shouldInvoke(tool, todo.getDescription())) {
                log.info("ToolNode: 工具 [{}] 与当前 TODO 不匹配，跳过", tool.name());
                continue;
            }
            candidates.add(tool);
        }

        Map<String, Object> toolResults = new HashMap<>();
        Map<String, String> files = new HashMap<>(state.files());
        boolean llmDecisionUsed = false;
        // Plan C P0#2：lint 累计违规触发 HITL 中断时填入（最终合并到返回 Map）
        String lintInterruptReason = null;
        String lintInterruptSeverity = null;

        // 短路：reply_text 是纯文本回显通道（非"工具选择"语义），不应走 function-calling 决策。
        // 否则 LLM 会把"完整 SUMMARY 文本"塞进 tool args，function-calling 模式下 onPartialResponse
        // 不回调（注释见 emitCodeStreamingForFileWriteOnce），HTTP 线程在 decideByLlmStreaming
        // 的 future.get(10, MINUTES) 同步阻塞，期间 SSE 缓冲区无法 flush，前端表现为"卡死直到服务关闭"。
        // 命中条件：候选含 reply_text 且 TODO 描述显式声明使用 reply_text（PlanNode 已统一此契约）。
        // 改走纯文本直出（无 toolSpecifications），LLM 几秒内返回短文本，立即调 ReplyTextTool.execute。
        if (chatModel != null && candidates.stream().anyMatch(t -> "reply_text".equals(t.name()))
                && isReplyTextTodo(todo.getDescription())) {
            LlmDecisionOutcome replyOutcome = tryPlainTextForReplyText(candidates, todo, state);
            if (replyOutcome != null && replyOutcome.chosen != null) {
                llmDecisionUsed = true;
                String chosenName = replyOutcome.chosen.name();
                try {
                    Object result = replyOutcome.toolResultOverride != null
                            ? replyOutcome.toolResultOverride
                            : executeWithRetry(replyOutcome.chosen, replyOutcome.args);
                    toolResults.put(chosenName, result);
                    log.info("ToolNode: reply_text 短路直出成功（跳过 function-calling 决策）");
                    emit(AgentEventType.TOOL_CALL, "调用工具: " + todo.getDescription(),
                            buildCallData(todo, candidates, replyOutcome.chosen, replyOutcome.args, true, null));
                } catch (Exception e) {
                    log.error("ToolNode: reply_text 短路执行失败", e);
                    toolResults.put(chosenName, "执行失败: " + e.getMessage());
                    emit(AgentEventType.TOOL_CALL, "调用工具失败: " + todo.getDescription(),
                            buildCallData(todo, candidates, replyOutcome.chosen, replyOutcome.args, false,
                                    "工具执行异常: " + e.getMessage()));
                }
            }
        }

        // 优先：LangChain4j function-call 决策（chatModel 已注入且候选非空）
        if (!llmDecisionUsed && chatModel != null && !candidates.isEmpty()) {
            // 主动短路：design profile + HTML 类 TODO 直接走纯文本 artifact 模式
            // 背景：function calling 模式下 LLM 把完整 HTML 塞 tool args，前端 6 分钟看不到任何输出；
            // 纯文本模式 LLM 流式输出 HTML 到聊天框，用户实时看到代码增长，完成后从 <artifact> 解析落盘。
            // 命中后 tryPlainTextFallbackForFileWrite 直接执行工具，复用其返回的 outcome；
            // 失败（返回 null）自动降级到原 function calling 决策路径。
            LlmDecisionOutcome outcome = null;
            if (isDesignHtmlTodo(state, todo, candidates)) {
                log.info("ToolNode: design profile + HTML TODO 短路纯文本 artifact 模式 todoId={}", todo.getId());
                outcome = tryPlainTextFallbackForFileWrite("file_write_chunk", candidates, todo, state, null);
                if (outcome == null) {
                    log.warn("ToolNode: design HTML 短路失败，降级到 function calling todoId={}", todo.getId());
                }
            }
            if (outcome == null) {
                outcome = decideByLlm(candidates, todo, state);
            }
            if (outcome != null && outcome.chosen != null) {
                llmDecisionUsed = true;
                String chosenName = outcome.chosen.name();
                try {
                    // 纯文本回退路径已直接执行过工具，复用其结果，不再重复 execute
                    Object result;
                    if (outcome.toolResultOverride != null) {
                        result = outcome.toolResultOverride;
                        log.info("ToolNode: 复用纯文本回退路径已执行的工具 [{}] 结果", chosenName);
                    } else {
                        result = executeWithRetry(outcome.chosen, outcome.args);
                    }
                    toolResults.put(chosenName, result);
                    handleFileWriteSideEffect(outcome.chosen, result, state, todo, files);
                    advanceTddPhase(state, chosenName, result);
                    String lintSig = runProfileLint(state, chosenName, result, todo);
                    if (lintSig != null && lintInterruptReason == null) {
                        lintInterruptReason = lintSig;
                        lintInterruptSeverity = "high";
                    }
                    log.info("ToolNode: LLM 决策调用工具 [{}] 成功", chosenName);

                    emit(AgentEventType.TOOL_CALL, "调用工具: " + todo.getDescription(),
                            buildCallData(todo, candidates, outcome.chosen, outcome.args, true, null));
                } catch (Exception e) {
                    log.error("ToolNode: LLM 决策的工具 [{}] 执行失败", chosenName, e);
                    toolResults.put(chosenName, "执行失败: " + e.getMessage());
                    emit(AgentEventType.TOOL_CALL, "调用工具失败: " + todo.getDescription(),
                            buildCallData(todo, candidates, outcome.chosen, outcome.args, false,
                                    "工具执行异常: " + e.getMessage()));
                }
            } else if (outcome != null && outcome.failReason != null) {
                // LLM 决策但未选到工具（未调用 / 调了未知工具）
                log.warn("ToolNode: LLM 未给出有效工具调用，原因: {}", outcome.failReason);
                toolResults.put("__no_tool_matched__", outcome.failReason);
                emit(AgentEventType.TOOL_CALL, "工具匹配失败: " + todo.getDescription(),
                        buildCallData(todo, candidates, null, Map.of(), false, outcome.failReason));
            }
            // outcome == null 表示 LLM 调用本身异常，自然降级到下面的旧遍历逻辑
        }

        // 回退：旧"遍历候选执行"路径（chatModel 为 null 或 LLM 决策异常时）
        if (!llmDecisionUsed && toolResults.isEmpty()) {
            log.info("ToolNode: 走旧遍历路径执行候选工具（候选数={}）", candidates.size());
            // 旧路径无 LLM 决策，reason 为 null（前端隐藏"选中原因"块），candidates 经 buildCallData 透传为 tools
            emit(AgentEventType.TOOL_CALL, "调用工具: " + todo.getDescription(),
                    buildCallData(todo, candidates, null, Map.of(), false, null));
            for (ToolProvider tool : candidates) {
                try {
                    Map<String, Object> params = new HashMap<>();
                    params.put("todo_description", todo.getDescription());
                    params.put("instructions", state.instructions());
                    String sessionId = state.sessionId();
                    if (sessionId != null && !sessionId.isBlank()) {
                        params.put("sessionId", sessionId);
                    }
                    Object result = executeWithRetry(tool, params);
                    toolResults.put(tool.name(), result);
                    handleFileWriteSideEffect(tool, result, state, todo, files);
                    advanceTddPhase(state, tool.name(), result);
                    String lintSig = runProfileLint(state, tool.name(), result, todo);
                    if (lintSig != null && lintInterruptReason == null) {
                        lintInterruptReason = lintSig;
                        lintInterruptSeverity = "high";
                    }
                    // 每个候选执行后补发一次 TOOL_CALL，携带 toolName/arguments（前端可看实际选中工具与参数）
                    Map<String, Object> execArgs = new HashMap<>(params);
                    emit(AgentEventType.TOOL_CALL, "调用工具: " + todo.getDescription(),
                            buildCallData(todo, candidates, tool, execArgs, true, null));
                    log.info("ToolNode: 工具 [{}] 执行成功（旧路径）", tool.name());
                } catch (Exception e) {
                    log.error("ToolNode: 工具 [{}] 执行失败（旧路径）", tool.name(), e);
                    toolResults.put(tool.name(), "执行失败: " + e.getMessage());
                }
            }
        }

        // 全部候选都被过滤时（candidates 为空）
        boolean noToolMatched = toolResults.isEmpty();
        if (noToolMatched) {
            log.warn("ToolNode: 无工具匹配 TODO [{}]，标记 FAILED", todo.getDescription());
            String missReason = String.format(
                    "工具匹配失败：TODO [%s] 未命中任何工具的触发条件。" +
                            "请改用 direct 策略回答，或重写 TODO 描述使其包含明确的工具触发关键词。",
                    todo.getDescription());
            toolResults.put("__no_tool_matched__", missReason);
            emit(AgentEventType.TOOL_CALL, "工具匹配失败: " + todo.getDescription(),
                    Map.of("todo", todo, "reason", missReason, "matched", false));
        }

        // 更新 TODO 状态：有失败 → FAILED，否则 → COMPLETED
        boolean hasFailure = toolResults.values().stream()
                .anyMatch(v -> v instanceof String s
                        && (s.startsWith("执行失败:") || s.startsWith("工具匹配失败")));
        TodoStatus resultStatus = hasFailure ? TodoStatus.FAILED : TodoStatus.COMPLETED;

        String readableResult = extractReadableResult(toolResults);

        List<TodoItem> updatedTodos = new ArrayList<>(state.todos());
        TodoItem updatedTodo = null;
        for (int i = 0; i < updatedTodos.size(); i++) {
            if (updatedTodos.get(i).getId().equals(todo.getId())) {
                updatedTodo = TodoItem.builder()
                        .id(todo.getId())
                        .description(todo.getDescription())
                        .status(resultStatus)
                        .result(readableResult)
                        .assignedAgent(todo.getAssignedAgent())
                        .updatedAt(LocalDateTime.now())
                        .build();
                updatedTodos.set(i, updatedTodo);
                break;
            }
        }

        if (updatedTodo != null && resultStatus == TodoStatus.COMPLETED) {
            emit(AgentEventType.TODO_COMPLETED, "已完成: " + todo.getDescription(),
                    Map.of("todo", updatedTodo, "toolResults", toolResults));
        }

        // ===== 多模态下发通道 =====
        // FileReadTool 等工具返回的 JSON 含 multimodal:true + base64 时，
        // 把 base64 解析为 ImageContent 追加为 UserMessage，让下一轮 LLM 真正"看到"图片；
        // 同时把 toolResults 中保存的副本替换为剔除 base64 的元数据，避免 base64 被当成
        // 文本喂回 LLM（浪费 token / 干扰 PlanNode 文本 prompt）。
        List<ChatMessage> multimodalMessages = extractMultimodalMessages(toolResults);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(AiMessage.from(String.format("工具调用完成: %s", toolResults.keySet())));
        messages.addAll(multimodalMessages);

        Map<String, Object> output = new HashMap<>();
        output.put(DeepAgentState.TODOS, updatedTodos);
        output.put(DeepAgentState.FILES, files);
        output.put(DeepAgentState.MESSAGES, messages);
        if (lintInterruptReason != null) {
            output.put(DeepAgentState.NEED_CONFIRMATION, true);
            output.put(DeepAgentState.INTERRUPT_REASON, lintInterruptReason);
            output.put(DeepAgentState.INTERRUPT_SEVERITY, lintInterruptSeverity);
        }
        return output;
    }

    /**
     * 扫描 toolResults 中的字符串结果，解析含 multimodal:true 的 JSON 片段。
     * <p>
     * 命中时：
     * <ol>
     *   <li>构造 {@link UserMessage}（TextContent + ImageContent）加入下一轮消息</li>
     *   <li>原地替换 toolResults 中该 key 的值为去除 base64 字段后的 JSON（防止 base64 进入 todo.result）</li>
     * </ol>
     * </p>
     * 设计目标：让 ToolNode 在不破坏既有 String 返回契约的前提下，支持工具下发多模态内容。
     */
    private List<ChatMessage> extractMultimodalMessages(Map<String, Object> toolResults) {
        List<ChatMessage> result = new ArrayList<>();
        if (toolResults == null || toolResults.isEmpty()) {
            return result;
        }
        for (Map.Entry<String, Object> entry : toolResults.entrySet()) {
            Object v = entry.getValue();
            if (!(v instanceof String s) || s.isBlank()) continue;
            if (!s.contains("\"multimodal\"") || !s.contains("\"base64\"")) continue;
            try {
                cn.hutool.json.JSONObject json = cn.hutool.json.JSONUtil.parseObj(s);
                if (!json.getBool("multimodal", false)) continue;
                String base64 = json.getStr("base64");
                String mimeType = json.getStr("mimeType", "image/png");
                String path = json.getStr("path", "unknown");
                if (base64 == null || base64.isBlank()) continue;

                // 构造下一轮 UserMessage：文本提示 + 图片
                UserMessage msg = UserMessage.from(
                        TextContent.from("工具 " + entry.getKey() + " 返回了图片: " + path),
                        ImageContent.from(base64, mimeType)
                );
                result.add(msg);

                // 原地剔除 base64/multimodal 字段，避免 base64 文本进入 todo.result → PlanNode prompt
                json.remove("base64");
                json.remove("multimodal");
                json.set("deliveredAs", "multimodal_image_attached");
                json.set("note", "图片已作为多模态消息随上一条工具调用下发");
                entry.setValue(json.toString());
            } catch (Exception e) {
                log.warn("ToolNode: 解析多模态工具结果失败 tool={}, err={}", entry.getKey(), e.getMessage());
            }
        }
        return result;
    }

    /**
     * 调用 LangChain4j ChatModel 让 LLM 选定工具 + 填写参数。
     * 失败返回 null（由 apply 回退旧逻辑）；成功但 LLM 没选工具时返回带 failReason 的 outcome。
     */
    private LlmDecisionOutcome decideByLlm(List<ToolProvider> candidates, TodoItem todo, DeepAgentState state) {
        // 优先：流式 LLM 调用（长输出场景如 file_write 生成 HTML，避免整体超时）
        if (streamingChatModel != null) {
            try {
                LlmDecisionOutcome streamingOutcome = decideByLlmStreaming(candidates, todo, state);
                if (streamingOutcome != null) {
                    return streamingOutcome;
                }
            } catch (com.hypersense.boot.common.exception.BusinessException be) {
                // 参数校验异常向上抛，触发外层重试
                throw be;
            } catch (Exception e) {
                log.warn("ToolNode: 流式 LLM 决策失败，回退到同步: {}", e.getMessage());
            }
        }
        return decideByLlmSync(candidates, todo, state);
    }

    /**
     * 流式版本：用 SSE chunk 接收 LLM 响应，本质规避整体 timeout。
     * 通过 CompletableFuture 在 onCompleteResponse 中同步等待。
     */
    private LlmDecisionOutcome decideByLlmStreaming(List<ToolProvider> candidates, TodoItem todo, DeepAgentState state) {
        final int maxRetry = 2;
        String feedback = null;
        for (int attempt = 0; attempt <= maxRetry; attempt++) {
            List<ToolSpecification> specs = new ArrayList<>();
            for (ToolProvider t : candidates) {
                specs.add(t.specification());
            }

            String systemPrompt = buildDecideSystemPrompt();
            String userPrompt = buildUserPrompt(todo, state);
            if (feedback != null) {
                userPrompt = userPrompt + "\n\n[上次调用错误反馈] " + feedback + "\n请基于上述反馈修正参数后重新调用工具。";
            }

            List<ChatMessage> messages = List.of(
                    SystemMessage.from(systemPrompt),
                    UserMessage.from(userPrompt)
            );

            // 同步等待流式响应：用 CompletableFuture 桥接
            java.util.concurrent.CompletableFuture<ChatResponse> future = new java.util.concurrent.CompletableFuture<>();
            dev.langchain4j.model.chat.request.ChatRequest req = dev.langchain4j.model.chat.request.ChatRequest.builder()
                    .messages(messages)
                    .toolSpecifications(specs)
                    .maxOutputTokens(LLM_MAX_OUTPUT_TOKENS)
                    .build();

            // 主线程捕获事件消费者（langchain4j 流式回调在 HTTP 线程，ThreadLocal 拿不到）
            final java.util.function.Consumer<com.hypersense.boot.framework.agents.model.AgentEvent> eventConsumer =
                    com.hypersense.boot.framework.agents.engine.SubAgentEventBus.get();
            // 流式 token 节流状态：200ms 节流 + 累积全文（前端可直接用 accumulated 覆盖式渲染）
            final StringBuilder codeAccumulator = new StringBuilder();
            final long[] lastEmitTime = {0};
            final String streamSessionId = state.sessionId();
            final String streamTodoId = todo.getId();
            final String streamTodoDesc = todo.getDescription();

            streamingChatModel.chat(req, new dev.langchain4j.model.chat.response.StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partialResponse) {
                    emitCodeStreaming(eventConsumer, codeAccumulator, lastEmitTime,
                            streamTodoId, streamTodoDesc, streamSessionId, partialResponse);
                }

                @Override
                public void onCompleteResponse(ChatResponse completeResponse) {
                    future.complete(completeResponse);
                }

                @Override
                public void onError(Throwable error) {
                    future.completeExceptionally(error);
                }
            });

            ChatResponse resp;
            try {
                resp = future.get(10, java.util.concurrent.TimeUnit.MINUTES);
            } catch (Exception e) {
                log.warn("ToolNode: 流式 LLM 调用失败 attempt={} err={}", attempt, e.getMessage());
                future.cancel(true);
                if (attempt < maxRetry) continue;
                return null;
            }
            AiMessage ai = resp.aiMessage();

            if (!ai.hasToolExecutionRequests()) {
                LlmDecisionOutcome o = new LlmDecisionOutcome();
                o.failReason = "LLM 未调用任何工具（可能 TODO 不需要工具或上下文不足）";
                return o;
            }

            ToolExecutionRequest req2 = ai.toolExecutionRequests().get(0);
            String reqName = normalizeToolAlias(req2.name(), state);
            // Plan A：在工具选择前先校验 active profile 的 allowedTools 白名单（流式路径）
            LlmDecisionOutcome profileReject = rejectIfNotInProfile(reqName, state);
            if (profileReject != null) {
                return profileReject;
            }
            Optional<ToolProvider> chosenOpt = candidates.stream()
                    .filter(t -> t.name().equals(reqName))
                    .findFirst();
            if (chosenOpt.isEmpty()) {
                LlmDecisionOutcome o = new LlmDecisionOutcome();
                o.failReason = "LLM 调用了未注册的工具: " + reqName;
                return o;
            }

            try {
                LlmDecisionOutcome o = new LlmDecisionOutcome();
                o.chosen = chosenOpt.get();
                o.args = ToolProvider.parseArguments(req2.arguments());
                // content 兜底：LLM 调用了 file_write/reply_text 但 content 缺失时，
                // 尝试从 ai.text() 提取 HTML/纯文本作为 content（处理国产模型把内容放 reasoning 而非 args 的场景）
                rescueContentFromText(o.chosen.name(), o.args, ai.text());
                validateToolArgs(o.chosen.name(), o.args);
                String sessionId = state.sessionId();
                if (sessionId != null && !sessionId.isBlank()) {
                    o.args.put("sessionId", sessionId);
                }
                o.args.put("todo_description", todo.getDescription());
                o.args.put("instructions", state.instructions());
                // Plan #1：function calling 模式下 LLM 把内容塞 tool args，
                // onPartialResponse 通常不会被回调（langchain4j 不流式吐 args 片段），
                // 导致 file_write / file_write_chunk 在 function-call 路径下前端永远收不到
                // CODE_STREAMING。这里在「LLM 完成 → 工具执行」之间补发一次：
                // 把 content / chunk 字段作为 accumulated 整体推送，前端能立即看到完整代码气泡。
                // 去重：纯文本回退路径已在 onPartialResponse 里 emit 过（accumulator 非空），
                //      function-call 路径下 accumulator 为空才补发，避免重复。
                emitCodeStreamingForFileWriteOnce(eventConsumer, codeAccumulator,
                        streamTodoId, streamTodoDesc, streamSessionId,
                        o.chosen.name(), o.args);
                return o;
            } catch (com.hypersense.boot.common.exception.BusinessException be) {
                log.error("ToolNode: 流式参数校验失败 attempt={} err={}", attempt, be.getMessage());

                // file_write content 缺失是结构性问题（LLM 不擅长通过 function calling 传长 HTML），
                // 重试无意义，立即走纯文本回退：让 LLM 以普通 chat 方式直出 HTML
                if (isFileWriteContentMissing(be)) {
                    // 静默：内部过渡失败，不发 SSE（避免前端「工具调用失败」噪音）
                    // 前端只会看到最终的「文件已生成」(TOOL_CALL) 或最终失败
                    // 仅后端日志记录，立即触发纯文本回退
                    log.warn("ToolNode: file_write content 缺失（静默处理），跳过重试，立即触发纯文本回退（流式路径）. todoId={} reason={}",
                            todo.getId(), be.getMessage());
                    LlmDecisionOutcome fallback = tryPlainTextFallbackForFileWrite(
                            reqName, candidates, todo, state, be);
                    if (fallback != null) {
                        return fallback;
                    }
                    LlmDecisionOutcome o = new LlmDecisionOutcome();
                    o.failReason = "工具参数校验失败: " + be.getMessage();
                    return o;
                }

                // 其他校验错误：保持原有 maxRetry 重试逻辑
                if (attempt < maxRetry) {
                    feedback = be.getMessage();
                    try {
                        Map<String, Object> payload = new HashMap<>();
                        payload.put("sessionId", state.sessionId());
                        payload.put("todoId", todo.getId());
                        payload.put("error", be.getMessage());
                        payload.put("attempt", attempt + 1);
                        payload.put("willRetry", true);
                        emit(AgentEventType.TOOL_ERROR,
                                "工具参数校验失败（第 " + (attempt + 1) + " 次，将重试）: " + be.getMessage(),
                                payload);
                    } catch (Exception ignored) {}
                    continue;
                }
                try {
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("sessionId", state.sessionId());
                    payload.put("todoId", todo.getId());
                    payload.put("error", be.getMessage());
                    payload.put("willRetry", false);
                    emit(AgentEventType.TOOL_ERROR,
                            "工具参数校验失败（已耗尽重试）: " + be.getMessage(),
                            payload);
                } catch (Exception ignored) {}
                // 纯文本回退：仅对 file_write 生效（其他工具不回退）
                LlmDecisionOutcome fallback = tryPlainTextFallbackForFileWrite(
                        "file_write", candidates, todo, state, be);
                if (fallback != null) {
                    return fallback;
                }
                LlmDecisionOutcome o = new LlmDecisionOutcome();
                o.failReason = "工具参数校验失败: " + be.getMessage();
                return o;
            }
        }
        return null;
    }

    /**
     * 共享 system prompt：流式与同步 decideByLlm 复用，避免约束规则双份维护。
     */
    private String buildDecideSystemPrompt() {
        return "你是工具选择器。根据当前 TODO 和完整上下文，选择一个最合适的工具并填写其参数。" +
                "只输出工具调用，不要任何解释或额外文本。\n" +
                "重要约束：\n" +
                "1. 参数填写分两类，必须严格区分：\n" +
                "   A. 数据/路径/外部事实类参数（如 internet_search 的 query、file_read 的 path）：\n" +
                "      必须从 state（前序 TODO 的 result、用户原始输入、state.files 列表）中提取，禁止凭空编造。\n" +
                "      特别地，file_read 的 path 必须出现在 state.files 中，若 state.files 为空或不含该文件，禁止调用 file_read。\n" +
                "   B. 创作类参数（如 file_write 的 content、reply_text 的 content）：\n" +
                "      - 当 TODO 描述含「创建/编写/设计/生成 HTML/CSS/JS/代码/文案/页面」等创作动词时，\n" +
                "        由你（LLM）根据用户需求和上下文原创生成完整内容，这是允许且必须的，禁止以'无前序产出'为由放弃。\n" +
                "      - 当 TODO 描述含「保存/写入/落地」前序已经产出的内容时，必须从 state 前序 result 完整复制原文。\n" +
                "2. 调用 file_write 工具时必须遵守：\n" +
                "   - filename 参数：目标文件名（如 pet_adoption.html）\n" +
                "   - content 参数：完整的文件源码，不得有任何省略、截断、占位符（如 '...' 或 '<原有代码>'）\n" +
                "   - 大文件（如长 HTML 页面）也必须完整传入 content，模型上下文足够容纳\n" +
                "   - 错误示例：content='...' 或 content='<html>...</html>' 都是禁止的\n" +
                "   - 正确示例：content 必须包含从 <!DOCTYPE html> 到 </html> 的完整代码\n" +
                "   - 若是原创生成场景，content 必须是你自己产出的完整、可直接运行的源码。\n" +
                "3. 仅在以下情况可以放弃调用工具（不调用任何工具）：\n" +
                "   - TODO 要求读取/查询的文件或数据明确不存在（前序步骤已报错或 state.files 中无对应文件）；\n" +
                "   - TODO 实质上不需要任何工具操作（如纯澄清类回复已由 reply_text 之外的路径处理）。\n" +
                "   除以上两种情况外，必须调用一个工具完成 TODO，不允许因为'上下文不足'而放弃。";
    }

    /**
     * 同步版本：旧 decideByLlm 主体逻辑（保留作为流式不可用时的回退路径）。
     */
    private LlmDecisionOutcome decideByLlmSync(List<ToolProvider> candidates, TodoItem todo, DeepAgentState state) {
        // 最大重试次数：LLM 漏传关键参数时反馈错误让其重新调用
        final int maxRetry = 2;
        String feedback = null;
        for (int attempt = 0; attempt <= maxRetry; attempt++) {
            // reqName 提到 try 外：catch 块（纯文本回退）需要按实际工具名分支
            String reqName = null;
            try {
                List<ToolSpecification> specs = new ArrayList<>();
                for (ToolProvider t : candidates) {
                    specs.add(t.specification());
                }

                String systemPrompt = buildDecideSystemPrompt();
                String userPrompt = buildUserPrompt(todo, state);
                if (feedback != null) {
                    userPrompt = userPrompt + "\n\n[上次调用错误反馈] " + feedback + "\n请基于上述反馈修正参数后重新调用工具。";
                }

                List<ChatMessage> messages = List.of(
                        SystemMessage.from(systemPrompt),
                        UserMessage.from(userPrompt)
                );

                ChatResponse resp = chatModel.chat(ChatRequest.builder()
                        .messages(messages)
                        .toolSpecifications(specs)
                        .maxOutputTokens(LLM_MAX_OUTPUT_TOKENS)
                        .build());
                AiMessage ai = resp.aiMessage();

                if (!ai.hasToolExecutionRequests()) {
                    LlmDecisionOutcome o = new LlmDecisionOutcome();
                    o.failReason = "LLM 未调用任何工具（可能 TODO 不需要工具或上下文不足）";
                    return o;
                }

                ToolExecutionRequest req = ai.toolExecutionRequests().get(0);
                reqName = normalizeToolAlias(req.name(), state);
                // Plan A：在工具选择前先校验 active profile 的 allowedTools 白名单（同步路径）
                LlmDecisionOutcome profileReject = rejectIfNotInProfile(reqName, state);
                if (profileReject != null) {
                    return profileReject;
                }
                final String chosenReqName = reqName; // lambda 捕获需要 effectively final
                Optional<ToolProvider> chosenOpt = candidates.stream()
                        .filter(t -> t.name().equals(chosenReqName))
                        .findFirst();
                if (chosenOpt.isEmpty()) {
                    LlmDecisionOutcome o = new LlmDecisionOutcome();
                    o.failReason = "LLM 调用了未注册的工具: " + reqName;
                    return o;
                }

                LlmDecisionOutcome o = new LlmDecisionOutcome();
                o.chosen = chosenOpt.get();
                o.args = ToolProvider.parseArguments(req.arguments());
                // content 兜底：同步路径同样支持从 ai.text() 抢救 HTML 内容
                rescueContentFromText(o.chosen.name(), o.args, ai.text());
                // 参数预校验：content 缺失/占位符等在调用工具前提前拦截
                validateToolArgs(o.chosen.name(), o.args);
                // 透传 sessionId 供沙箱工具等需要会话隔离的组件使用
                String sessionId = state.sessionId();
                if (sessionId != null && !sessionId.isBlank()) {
                    o.args.put("sessionId", sessionId);
                }
                // 兜底保留 todo_description，供 FileWriteTool 自动命名时取用
                o.args.put("todo_description", todo.getDescription());
                o.args.put("instructions", state.instructions());
                // Plan #1（同步路径）：与 decideByLlmStreaming 对齐，function calling 模式下
                // 补发一次 CODE_STREAMING，避免同步路径 file_write/file_write_chunk 前端永远看不到代码气泡。
                // 同步路径没有流式 token（chatModel.chat 是一次性返回），onPartialResponse 不存在；
                // 直接把 LLM 填好的 content/chunk 整体作为 accumulated 推送。
                try {
                    java.util.function.Consumer<com.hypersense.boot.framework.agents.model.AgentEvent> syncConsumer =
                            com.hypersense.boot.framework.agents.engine.SubAgentEventBus.get();
                    emitCodeStreamingForFileWriteOnce(syncConsumer, new StringBuilder(),
                            todo.getId(), todo.getDescription(), state.sessionId(),
                            o.chosen.name(), o.args);
                } catch (Throwable ignored) {
                    // 同步路径补发失败绝不能影响主流程
                }
                return o;
            } catch (com.hypersense.boot.common.exception.BusinessException be) {
                // 参数校验失败：不允许静默降级，反馈给 LLM 重试
                log.error("ToolNode: 工具参数校验失败 attempt={} tool={} err={}", attempt, be.getMessage());

                // file_write content 缺失是结构性问题（LLM 不擅长通过 function calling 传长 HTML），
                // 重试无意义，立即走纯文本回退：让 LLM 以普通 chat 方式直出 HTML
                if (isFileWriteContentMissing(be)) {
                    // 静默：内部过渡失败，不发 SSE（避免前端「工具调用失败」噪音）
                    // 前端只会看到最终的「文件已生成」(TOOL_CALL) 或最终失败
                    // 仅后端日志记录，立即触发纯文本回退
                    log.warn("ToolNode: file_write content 缺失（静默处理），跳过重试，立即触发纯文本回退（同步路径）. todoId={} reason={}",
                            todo.getId(), be.getMessage());
                    LlmDecisionOutcome fallback = tryPlainTextFallbackForFileWrite(
                            reqName, candidates, todo, state, be);
                    if (fallback != null) {
                        return fallback;
                    }
                    LlmDecisionOutcome o = new LlmDecisionOutcome();
                    o.failReason = "工具参数校验失败: " + be.getMessage();
                    return o;
                }

                // 其他校验错误：保持原有 maxRetry 重试逻辑
                if (attempt < maxRetry) {
                    feedback = be.getMessage();
                    // 发出 TOOL_ERROR SSE 事件，前端可见
                    try {
                        Map<String, Object> payload = new HashMap<>();
                        payload.put("sessionId", state.sessionId());
                        payload.put("todoId", todo.getId());
                        payload.put("error", be.getMessage());
                        payload.put("attempt", attempt + 1);
                        payload.put("willRetry", true);
                        emit(AgentEventType.TOOL_ERROR,
                                "工具参数校验失败（第 " + (attempt + 1) + " 次，将重试）: " + be.getMessage(),
                                payload);
                    } catch (Exception ignored) {}
                    continue;
                }
                // 重试耗尽：最后一次仍失败，向上抛出由 finalize 兜底
                try {
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("sessionId", state.sessionId());
                    payload.put("todoId", todo.getId());
                    payload.put("error", be.getMessage());
                    payload.put("willRetry", false);
                    emit(AgentEventType.TOOL_ERROR,
                            "工具参数校验失败（已耗尽重试）: " + be.getMessage(),
                            payload);
                } catch (Exception ignored) {}
                // 纯文本回退：仅对 file_write 生效（其他工具不回退）
                LlmDecisionOutcome fallback = tryPlainTextFallbackForFileWrite(
                        "file_write", candidates, todo, state, be);
                if (fallback != null) {
                    return fallback;
                }
                LlmDecisionOutcome o = new LlmDecisionOutcome();
                o.failReason = "工具参数校验失败: " + be.getMessage();
                return o;
            } catch (Exception e) {
                log.warn("ToolNode: LLM 决策调用失败，将回退旧遍历逻辑: {}", e.getMessage());
                return null;
            }
        }
        // 理论不可达
        return null;
    }

    /**
     * 判断 BusinessException 是否为「file_write content 缺失 / HTML 质量审计失败」场景。
     * <p>
     * 这些场景都是结构性问题（国产 LLM 不擅长通过 function calling 传长 content，
     * 或给出的 content 是残缺/占位符 HTML），重试无意义，应立即触发纯文本回退，
     * 让 LLM 以普通 chat 方式直出 HTML。
     * </p>
     * <p>匹配范围：content 缺失、HTML 长度异常、含占位符、HTML 结构不完整。</p>
     */
    private boolean isFileWriteContentMissing(com.hypersense.boot.common.exception.BusinessException be) {
        if (be == null || be.getMessage() == null) return false;
        String msg = be.getMessage();
        // 匹配 validateToolArgs 中抛出的所有 HTML 质量审计类错误，立即触发纯文本回退
        // 同时覆盖 file_write（content 字段）和 file_write_chunk（chunk 字段）两条路径
        return msg.contains("参数缺失")
                || msg.contains("长度异常")
                || msg.contains("含占位符")
                || msg.contains("HTML 结构不完整");
    }

    /**
     * 纯文本回退桥接：当 file_write 重试耗尽后，尝试用纯文本路径生成 content 并执行工具。
     * <p>
     * 仅对名为 file_write 的候选工具生效。流程：
     * <ol>
     *   <li>在 candidates 中找 file_write 工具；找不到则返回 null</li>
     *   <li>构造 fallback args（保留 LLM 上一轮给定的 filename；filename 缺失时从 todo 描述推断）</li>
     *   <li>调用 {@link #generateContentByPlainText} 生成 content 并执行 FileWriteTool</li>
     *   <li>成功则包装为 outcome 返回（chosen = file_write 工具，args 含 content，extraResult 透传工具结果）；
     *       失败则返回 null，让上层返回 failReason</li>
     * </ol>
     * </p>
     *
     * @param toolName   期望回退的工具名（固定 "file_write"，参数化以便未来扩展）
     * @param candidates 当前候选工具列表
     * @param todo       当前 TODO
     * @param state      Agent 状态
     * @param cause      触发回退的原始异常（仅用于日志）
     * @return 成功的 outcome，或 null 表示回退未启用/失败
     */
    private LlmDecisionOutcome tryPlainTextFallbackForFileWrite(String toolName,
                                                                List<ToolProvider> candidates,
                                                                TodoItem todo,
                                                                DeepAgentState state,
                                                                Throwable cause) {
        // 支持 file_write 和 file_write_chunk 两条路径的纯文本回退
        // 设计：file_write_chunk 是 design profile 的主路径，但 function calling JSON 嵌 HTML
        // 经常因引号转义 / token 上限产生残缺 chunk——回退到纯文本直出可绕开这些问题
        boolean isFileWrite = "file_write".equals(toolName);
        boolean isFileWriteChunk = "file_write_chunk".equals(toolName);
        if (!isFileWrite && !isFileWriteChunk) {
            return null;
        }
        final String targetToolName = isFileWrite ? "file_write" : "file_write_chunk";
        Optional<ToolProvider> fileWriteOpt = candidates.stream()
                .filter(t -> targetToolName.equals(t.name()))
                .findFirst();
        if (fileWriteOpt.isEmpty()) {
            log.warn("ToolNode: {} 纯文本回退跳过——候选工具中无 {}", targetToolName, targetToolName);
            return null;
        }
        ToolProvider fileWriteTool = fileWriteOpt.get();

        // 静默：内部过渡通知，不发 SSE（避免前端噪音）
        // 前端只会看到 fallback_success 的「文件已生成」(TOOL_CALL) 或最终失败
        // 仅后端日志保留，用于调试
        log.info("ToolNode: {} 切换纯文本直出模式（function-call 失败，正在重新生成）. todoId={} reason={}",
                targetToolName, todo.getId(), cause == null ? "null" : cause.getMessage());

        // 构造 fallback args：保留 filename（若 LLM 上一轮给过）
        Map<String, Object> fallbackArgs = new HashMap<>();
        String desc = todo.getDescription() == null ? "" : todo.getDescription();
        fallbackArgs.put("todo_description", desc);
        fallbackArgs.put("instructions", state.instructions());
        // file_write_chunk 单次模式必需字段
        if (isFileWriteChunk) {
            fallbackArgs.put("mode", "write");
        }
        // filename 兜底：从 TODO 描述提取 .html 文件名
        java.util.regex.Matcher fnMatcher = java.util.regex.Pattern.compile(
                "([\\w\\-]+\\.html?)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(desc);
        if (fnMatcher.find()) {
            fallbackArgs.put("filename", fnMatcher.group(1));
        } else if (!fallbackArgs.containsKey("filename") || fallbackArgs.get("filename") == null) {
            // 最终兜底：根据场景类型生成语义化文件名
            String defaultName;
            String lowerDesc = desc.toLowerCase();
            if (lowerDesc.contains("dashboard") || lowerDesc.contains("后台") || lowerDesc.contains("管理")) {
                defaultName = "dashboard.html";
            } else if (lowerDesc.contains("login") || lowerDesc.contains("登录")) {
                defaultName = "login.html";
            } else if (lowerDesc.contains("landing") || lowerDesc.contains("首页")) {
                defaultName = "index.html";
            } else {
                defaultName = "page_" + (System.currentTimeMillis() % 100000) + ".html";
            }
            fallbackArgs.put("filename", defaultName);
            log.info("ToolNode: filename 兜底为 {}", defaultName);
        }

        try {
            // file_write_chunk 用 chunk 字段，file_write 用 content 字段
            // generateContentByPlainText 内部把生成结果写入 "content" 字段，
            // 此处对 file_write_chunk 路径在调用前把字段名映射好（在 generateContentByPlainText 之后修正）
            Map<String, Object> toolResult = generateContentByPlainText(
                    fileWriteTool, fallbackArgs, state, todo);
            // file_write_chunk 字段名修正：把 content 复制到 chunk（工具实际读取 chunk）
            if (isFileWriteChunk && fallbackArgs.containsKey("content")) {
                fallbackArgs.put("chunk", fallbackArgs.get("content"));
            }
            // 回退成功：把执行结果透传到 outcome，外层 apply 会调用 handleFileWriteSideEffect
            LlmDecisionOutcome o = new LlmDecisionOutcome();
            o.chosen = fileWriteTool;
            o.args = fallbackArgs;  // 此 args 已被 generateContentByPlainText 填充 content
            o.toolResultOverride = toolResult;  // 直接复用已执行结果，外层不再重复 execute

            // 回退成功事件（让前端能区分成功与失败）
            try {
                Map<String, Object> successPayload = new HashMap<>();
                successPayload.put("sessionId", state.sessionId());
                successPayload.put("todoId", todo.getId());
                successPayload.put("phase", "fallback_success");
                successPayload.put("filename", fallbackArgs.get("filename"));
                successPayload.put("fallback", "plain_text_direct_output");
                emit(AgentEventType.TOOL_CALL,
                        "纯文本直出成功，文件已生成: " + fallbackArgs.get("filename"),
                        successPayload);
            } catch (Exception ignored) {}

            return o;
        } catch (Exception e) {
            log.error("ToolNode: file_write 纯文本回退失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 纯文本回退：当 function-call 模式下 file_write 重试 maxRetry 次后仍无 content 时调用。
     * <p>
     * 部分国产 LLM 在 function calling 模式下生成长 HTML 不可靠（把内容输出到 reasoning 而非 args），
     * 本方法绕开 function calling：以普通 chat 方式（不带任何 tools）让 LLM 直接输出 HTML 文本，
     * 再用 {@link #rescueContentFromText} 从文本提取 HTML 包装成 file_write 调用结果。
     * </p>
     * <p>双路径：优先流式（streamingChatModel），无则同步 chatModel。</p>
     *
     * @param fileWriteTool FileWriteTool 实例
     * @param originalArgs  LLM 上一轮给出的 args（含 filename、sessionId、todo_description 等，content 为空）
     * @param state         Agent 运行状态
     * @param todo          当前 TODO
     * @return 工具执行结果 Map（与 FileWriteTool.execute 返回值一致）
     */
    private Map<String, Object> generateContentByPlainText(ToolProvider fileWriteTool,
                                                           Map<String, Object> originalArgs,
                                                           DeepAgentState state,
                                                           TodoItem todo) throws Exception {
        // 1. 构造纯文本 prompt（不带任何 tools）
        String systemPrompt = buildPlainTextFallbackSystemPrompt();
        String userPrompt = buildPlainTextFallbackUserPrompt(todo, state);

        List<ChatMessage> messages = List.of(
                SystemMessage.from(systemPrompt),
                UserMessage.from(userPrompt)
        );

        // 2. 调用 LLM（不带 tools 参数）
        String rawText;
        if (streamingChatModel != null) {
            rawText = callLlmStreamingForPlainText(messages, todo, state);
        } else {
            rawText = callLlmSyncForPlainText(messages);
        }
        if (rawText == null || rawText.isBlank()) {
            throw new com.hypersense.boot.common.exception.BusinessException(
                    "file_write 纯文本回退失败：LLM 未返回任何文本");
        }

        // 3. 从响应文本提取 HTML（复用 rescueContentFromText 的提取规则，借助临时 args）
        // 优先解析 <artifact> 标签（open-design 模式，主路径）；退回 ```html / <!DOCTYPE> 等老规则
        Map<String, Object> rescueArgs = new HashMap<>(originalArgs);
        rescueArgs.remove("content");  // 确保兜底逻辑真正执行
        String rescueToolName = fileWriteTool.name();  // file_write 或 file_write_chunk
        rescueContentFromText(rescueToolName, rescueArgs, rawText);
        Object rescued = rescueArgs.get("content");
        if (rescued == null || rescued.toString().isBlank()) {
            log.error("ToolNode: {} 纯文本回退失败，无法从 LLM 文本中提取 HTML（文本长度={}）",
                    rescueToolName, rawText.length());
            throw new com.hypersense.boot.common.exception.BusinessException(
                    rescueToolName + " 纯文本回退失败：LLM 文本未包含 <artifact> 标签或有效 HTML");
        }
        // 若 artifact 标签带了 path，filename 可能已被 rescue 更新——同步回 originalArgs 供后续 execArgs 使用
        if (rescueArgs.get("filename") != null) {
            originalArgs.put("filename", rescueArgs.get("filename"));
        }

        // 4. 塞回 args 并调用 FileWriteTool 执行
        Map<String, Object> execArgs = new HashMap<>(originalArgs);
        execArgs.put("content", rescued);
        // file_write_chunk 工具契约读 chunk 字段（虽然 FileWriteChunkTool pickString 会兜底 content，
        // 此处显式同步以保持字段一致性，避免依赖隐式 fallback）
        if ("file_write_chunk".equals(rescueToolName)) {
            execArgs.put("chunk", rescued);
        }
        String sessionId = state.sessionId();
        if (sessionId != null && !sessionId.isBlank()) {
            execArgs.put("sessionId", sessionId);
        }
        execArgs.put("todo_description", todo.getDescription());
        execArgs.put("instructions", state.instructions());

        log.info("ToolNode: 走纯文本回退路径，从 LLM 直出文本提取 HTML {} 字符，调用 FileWriteTool",
                rescued.toString().length());

        Object result = fileWriteTool.execute(execArgs);
        if (result instanceof Map<?, ?>) {
            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result;
            return resultMap;
        }
        // 兜底：非 Map 返回包装为 Map
        Map<String, Object> wrap = new HashMap<>();
        wrap.put("content", result == null ? "" : result.toString());
        return wrap;
    }

    /**
     * 纯文本回退路径的 system prompt：约束 LLM 用 &lt;artifact&gt; 标签输出 HTML（open-design 模式）。
     * <p>open-design 模式核心：用 XML 风格的 {@code <artifact path="xxx.html">...</artifact>}
     * 包裹完整 HTML，绕开 JSON 函数调用的转义与 token 上限问题——这是 design profile 的主路径。</p>
     */
    private String buildPlainTextFallbackSystemPrompt() {
        return "【强制输出格式 - artifact 模式（严格遵守）】\n" +
                "你是 HTML 生成器，唯一职责是用 <artifact> 标签输出完整可运行的 HTML 文档。\n\n" +
                "输出格式（必须严格遵守）：\n" +
                "<artifact path=\"文件名.html\">\n" +
                "<!DOCTYPE html>\n" +
                "... 完整 HTML 内容 ...\n" +
                "</artifact>\n\n" +
                "硬性规则（违反任何一条即视为失败）：\n" +
                "1. 整个响应必须且仅能包含一个 <artifact>...</artifact> 块\n" +
                "2. <artifact> 标签必须带 path 属性（值为文件名，如 landing.html）\n" +
                "3. <artifact> 内部第一行必须是 <!DOCTYPE html>\n" +
                "4. <artifact> 内部最后必须以 </html> 结尾，</artifact> 之后不得有任何字符\n" +
                "5. 严禁输出任何礼貌用语：「好的」「我来」「请稍等」「以下」「为您」「根据您的要求」等\n" +
                "6. 严禁使用 markdown 代码块包裹（禁止使用 ```html 或 ```）\n" +
                "7. 严禁输出任何解释、思考、说明、前言、后语\n" +
                "8. 所有 CSS 必须内联在 <style> 标签内，所有 JS 必须内联在 <script> 标签内\n" +
                "9. 禁止引用外部资源（除 CDN：tailwindcss、bootstrap、three.js 等允许）\n\n" +
                "技术规范：\n" +
                "- HTML5 语义化标签\n" +
                "- 移动端响应式（含 viewport meta）\n" +
                "- 现代视觉风格（柔和阴影、合理留白、对比鲜明的配色）\n" +
                "- 完整页面结构：header/nav/main/section/footer\n" +
                "- HTML 体量控制在 6K tokens / ~24KB 以内（CSS ≤ 200 行，body ≤ 300 行）\n\n" +
                "禁止思考、禁止解释、禁止前言。直接输出 <artifact path=\"page.html\"> 开头、</artifact> 结尾的完整内容。";
    }

    /**
     * 纯文本回退路径的 user prompt：注入用户原始需求 + 前序 TODO 上下文。
     */
    private String buildPlainTextFallbackUserPrompt(TodoItem todo, DeepAgentState state) {
        StringBuilder sb = new StringBuilder();
        sb.append("请生成以下任务所需的完整 HTML：\n\n");
        sb.append("当前任务: ").append(todo.getDescription() == null ? "" : todo.getDescription()).append("\n\n");

        String instructions = state.instructions() == null ? "" : state.instructions();
        sb.append("用户原始需求:\n").append(instructions).append("\n\n");

        // 前序 TODO 已完成的产出（设计要点可能在前序步骤中已规划）
        List<TodoItem> todos = state.todos();
        if (todos != null && !todos.isEmpty()) {
            StringBuilder prevBuf = new StringBuilder();
            int stepIdx = 0;
            String currentId = todo.getId();
            for (TodoItem t : todos) {
                if (t == null) continue;
                if (currentId != null && currentId.equals(t.getId())) continue;
                if (t.getStatus() != TodoStatus.COMPLETED) continue;
                String tDesc = t.getDescription() == null ? "" : t.getDescription();
                String tResult = t.getResult() == null ? "" : t.getResult();
                if (tResult.isBlank() && tDesc.isBlank()) continue;
                stepIdx++;
                prevBuf.append("[步骤 ").append(stepIdx).append("] ").append(tDesc).append("\n");
                prevBuf.append(tResult).append("\n\n");
            }
            if (prevBuf.length() > 0) {
                sb.append("前序步骤已规划的设计要求:\n").append(prevBuf).append("\n");
            }
        }

        sb.append("请用 <artifact path=\"page.html\">...</artifact> 标签输出完整 HTML（path 用任务描述中的文件名，没有就用 page.html）:");
        return sb.toString();
    }

    /**
     * 纯文本回退的流式调用：不带 tools，CompletableFuture 同步等待 10 分钟。
     * <p>file_write 长 HTML 主要在此路径生成（function calling 模式 LLM 通常 content 残缺会回退到此），
     * 必须在 onPartialResponse 中 emit CODE_STREAMING 事件，让前端流式展示代码 + 工作 loading 动画。</p>
     */
    private String callLlmStreamingForPlainText(List<ChatMessage> messages, TodoItem todo, DeepAgentState state) throws Exception {
        java.util.concurrent.CompletableFuture<ChatResponse> future = new java.util.concurrent.CompletableFuture<>();
        dev.langchain4j.model.chat.request.ChatRequest req = dev.langchain4j.model.chat.request.ChatRequest.builder()
                .messages(messages)
                // 注意：不带 .toolSpecifications(...)，绕开 function calling
                .maxOutputTokens(LLM_MAX_OUTPUT_TOKENS)
                .build();
        // 主线程捕获事件消费者（langchain4j 流式回调在 HTTP 线程，ThreadLocal 拿不到）
        final java.util.function.Consumer<AgentEvent> eventConsumer = SubAgentEventBus.get();
        final StringBuilder codeAccumulator = new StringBuilder();
        final long[] lastEmitTime = {0};
        final String streamSessionId = state.sessionId();
        final String streamTodoId = todo == null ? null : todo.getId();
        final String streamTodoDesc = todo == null ? null : todo.getDescription();

        streamingChatModel.chat(req, new dev.langchain4j.model.chat.response.StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                // 与 function-call 路径复用同一节流 emit helper，前端收到 CODE_STREAMING 后
                // 流式渲染代码气泡 + 工作区显示 generating 动画
                emitCodeStreaming(eventConsumer, codeAccumulator, lastEmitTime,
                        streamTodoId, streamTodoDesc, streamSessionId, partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                future.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                future.completeExceptionally(error);
            }
        });
        try {
            ChatResponse resp = future.get(10, java.util.concurrent.TimeUnit.MINUTES);
            return resp.aiMessage().text();
        } catch (Exception e) {
            future.cancel(true);
            log.error("ToolNode: 纯文本回退流式调用失败: {}", e.getMessage(), e);
            throw new com.hypersense.boot.common.exception.BusinessException(
                    "file_write 纯文本回退流式调用失败: " + e.getMessage());
        }
    }

    /**
     * 纯文本回退的同步调用：不带 tools。
     */
    private String callLlmSyncForPlainText(List<ChatMessage> messages) {
        ChatResponse resp = chatModel.chat(ChatRequest.builder()
                .messages(messages)
                .maxOutputTokens(LLM_MAX_OUTPUT_TOKENS)
                .build());
        return resp.aiMessage().text();
    }

    /**
     * 构造给工具选择 LLM 的用户 prompt。
     * <p>
     * 关键：把所有已完成 TODO 的 description + result 全部拼进来，
     * 让 LLM 看到前序步骤的真实产出（例如 ExecuteNode 已整理好的 Markdown），
     * 避免 file_write 写错内容（拿任务总结当 content）。
     * </p>
     */
    private String buildUserPrompt(TodoItem todo, DeepAgentState state) {
        StringBuilder sb = new StringBuilder();
        String todoDesc = todo.getDescription() == null ? "" : todo.getDescription();
        sb.append("当前任务: ").append(todoDesc).append("\n\n");

        String instructions = state.instructions() == null ? "" : state.instructions();
        sb.append("用户原始指令:\n").append(instructions).append("\n\n");

        // 拼接所有已完成（COMPLETED）TODO 的描述 + 完整结果 —— 这是 file_write 的关键上下文
        List<TodoItem> todos = state.todos();
        if (todos != null && !todos.isEmpty()) {
            StringBuilder prevBuf = new StringBuilder();
            int stepIdx = 0;
            String currentId = todo.getId();
            for (TodoItem t : todos) {
                if (t == null) continue;
                // 跳过当前 TODO 自身
                if (currentId != null && currentId.equals(t.getId())) continue;
                if (t.getStatus() != TodoStatus.COMPLETED) continue;
                String tDesc = t.getDescription() == null ? "" : t.getDescription();
                String tResult = t.getResult() == null ? "" : t.getResult();
                if (tResult.isBlank() && tDesc.isBlank()) continue;
                stepIdx++;
                prevBuf.append("[步骤 ").append(stepIdx).append("] ")
                        .append(tDesc).append("\n");
                prevBuf.append("结果:\n").append(tResult).append("\n\n");
            }
            if (prevBuf.length() > 0) {
                sb.append("前序步骤已完成的结果:\n").append(prevBuf).append("\n");
            } else {
                sb.append("前序步骤已完成的结果:（无）\n\n");
            }
        }

        // 已有文件列表：明确告知 LLM 工作空间的真实文件清单，防止幻觉
        Map<String, String> files = state.files();
        if (files != null && !files.isEmpty()) {
            sb.append("工作空间已有文件（仅可读取以下文件，禁止假设还有其他文件）:\n");
            for (String name : files.keySet()) {
                sb.append("- ").append(name).append("\n");
            }
            sb.append("\n");
        } else {
            sb.append("工作空间已有文件:（空，禁止假设工作空间中存在任何文件，禁止调用 file_read）\n\n");
        }

        sb.append("请基于以上上下文，选择合适的工具并填写参数。判断规则：\n")
          .append("- 如果当前 TODO 是「创建/设计/编写 HTML/代码/页面」类原创任务，")
          .append("必须调用 file_write，content 参数由你原创生成完整源码（禁止以'无前序产出'为由放弃）。\n")
          .append("- 如果当前 TODO 是「保存/落地」前序已产出的内容，")
          .append("content 参数必须直接取自前序步骤结果，不要总结、改写、截断或压缩。\n")
          .append("- 仅当 TODO 要求读取工作空间中不存在的文件时，才允许不调用工具。");
        return sb.toString();
    }

    /** file_write / file_write_chunk / file_render 成功时把 content 落盘到 files 通道，并发 FILE_CREATED 事件 */
    private void handleFileWriteSideEffect(ToolProvider tool, Object result, DeepAgentState state,
                                           TodoItem todo, Map<String, String> files) {
        // #12 修复：file_write_chunk 与 file_write 同走文件副作用处理
        // file_render：PPT 渲染主路径，产物为 slide_<n>.html + index.html，逐文件发 FILE_CREATED
        String toolName = tool.name();
        boolean isFileWrite = "file_write".equals(toolName) || "file_write_chunk".equals(toolName);
        boolean isFileRender = "file_render".equals(toolName);
        if ((!isFileWrite && !isFileRender) || !(result instanceof Map)) {
            return;
        }
        if (isFileRender) {
            handleFileRenderSideEffect(tool, (Map<String, Object>) result, state);
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap = (Map<String, Object>) result;
        // 失败：透传错误信息到前端，让用户看到失败原因
        if (!Boolean.TRUE.equals(resultMap.get("success"))) {
            String errMsg = String.valueOf(resultMap.getOrDefault("error",
                    resultMap.getOrDefault("message", "工具调用失败")));
            try {
                Map<String, Object> payload = new HashMap<>();
                payload.put("tool", tool.name());
                payload.put("error", errMsg);
                payload.put("filename", resultMap.get("filename"));
                payload.put("sessionId", state.sessionId());
                emit(AgentEventType.TOOL_ERROR, "工具调用失败: " + errMsg, payload);
            } catch (Exception ignored) {
                // SSE 发送失败不应影响工具调用主流程
            }
            return;
        }
        String filename = (String) resultMap.get("filename");
        if (filename == null) {
            return;
        }
        String content = pickStringFrom(resultMap, "content", "contents");
        if (content == null || content.isBlank()) {
            content = state.instructions();
        }
        if (content == null || content.isBlank()) {
            content = todo.getDescription();
        }
        files.put(filename, content);

        // 成功：发送 FILE_CREATED 事件，通知前端刷新工作空间附件列表
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("filename", filename);
            // 同时下发绝对路径（path，后端调试用）与相对路径（relativePath/workspacePath，前端展示用）
            payload.put("path", resultMap.get("path"));
            payload.put("relativePath", resultMap.get("relativePath"));
            payload.put("workspacePath", resultMap.get("workspacePath"));
            payload.put("sessionId", state.sessionId());
            emit(AgentEventType.FILE_CREATED, "文件已创建: " + filename, payload);
        } catch (Exception ignored) {
            // SSE 发送失败不应影响工具调用主流程
        }
    }

    /**
     * file_render 产物副作用：逐文件发 FILE_CREATED 事件（方案 A）。
     * <p>file_render 一次产 N 个 slide_<n>.html + 1 个 index.html。每个文件单独发事件，
     * 前端可按 filename 自然去重/追加；前端已做存在性判断，无新增字段也能兼容。</p>
     * <p>路径契约与 FileWriteTool 对齐：relativePath={sessionId}/{filename}（file_render 无 uploads 子目录），
     * workspacePath=workspace/{relativePath}。outputDir 透传到 path 字段供后端调试。</p>
     */
    private void handleFileRenderSideEffect(ToolProvider tool, Map<String, Object> resultMap,
                                            DeepAgentState state) {
        if (!Boolean.TRUE.equals(resultMap.get("success"))) {
            return;
        }
        Object filesObj = resultMap.get("files");
        if (!(filesObj instanceof List<?> list) || list.isEmpty()) {
            return;
        }
        String sessionId = state.sessionId();
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = String.valueOf(resultMap.getOrDefault("sessionId", "default"));
        }
        Object outputDir = resultMap.get("outputDir");
        for (Object fn : list) {
            if (!(fn instanceof String filename) || filename.isBlank()) continue;
            String relativePath = sessionId + "/" + filename;
            String workspacePath = "workspace/" + relativePath;
            try {
                Map<String, Object> payload = new HashMap<>();
                payload.put("filename", filename);
                payload.put("path", outputDir);
                payload.put("relativePath", relativePath);
                payload.put("workspacePath", workspacePath);
                payload.put("sessionId", sessionId);
                payload.put("sourceTool", "file_render");
                emit(AgentEventType.FILE_CREATED, "文件已创建: " + filename, payload);
            } catch (Exception ignored) {
                // SSE 失败不影响主流程
            }
        }
    }

    /** 构造 TOOL_CALL 事件的 data，新增 toolName/arguments 字段 */
    private Map<String, Object> buildCallData(TodoItem todo, List<ToolProvider> candidates,
                                              ToolProvider chosen, Map<String, Object> args,
                                              boolean matched, String reason) {
        Map<String, Object> data = new HashMap<>();
        data.put("todo", todo);
        // 候选工具列表：tools（旧字段，向后兼容）+ candidates（前端 ProcessPanel 新契约）
        List<String> candidateNames = candidates.stream().map(ToolProvider::name).toList();
        data.put("tools", candidateNames);
        data.put("candidates", candidateNames);
        data.put("matched", matched);
        if (chosen != null) {
            // 选中工具：toolName（旧字段）+ selected（前端新契约）
            data.put("toolName", chosen.name());
            data.put("selected", chosen.name());
            // 参数：arguments（旧字段）+ args（前端新契约）
            data.put("arguments", args);
            data.put("args", args);
            // file_write / file_write_chunk / file_render 时透传 fileName 给前端切设计模式（前端 useAgentSSE 匹配此字段）
            String cn = chosen.name();
            if (("file_write".equals(cn) || "file_write_chunk".equals(cn) || "file_render".equals(cn))
                    && args.get("filename") instanceof String fn && !fn.isBlank()) {
                data.put("fileName", fn);
            }
        }
        // reason：LLM function calling 模式通常不返回 reasoning，此处为 null 时省略（前端隐藏"选中原因"块）
        if (reason != null) {
            data.put("reason", reason);
        }
        return data;
    }

    /**
     * Plan C P0#1：工具执行后推进 TDD 状态机（code-profile 专用）。
     * <p>逻辑与 {@link ExecuteNode#onToolExecuted} 一致：file_write 推 READ→TEST→TEST_HITL→IMPL→EXEC
     * 并抽取源码 import 注册到 SymbolRegistry；sandbox_exec 推 EXEC→LINT。非 code-profile / 未注入
     * 管理器时静默跳过。</p>
     */
    private void advanceTddPhase(DeepAgentState state, String toolName, Object result) {
        if (state == null || toolName == null) return;
        if (tddPhaseManager == null || symbolRegistry == null) return;
        String activeProfileId = state.<String>value(DeepAgentState.ACTIVE_PROFILE).orElse(null);
        if (!"code".equals(activeProfileId)) return;
        String sessionId = state.<String>value(DeepAgentState.SESSION_ID).orElse("__default__");

        try {
            com.hypersense.boot.framework.agents.profile.impl.TddPhase cur =
                    tddPhaseManager.current(sessionId);
            if ("file_write".equals(toolName)) {
                switch (cur) {
                    case READ -> tddPhaseManager.transition(sessionId,
                            com.hypersense.boot.framework.agents.profile.impl.TddPhase.TEST);
                    case TEST -> tddPhaseManager.transition(sessionId,
                            com.hypersense.boot.framework.agents.profile.impl.TddPhase.TEST_HITL);
                    case IMPL -> tddPhaseManager.transition(sessionId,
                            com.hypersense.boot.framework.agents.profile.impl.TddPhase.EXEC);
                    default -> { /* 其他阶段不因 file_write 推进 */ }
                }
                Map<String, Object> resultMap = (result instanceof Map<?, ?>) ? castToObjMap(result) : null;
                registerImportsFromCode(extractFileContent(resultMap), sessionId);
            } else if ("sandbox_exec".equals(toolName)) {
                if (cur == com.hypersense.boot.framework.agents.profile.impl.TddPhase.EXEC) {
                    tddPhaseManager.transition(sessionId,
                            com.hypersense.boot.framework.agents.profile.impl.TddPhase.LINT);
                }
            }
        } catch (Exception e) {
            log.warn("ToolNode.advanceTddPhase: 推进 TDD 阶段失败 tool={}, session={}, err={}",
                    toolName, sessionId, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castToObjMap(Object o) {
        return (Map<String, Object>) o;
    }

    /** 从工具结果 Map 中提取写盘内容（FileWriteTool 的产物）。无则返回 null。 */
    private String extractFileContent(Map<String, Object> toolResult) {
        if (toolResult == null) return null;
        Object content = toolResult.get("content");
        if (content == null) content = toolResult.get("text");
        return content == null ? null : content.toString();
    }

    /**
     * 抽取源码 import 并注册到 SymbolRegistry（防 LLM 用 package_lookup 之外的 API）。
     * <p>规则与 {@link ExecuteNode} 同名私有方法保持一致，确保两条执行路径（LLM 决策 / 旧遍历）
     * 都能覆盖。</p>
     */
    private void registerImportsFromCode(String code, String sessionId) {
        if (code == null || code.isBlank()) return;
        // Python
        java.util.regex.Pattern pyImport = java.util.regex.Pattern.compile(
                "(?m)^\\s*(?:from\\s+([\\w.]+)\\s+import\\s+(\\w+)|import\\s+([\\w.]+))\\s*$");
        java.util.regex.Matcher m = pyImport.matcher(code);
        while (m.find()) {
            if (m.group(2) != null && !m.group(2).isEmpty()) {
                symbolRegistry.register(sessionId, m.group(2));
            }
            if (m.group(3) != null && !m.group(3).isEmpty()) {
                String[] parts = m.group(3).split("\\.");
                if (parts.length > 1) {
                    symbolRegistry.register(sessionId, parts[parts.length - 1]);
                }
            }
        }
        // JS
        java.util.regex.Pattern jsImport = java.util.regex.Pattern.compile(
                "import\\s+[^;]*?\\{([^}]+)\\}\\s*from\\s+['\"][^'\"]+['\"]");
        java.util.regex.Matcher jm = jsImport.matcher(code);
        while (jm.find()) {
            for (String s : jm.group(1).split(",")) {
                String sym = s.trim().split("\\s+as\\s+")[0].trim();
                if (!sym.isEmpty()) symbolRegistry.register(sessionId, sym);
            }
        }
    }

    /**
     * Plan C P0#2：file_write/file_render/sandbox_exec 后扫描 profile.lintRules()，
     * 命中违规时累加 session 级计数器并发 LINT_VIOLATION 事件。
     *
     * <p>违规累计超 {@code profile.hitlPolicy().maxLintRetriesBeforeInterrupt()} 时
     * 返回中断理由字符串（用于在 {@link #apply} 末尾写 state.INTERRUPT_REASON），
     * 同时发 INTERRUPT 事件。code-profile 还会调 {@code tddPhaseManager.failLint}。</p>
     *
     * <p>非 profile / 无 lintRules / 工具不在扫描范围 → 静默返回 null。
     * 任何异常吞掉 + log.warn，绝不阻塞主流程。</p>
     *
     * @return 中断理由（用于 NEED_CONFIRMATION）；未触发中断时返回 null
     */
    private String runProfileLint(DeepAgentState state, String toolName, Object result, TodoItem todo) {
        if (state == null || toolName == null) return null;
        // 仅对产物型工具扫描 lint：file_write/file_render（HTML/源码）、sandbox_exec（已携带 compile/test 语义）
        if (!"file_write".equals(toolName)
                && !"file_render".equals(toolName)
                && !"sandbox_exec".equals(toolName)) {
            return null;
        }
        if (profileRegistry == null) return null;
        String activeProfileId = state.<String>value(DeepAgentState.ACTIVE_PROFILE).orElse(null);
        if (activeProfileId == null || activeProfileId.isBlank()) return null;

        com.hypersense.boot.framework.agents.profile.CapabilityProfile profile;
        try {
            String sid = state.<String>value(DeepAgentState.SESSION_ID).orElse(null);
            java.util.Map<String, Object> hints = state.<java.util.Map<String, Object>>value(DeepAgentState.PROFILE_HINTS).orElse(java.util.Map.of());
            profile = profileRegistry.get(activeProfileId, sid, hints);
        } catch (Exception e) {
            log.warn("ToolNode.runProfileLint: 加载 profile [{}] 失败: {}", activeProfileId, e.getMessage());
            return null;
        }
        if (profile == null) return null;
        java.util.List<com.hypersense.boot.framework.agents.profile.LintRule> rules = profile.lintRules();
        if (rules == null || rules.isEmpty()) return null;

        String content = extractLintTarget(toolName, result);
        if (content == null || content.isBlank()) {
            log.debug("ToolNode.runProfileLint: tool={} 无可扫内容，跳过 lint", toolName);
            return null;
        }

        String sessionId = state.<String>value(DeepAgentState.SESSION_ID).orElse("__default__");
        com.hypersense.boot.framework.agents.profile.HitlPolicy policy = profile.hitlPolicy();
        int maxRetries = policy == null ? Integer.MAX_VALUE : policy.maxLintRetriesBeforeInterrupt();
        if (maxRetries <= 0) maxRetries = Integer.MAX_VALUE;  // 0 / 负值表示不触发 HITL

        boolean anyViolated = false;
        for (com.hypersense.boot.framework.agents.profile.LintRule rule : rules) {
            String violation;
            try {
                violation = rule.check(content);
            } catch (Exception e) {
                log.warn("ToolNode.runProfileLint: rule={} 检查异常: {}", rule.id(), e.getMessage());
                continue;
            }
            if (violation == null || violation.isBlank()) continue;
            anyViolated = true;
            int attempt = lintStatsManager == null ? 0 : lintStatsManager.increment(sessionId, rule.id());
            boolean willInterrupt = attempt >= maxRetries;
            emitLintViolation(rule, violation, content, toolName, sessionId, attempt, willInterrupt, todo);
            log.info("ToolNode.runProfileLint: 命中 rule={} tool={} attempt={}/{} willInterrupt={}",
                    rule.id(), toolName, attempt, maxRetries == Integer.MAX_VALUE ? "∞" : maxRetries, willInterrupt);
        }

        if (!anyViolated) return null;

        // code-profile：把 lint 失败推进 TDD 状态机（IMPL/LINT → 回 IMPL 等待 retry / 或 HITL）
        if (tddPhaseManager != null && "code".equals(activeProfileId)) {
            try {
                tddPhaseManager.failLint(sessionId);
            } catch (Exception e) {
                log.warn("ToolNode.runProfileLint: tddPhaseManager.failLint 失败: {}", e.getMessage());
            }
        }

        int totalViolations = lintStatsManager == null ? 0 : lintStatsManager.total(sessionId);
        if (totalViolations >= maxRetries) {
            String reason = String.format(
                    "Lint 违规累计 %d 次 ≥ 阈值 %d，触发 HITL 等待审批。profile=%s",
                    totalViolations, maxRetries, activeProfileId);
            try {
                Map<String, Object> payload = new HashMap<>();
                payload.put("sessionId", sessionId);
                payload.put("todoId", todo == null ? null : todo.getId());
                payload.put("profile", activeProfileId);
                payload.put("tool", toolName);
                payload.put("totalViolations", totalViolations);
                payload.put("maxRetries", maxRetries);
                payload.put("phase", "lint_failed");
                emit(AgentEventType.INTERRUPT, reason, payload);
            } catch (Exception ignored) {}
            return reason;
        }
        return null;
    }

    /**
     * 从工具结果中提取 lint 扫描目标文本。
     * <p>file_write/file_render → content 字段；sandbox_exec → stdout（用于反幻觉 API / 注释语言 lint）。</p>
     */
    private String extractLintTarget(String toolName, Object result) {
        if (!(result instanceof Map<?, ?>)) return null;
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) result;
        Object content = m.get("content");
        if (content == null) content = m.get("text");
        if (content == null) content = m.get("stdout");
        if (content == null) content = m.get("html");
        return content == null ? null : content.toString();
    }

    /** 发送 LINT_VIOLATION 事件，附带规则元信息与命中片段（截断 200 字防止 SSE 膨胀）。 */
    private void emitLintViolation(com.hypersense.boot.framework.agents.profile.LintRule rule,
                                   String message, String content, String toolName,
                                   String sessionId, int attempt, boolean willInterrupt,
                                   TodoItem todo) {
        try {
            String snippet = content == null ? "" : content.substring(0, Math.min(200, content.length()));
            Map<String, Object> payload = new HashMap<>();
            payload.put("ruleId", rule.id());
            payload.put("description", rule.description());
            payload.put("message", message);
            payload.put("snippet", snippet);
            payload.put("sessionId", sessionId);
            payload.put("toolName", toolName);
            payload.put("attempt", attempt);
            payload.put("willInterrupt", willInterrupt);
            if (todo != null) payload.put("todoId", todo.getId());
            emit(AgentEventType.LINT_VIOLATION,
                    "Lint 违规 [" + rule.id() + "]: " + message, payload);
        } catch (Exception ignored) {
            // SSE 失败不影响主流程
        }
    }

    /** LLM 决策结果载体 */
    private static class LlmDecisionOutcome {
        ToolProvider chosen;
        Map<String, Object> args = new HashMap<>();
        String failReason;
        /**
         * 工具执行结果覆盖：当回退路径已直接执行工具（如纯文本回退执行了 FileWriteTool）时，
         * 外层 apply 直接使用该结果，不再调用 executeWithRetry 重复执行。null 表示走标准 execute 流程。
         */
        Map<String, Object> toolResultOverride;
    }

    /**
     * 通过 SubAgentEventBus 推送事件
     */
    private void emit(AgentEventType type, String message, Map<String, Object> data) {
        var consumer = SubAgentEventBus.get();
        if (consumer == null) return;
        AgentEvent event = AgentEvent.builder()
                .type(type)
                .message(message)
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build();
        consumer.accept(event);
    }

    /**
     * 流式代码生成 SSE 推送（节流 200ms）。
     * <p>function-call 路径与纯文本回退路径共用此 helper，
     * 确保 file_write 长 HTML 生成期间前端能持续看到代码增量 + 工作 loading 动画。</p>
     *
     * @param consumer        主线程捕获的事件消费者（闭包传递，跨线程安全）
     * @param accumulator     累积全文 buffer（多次调用追加）
     * @param lastEmitTimeHolder 节流时间戳 holder（long[1]）
     * @param todoId          当前 TODO id
     * @param todoDesc        当前 TODO 描述
     * @param sessionId       会话 id
     * @param partialResponse 本次增量文本
     */
    private void emitCodeStreaming(
            java.util.function.Consumer<AgentEvent> consumer,
            StringBuilder accumulator,
            long[] lastEmitTimeHolder,
            String todoId, String todoDesc, String sessionId,
            String partialResponse) {
        if (partialResponse == null || partialResponse.isEmpty() || consumer == null) return;
        accumulator.append(partialResponse);
        long now = System.currentTimeMillis();
        // 节流：距上次 emit ≥ 200ms 才发，避免 token 风暴压垮 SSE
        if (now - lastEmitTimeHolder[0] < 200) return;
        lastEmitTimeHolder[0] = now;
        try {
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("todoId", todoId);
            data.put("todoDescription", todoDesc);
            if (sessionId != null) data.put("sessionId", sessionId);
            data.put("delta", partialResponse);
            data.put("accumulated", accumulator.toString());
            AgentEvent event = AgentEvent.builder()
                    .type(AgentEventType.CODE_STREAMING)
                    .message("生成代码中...")
                    .data(data)
                    .timestamp(now)
                    .build();
            consumer.accept(event);
        } catch (Throwable t) {
            // 流式回调异常绝不能影响主流程，仅记录
            log.warn("ToolNode: CODE_STREAMING emit 失败（忽略）: {}", t.getMessage());
        }
    }

    /**
     * Plan #1：function calling 路径下 file_write / file_write_chunk 补发一次 CODE_STREAMING。
     * <p>背景：function calling 模式 LLM 把内容塞 tool args，{@code onPartialResponse} 通常不回调，
     * 前端在「LLM 决策完成 → 工具执行 → TOOL_CALL 事件」之间收不到任何代码增量。</p>
     * <p>策略：若 accumulator 为空（即纯文本回退路径未发过），把 content / chunk 字段整体
     * 作为 accumulated 推送一次（前端 MessageMarkdown 覆盖式渲染，单次推送无闪烁）。
     * accumulator 非空时跳过（去重，避免与 onPartialResponse 重复）。</p>
     *
     * @param consumer   主线程事件消费者
     * @param accumulator 流式累积 buffer（用于判断是否已被纯文本路径发过）
     * @param todoId     TODO id
     * @param todoDesc   TODO 描述
     * @param sessionId  会话 id
     * @param toolName   选中工具名（file_write / file_write_chunk）
     * @param args       工具参数（含 content 或 chunk 字段）
     */
    private void emitCodeStreamingForFileWriteOnce(
            java.util.function.Consumer<AgentEvent> consumer,
            StringBuilder accumulator,
            String todoId, String todoDesc, String sessionId,
            String toolName, java.util.Map<String, Object> args) {
        if (consumer == null || args == null) return;
        if (!"file_write".equals(toolName) && !"file_write_chunk".equals(toolName)) return;
        // 纯文本回退路径已 emit 过，跳过
        if (accumulator.length() > 0) return;
        Object contentObj = "file_write_chunk".equals(toolName)
                ? args.get("chunk")
                : args.get("content");
        if (contentObj == null) {
            // file_write_chunk 可能也用 content 字段（pickString 兜底）
            if ("file_write_chunk".equals(toolName)) contentObj = args.get("content");
        }
        String content = contentObj == null ? null : contentObj.toString();
        if (content == null || content.isEmpty()) return;
        // 把内容并入 accumulator，标记已 emit（后续若还有路径不会再发）
        accumulator.append(content);
        try {
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("todoId", todoId);
            data.put("todoDescription", todoDesc);
            if (sessionId != null) data.put("sessionId", sessionId);
            // 单次推送：delta 与 accumulated 一致（前端按 accumulated 覆盖渲染）
            data.put("delta", content);
            data.put("accumulated", content);
            AgentEvent event = AgentEvent.builder()
                    .type(AgentEventType.CODE_STREAMING)
                    .message("生成代码中...")
                    .data(data)
                    .timestamp(System.currentTimeMillis())
                    .build();
            consumer.accept(event);
        } catch (Throwable t) {
            log.warn("ToolNode: file_write CODE_STREAMING 补发失败（忽略）: {}", t.getMessage());
        }
    }

    /**
     * 从工具返回结果中提取可读摘要。
     */
    @SuppressWarnings("unchecked")
    private String extractReadableResult(Map<String, Object> toolResults) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : toolResults.entrySet()) {
            String toolName = entry.getKey();
            Object result = entry.getValue();
            if (result instanceof String s && (s.startsWith("执行失败:") || s.startsWith("工具匹配失败"))) {
                sb.append("[").append(toolName).append("] ").append(s).append("\n");
                continue;
            }
            if (result instanceof Map<?, ?> resultMap) {
                // reply_text 工具：直接提取 content 作为可读结果，避免 debug 包装污染最终回复。
                // reply_text 是「废除 direct 后所有回复的承接方」，其 content 就是面向用户的最终文本。
                if ("reply_text".equals(toolName) && Boolean.TRUE.equals(resultMap.get("replied"))) {
                    Object contentObj = resultMap.get("content");
                    if (contentObj != null && !contentObj.toString().isBlank()) {
                        sb.append(contentObj);
                        continue;
                    }
                }
                Object resultsObj = resultMap.get("results");
                Object queryObj = resultMap.get("query");
                if (resultsObj instanceof List<?> list && !list.isEmpty()) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append("搜索关键词: ").append(queryObj != null ? queryObj : "N/A").append("\n");
                    int idx = 1;
                    for (Object item : list) {
                        if (!(item instanceof Map<?, ?>)) continue;
                        Map<?, ?> m = (Map<?, ?>) item;
                        String title = stringOrEmpty(m.get("title"));
                        String content = stringOrEmpty(m.get("content"));
                        String url = stringOrEmpty(m.get("url"));
                        sb.append(idx++).append(". ").append(title);
                        if (!content.isBlank()) sb.append(" — ").append(content);
                        if (!url.isBlank()) sb.append("（").append(url).append("）");
                        sb.append("\n");
                        if (idx > 5) break;
                    }
                    continue;
                }
                Object msg = resultMap.get("message");
                sb.append("[").append(toolName).append("] ")
                        .append(msg != null ? msg : resultMap).append("\n");
                // 提取结构化相对路径，供下游节点（PlanNode / FinalizeNode）的 LLM 准确引用，
                // 避免基于训练数据编造 /home/user/ 等路径
                Object workspacePath = resultMap.get("workspacePath");
                Object relativePath = resultMap.get("relativePath");
                Object filename = resultMap.get("filename");
                if (workspacePath != null) {
                    sb.append("   工作空间路径: ").append(workspacePath).append("\n");
                } else if (relativePath != null) {
                    sb.append("   工作空间路径: workspace/").append(relativePath).append("\n");
                } else if (filename != null) {
                    // 工具未返回路径字段时的兜底：用 filename 提示，不拼具体路径以免编造 sessionId
                    sb.append("   工作空间路径: workspace/<sessionId>/uploads/").append(filename).append("\n");
                }
                continue;
            }
            sb.append("[").append(toolName).append("] ").append(result).append("\n");
        }
        return sb.toString().trim();
    }

    private String stringOrEmpty(Object v) {
        return v != null ? String.valueOf(v) : "";
    }

    private String pickStringFrom(Map<String, Object> map, String... keys) {
        if (map == null || keys == null) return null;
        for (String key : keys) {
            Object v = map.get(key);
            if (v != null && !v.toString().isBlank()) {
                return v.toString();
            }
        }
        return null;
    }

    /**
     * 根据 TODO 描述判断是否应该调用该工具，避免无参数工具被无脑遍历触发。
     */
    private boolean shouldInvoke(ToolProvider tool, String todoDescription) {
        if (todoDescription == null || todoDescription.isBlank()) return true;
        String desc = todoDescription.toLowerCase();
        String name = tool.name();
        switch (name) {
            case "reply_text":
                // 纯文本回复场景：问候/知识问答/解释/总结/澄清。
                // 关键词命中即视为候选；ExecuteNode 废除 direct 后，所有非工具/非委派 TODO 都应走 reply_text。
                // PlanNode DIRECT_REPLY 短路会注入「回复用户」类 TODO，确保此处能命中。
                if (desc.contains("reply_text") || desc.contains("回复用户")
                        || desc.contains("回答用户") || desc.contains("向用户回复")
                        || desc.contains("问候") || desc.contains("打招呼")
                        || desc.contains("解释") || desc.contains("澄清")
                        || desc.contains("问候语") || desc.contains("答复")) {
                    return true;
                }
                // 组合匹配：「回复/回答」+「用户」
                if ((desc.contains("回复") || desc.contains("回答")) && desc.contains("用户")) {
                    return true;
                }
                return false;
            case "internet_search":
                return desc.matches(".*\\b(search the (web|internet)|web search|google)\\b.*")
                        || desc.contains("网络搜索") || desc.contains("互联网搜索")
                        || desc.contains("搜索引擎") || desc.contains("最新新闻")
                        || desc.contains("实时数据") || desc.contains("实时信息")
                        || desc.contains("天气") || desc.contains("股价") || desc.contains("汇率")
                        || (desc.contains("搜索") && !desc.contains("搜索文件") && !desc.contains("文件搜索"));
            case "read_file":
                // 文件读取/查看/了解/分析类任务：read_file 优先匹配
                if (desc.contains("读取") || desc.contains("读取文件") || desc.contains("读文件")
                        || desc.contains("读取附件") || desc.contains("打开附件")
                        || desc.contains("查看") || desc.contains("了解")
                        || desc.contains("分析") || desc.contains("查看文件")
                        || desc.contains("了解文件") || desc.contains("预览")
                        || desc.contains("read_file") || desc.contains("附件")
                        || desc.contains("uploads") || desc.contains("文档")) {
                    return true;
                }
                return desc.matches(".*\\.(md|markdown|txt|csv|json|py|js|ts|html?|css|java|c|cpp|go|rs|xml|ya?ml|log|sql|sh|png|jpe?g|gif|webp|bmp|svg|pdf|docx?|xlsx?)\\b.*");
            case "sandbox":
            case "file_write":
                if (desc.contains("读取") || desc.contains("读取文件") || desc.contains("读文件")
                        || desc.contains("读取附件") || desc.contains("打开附件")
                        || desc.contains("列出目录") || desc.contains("查看目录")
                        || desc.contains("搜索文件") || desc.contains("文件搜索")
                        || desc.contains("执行代码") || desc.contains("运行代码")
                        || desc.contains("run_command") || desc.contains("execute_code")
                        || desc.contains("附件") || desc.contains("uploads")
                        || desc.contains("sandbox") || desc.contains("沙箱")) {
                    return true;
                }
                if (desc.contains("写入") || desc.contains("写文件") || desc.contains("保存")
                        || desc.contains("创建文件") || desc.contains("新建文件")
                        || desc.contains("编辑文件") || desc.contains("修改文件")
                        || desc.contains("生成") || desc.contains("导出") || desc.contains("输出")
                        || desc.contains("转换为") || desc.contains("整理为") || desc.contains("转存")
                        || desc.contains("另存为") || desc.contains("落盘") || desc.contains("归档")
                        || desc.contains("read_file") || desc.contains("write_file")) {
                    return true;
                }
                if (desc.contains("save") || desc.contains("write") || desc.contains("create")
                        || desc.contains("export") || desc.contains("generate")
                        || desc.contains("output file") || desc.contains("save as")
                        || desc.contains("save to")) {
                    return true;
                }
                if (desc.matches(".*\\.(md|markdown|txt|csv|json|py|js|ts|html|htm|css|java|c|cpp|go|rs|docx|doc|xlsx|xls|pdf|xml|yaml|yml|log|sql|sh|bat)\\b.*")) {
                    return true;
                }
                return desc.contains("文件") || desc.contains("产物") || desc.contains("输出文件");
            case "file_write_chunk":
                // 分块写大文件（design/profile 自由 HTML 落盘的默认路径）
                // 显式提到 file_write_chunk 命中；复用 file_write 的「保存/写文件/落盘/导出」语义
                if (desc.contains("file_write_chunk") || desc.contains("file-write-chunk")
                        || desc.contains("分块") || desc.contains("chunk")) {
                    return true;
                }
                return desc.contains("写入") || desc.contains("写文件") || desc.contains("保存")
                        || desc.contains("创建文件") || desc.contains("新建文件")
                        || desc.contains("生成") || desc.contains("导出") || desc.contains("输出")
                        || desc.contains("另存为") || desc.contains("落盘") || desc.contains("归档")
                        || desc.contains("save") || desc.contains("write") || desc.contains("export")
                        || desc.matches(".*\\.(md|markdown|txt|csv|json|py|js|ts|html|htm|css|java|c|cpp|go|rs|docx|doc|xlsx|xls|pdf|xml|yaml|yml|log|sql|sh|bat)\\b.*");
            case "file_render":
                // PPT / 幻灯片模板渲染（design-profile slides JSON → slide_N.html）
                return desc.contains("file_render") || desc.contains("渲染")
                        || desc.contains("slides") || desc.contains("deck")
                        || desc.contains("ppt") || desc.contains("幻灯片")
                        || desc.contains("演示文稿") || desc.contains("keynote");
            case "design_asset_fetch":
                // 抓官方 logo / 真图（svgl/simpleicons/wikimedia）
                return desc.contains("design_asset_fetch") || desc.contains("抓取")
                        || desc.contains("取 logo") || desc.contains("取资产")
                        || desc.contains("品牌资产") || desc.contains("官方 logo")
                        || desc.contains("取真图") || desc.contains("wikimedia")
                        || desc.contains("unsplash") || desc.contains("simpleicons");
            case "design_direction_explore":
                // 3 份 outline 探索方向（roulette / reference / designer 三套互补逻辑）
                return desc.contains("design_direction_explore")
                        || desc.contains("outline") || desc.contains("探索方向")
                        || desc.contains("风格方向") || desc.contains("方向探索")
                        || desc.contains("3 份") || desc.contains("三份")
                        || desc.contains("variation") || desc.contains("变体");
            default:
                return false;
        }
    }

    /**
     * 判断 TODO 是否显式声明走 reply_text（短路条件）。
     * <p>仅当 PlanNode 已契约化生成「使用 reply_text 工具...」类 TODO 时才短路，
     * 避免对其他可能命中 reply_text 关键词的回复类 TODO 误触发（那些仍可走 LLM 决策）。</p>
     */
    private boolean isReplyTextTodo(String todoDescription) {
        if (todoDescription == null) return false;
        String desc = todoDescription.toLowerCase();
        return desc.contains("reply_text");
    }

    /**
     * 判断是否为「design profile + HTML 类 TODO」需主动短路纯文本 artifact 模式。
     * <p>命中条件（全部满足）：</p>
     * <ol>
     *   <li>active profile == "design"</li>
     *   <li>候选工具含 file_write_chunk</li>
     *   <li>TODO 描述含 HTML 关键词（不区分大小写）：
     *       <code>.html</code> / <code>html</code> / <code>landing</code> /
     *       <code>主页</code> / <code>首页</code> / <code>页面</code> /
     *       <code>信息图</code> / <code>infographic</code></li>
     * </ol>
     * <p>命中后 apply 会跳过 function calling 决策直接走纯文本流式输出，失败自动降级。</p>
     */
    private boolean isDesignHtmlTodo(DeepAgentState state, TodoItem todo, List<ToolProvider> candidates) {
        if (state == null || todo == null || candidates == null || candidates.isEmpty()) {
            return false;
        }
        String profileId = state.<String>value(DeepAgentState.ACTIVE_PROFILE).orElse("");
        if (!"design".equals(profileId)) {
            return false;
        }
        boolean hasFileWriteChunk = candidates.stream().anyMatch(t -> "file_write_chunk".equals(t.name()));
        if (!hasFileWriteChunk) {
            return false;
        }
        String desc = todo.getDescription();
        if (desc == null || desc.isBlank()) {
            return false;
        }
        String lower = desc.toLowerCase();
        return lower.contains(".html")
                || lower.contains("html")
                || lower.contains("landing")
                || lower.contains("infographic")
                || desc.contains("主页")
                || desc.contains("首页")
                || desc.contains("页面")
                || desc.contains("信息图");
    }

    /**
     * reply_text 纯文本直出：绕开 function-calling 决策，让 LLM 以普通 chat 直出回复文本。
     * <p>背景：function-calling 模式下 LLM 把内容塞 tool args，onPartialResponse 不回调，
     * HTTP 主线程在 future.get(10, MINUTES) 同步阻塞，SSE 缓冲区无法 flush，前端"卡死直到服务关闭"。
     * reply_text 是文本回显通道（无"工具选择"语义），用普通 chat 几秒返回短文本即可。</p>
     * <p>失败时返回 null，外层降级到原有 LLM 决策路径（保证兼容性）。</p>
     */
    private LlmDecisionOutcome tryPlainTextForReplyText(List<ToolProvider> candidates,
                                                         TodoItem todo, DeepAgentState state) {
        Optional<ToolProvider> replyOpt = candidates.stream()
                .filter(t -> "reply_text".equals(t.name()))
                .findFirst();
        if (replyOpt.isEmpty()) {
            return null;
        }
        ToolProvider replyTool = replyOpt.get();

        // 构造普通 chat prompt（不带 toolSpecifications）
        String systemPrompt = "你是用户回复助手。根据当前 TODO、用户原始需求和前序步骤产出，"
                + "生成一段面向用户的纯文本回复（可含 Markdown）。\n"
                + "硬性规则：\n"
                + "1. 直接输出回复正文，禁止任何前言、解释、思考链\n"
                + "2. 禁止编造文件路径、工具调用记录等操作痕迹\n"
                + "3. 回复内容必须与 TODO 主题一致（如 TODO 是总结，则输出总结；TODO 是问候，则输出问候）\n"
                + "4. 长度控制：问候/澄清 ≤ 200 字；解释/总结 ≤ 800 字";
        StringBuilder userPromptBuf = new StringBuilder();
        userPromptBuf.append("当前任务: ").append(todo.getDescription() == null ? "" : todo.getDescription()).append("\n\n");
        String instructions = state.instructions() == null ? "" : state.instructions();
        userPromptBuf.append("用户原始需求:\n").append(instructions).append("\n\n");
        // 前序 TODO 产出（让总结类回复有内容可依）
        List<TodoItem> todos = state.todos();
        if (todos != null && !todos.isEmpty()) {
            int stepIdx = 0;
            String currentId = todo.getId();
            for (TodoItem t : todos) {
                if (t == null || t.getId() == null) continue;
                if (t.getId().equals(currentId)) continue;
                if (t.getStatus() != TodoStatus.COMPLETED) continue;
                String tDesc = t.getDescription() == null ? "" : t.getDescription();
                String tResult = t.getResult() == null ? "" : t.getResult();
                if (tResult.isBlank() && tDesc.isBlank()) continue;
                stepIdx++;
                userPromptBuf.append("[步骤 ").append(stepIdx).append("] ").append(tDesc).append("\n");
                if (!tResult.isBlank()) {
                    String snippet = tResult.length() > 500 ? tResult.substring(0, 500) + "..." : tResult;
                    userPromptBuf.append("产出摘要: ").append(snippet).append("\n\n");
                } else {
                    userPromptBuf.append("\n");
                }
            }
        }
        userPromptBuf.append("\n请直接输出面向用户的回复正文：");

        List<ChatMessage> messages = List.of(
                SystemMessage.from(systemPrompt),
                UserMessage.from(userPromptBuf.toString())
        );

        String rawText = null;
        try {
            if (streamingChatModel != null) {
                rawText = callLlmStreamingForReplyText(messages, todo, state);
            } else {
                rawText = callLlmSyncForPlainText(messages);
            }
        } catch (Exception e) {
            log.warn("ToolNode: reply_text 纯文本直出 LLM 调用失败，降级到 function-calling 决策: {}", e.getMessage());
            return null;
        }
        if (rawText == null || rawText.isBlank()) {
            log.warn("ToolNode: reply_text 纯文本直出 LLM 返回空，降级到 function-calling 决策");
            return null;
        }

        Map<String, Object> args = new HashMap<>();
        args.put("content", rawText);
        // 推断 replyType（粗粒度，ReplyTextTool 自带兜底为 EXPLANATION）
        String lowerDesc = (todo.getDescription() == null ? "" : todo.getDescription().toLowerCase());
        if (lowerDesc.contains("总结") || lowerDesc.contains("汇总") || lowerDesc.contains("summary")) {
            args.put("replyType", "SUMMARY");
        } else if (lowerDesc.contains("问候") || lowerDesc.contains("打招呼")) {
            args.put("replyType", "GREETING");
        } else if (lowerDesc.contains("澄清") || lowerDesc.contains("确认")) {
            args.put("replyType", "CLARIFY");
        }
        String sessionId = state.sessionId();
        if (sessionId != null && !sessionId.isBlank()) {
            args.put("sessionId", sessionId);
        }
        args.put("todo_description", todo.getDescription());
        args.put("instructions", state.instructions());

        try {
            Object result = replyTool.execute(args);
            LlmDecisionOutcome o = new LlmDecisionOutcome();
            o.chosen = replyTool;
            o.args = args;
            if (result instanceof Map<?, ?> resultMap) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typedMap = (Map<String, Object>) resultMap;
                o.toolResultOverride = typedMap;
            }
            return o;
        } catch (Exception e) {
            log.error("ToolNode: reply_text 纯文本直出执行失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * reply_text 纯文本直出的流式调用：复用 SSE 推送通道（CODE_STREAMING 事件，前端覆盖式渲染）。
     * <p>与 callLlmStreamingForPlainText 区别：本方法不要求 LLM 输出 artifact/HTML，
     * 直接把流式 token 作为回复正文累积。</p>
     */
    private String callLlmStreamingForReplyText(List<ChatMessage> messages, TodoItem todo, DeepAgentState state) {
        final java.util.function.Consumer<com.hypersense.boot.framework.agents.model.AgentEvent> eventConsumer =
                com.hypersense.boot.framework.agents.engine.SubAgentEventBus.get();
        final StringBuilder accumulator = new StringBuilder();
        final long[] lastEmitTime = {0};
        final String streamSessionId = state.sessionId();
        final String streamTodoId = todo.getId();
        final String streamTodoDesc = todo.getDescription();

        dev.langchain4j.model.chat.request.ChatRequest req = dev.langchain4j.model.chat.request.ChatRequest.builder()
                .messages(messages)
                .maxOutputTokens(LLM_MAX_OUTPUT_TOKENS)
                .build();

        java.util.concurrent.CompletableFuture<ChatResponse> future = new java.util.concurrent.CompletableFuture<>();
        streamingChatModel.chat(req, new dev.langchain4j.model.chat.response.StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                // 复用 emitCodeStreaming：前端按 accumulated 覆盖渲染（MessageMarkdown）
                emitCodeStreaming(eventConsumer, accumulator, lastEmitTime,
                        streamTodoId, streamTodoDesc, streamSessionId, partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                future.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                future.completeExceptionally(error);
            }
        });

        ChatResponse resp;
        try {
            resp = future.get(10, java.util.concurrent.TimeUnit.MINUTES);
        } catch (Exception e) {
            future.cancel(true);
            log.warn("ToolNode: reply_text 流式调用失败: {}", e.getMessage());
            return null;
        }
        // reply_text 不读 tool args，主文本即回复正文
        return resp.aiMessage().text();
    }

    // ========== 重试逻辑 ==========

    /**
     * 工具参数预校验，校验失败抛 BusinessException。
     * 在 LLM 决策路径解析 args 后立即调用，避免 file_write 写入空内容或占位符。
     */
    /**
     * content 兜底：当 LLM 调用了 file_write/reply_text 但 args 中 content 缺失或为空时，
     * 尝试从 LLM 的纯文本输出（ai.text()）中提取完整 HTML 或文本作为 content。
     * <p>
     * 适用场景：部分国产模型（含思考链路的 R1/DeepSeek/智谱等）会把工具参数内容写在
     * reasoning_content 或主文本中，而 function call 的 args 留空或仅含 filename。
     * </p>
     * <p>提取规则：</p>
     * <ol>
     *   <li>优先匹配 ```html ... ``` / ``` ... ``` 代码块</li>
     *   <li>其次匹配 &lt;!DOCTYPE html&gt; ... &lt;/html&gt; 完整片段</li>
     *   <li>否则把整段非空 text 当作 content（用于 reply_text 文本兜底）</li>
     * </ol>
     */
    private void rescueContentFromText(String toolName, Map<String, Object> args, String llmText) {
        if (llmText == null || llmText.isBlank()) return;
        if (args == null) return;
        // 仅对需要 content 的工具兜底（file_write_chunk 与 file_write 走同一套提取逻辑）
        boolean isFileWriteLike = "file_write".equals(toolName) || "file_write_chunk".equals(toolName);
        if (!isFileWriteLike && !"reply_text".equals(toolName)) return;

        Object cur = args.get("content");
        if (cur != null && !cur.toString().isBlank()) return;  // 已有合法 content，不覆盖

        String text = llmText.trim();
        String rescued = null;

        // 0. 【artifact 优先解析】open-design 模式：LLM 用 <artifact path="xxx.html">...</artifact>
        // 包裹完整 HTML，规避 JSON 转义与 function-call token 上限——这是 design profile 的主路径
        // 容错：path/name/identifier 三个属性名都接受，兼容 LLM 变体命名
        // 仅对 file_write 类工具生效：reply_text 是纯对话回复，不应被 artifact 劫持（否则会把
        // LLM 偶然输出的 artifact 标签内容塞进回复气泡，污染对话流）
        if (isFileWriteLike) {
        java.util.regex.Matcher artifact = java.util.regex.Pattern.compile(
                "<artifact\\s+(?:path|name|identifier)\\s*=\\s*[\"']([^\"']+)[\"']\\s*>\\s*([\\s\\S]*?)</artifact>",
                java.util.regex.Pattern.CASE_INSENSITIVE
        ).matcher(text);
        if (artifact.find()) {
            String pathVal = artifact.group(1).trim();
            String contentVal = artifact.group(2).trim();
            if (!contentVal.isBlank()) {
                rescued = contentVal;
                // 若 args 没有 filename，从 path 推导（取 basename）
                Object curFn = args.get("filename");
                if ((curFn == null || curFn.toString().isBlank()) && !pathVal.isBlank()) {
                    String basename = pathVal.replace('\\', '/');
                    int slashIdx = basename.lastIndexOf('/');
                    if (slashIdx >= 0) basename = basename.substring(slashIdx + 1);
                    args.put("filename", basename);
                    log.info("ToolNode: artifact 兜底命中，从 path 属性推导 filename='{}'", basename);
                }
                log.info("ToolNode: artifact 兜底命中，提取 {} 字符（path={}）",
                        rescued.length(), pathVal);
            }
        }
        }  // end if (isFileWriteLike)

        // 第 0.5 步：剥除国产 LLM 常见的前言礼貌用语，截到首个 <!DOCTYPE 或 <html 标记处（artifact 未命中时走老路径）
        if (rescued == null) {
            int doctypeIdx = text.indexOf("<!DOCTYPE");
            int htmlIdx = text.indexOf("<html");
            int cutIdx = -1;
            if (doctypeIdx >= 0 && htmlIdx >= 0) cutIdx = Math.min(doctypeIdx, htmlIdx);
            else if (doctypeIdx >= 0) cutIdx = doctypeIdx;
            else if (htmlIdx >= 0) cutIdx = htmlIdx;
            if (cutIdx > 0) {
                text = text.substring(cutIdx).trim();
            }
        }

        // 1. 匹配 ```html ... ``` 或 ``` ... ``` 代码块（放宽：去掉换行强制要求，提高鲁棒性）
        java.util.regex.Matcher codeBlock = java.util.regex.Pattern.compile(
                "```(?:html|HTML|htm)?\\s*([\\s\\S]+?)```"
        ).matcher(text);
        if (codeBlock.find()) {
            rescued = codeBlock.group(1).trim();
        }
        // 2. 匹配完整 HTML 文档片段（必须含 DOCTYPE）
        if (rescued == null) {
            java.util.regex.Matcher html = java.util.regex.Pattern.compile(
                    "(<!DOCTYPE html[\\s\\S]*?</html>)",
                    java.util.regex.Pattern.CASE_INSENSITIVE
            ).matcher(text);
            if (html.find()) {
                rescued = html.group(1).trim();
            }
        }
        // 2.5 HTML 片段兜底：完整 <html>...</html>（不强求 DOCTYPE），需长度合理
        if (rescued == null && isFileWriteLike) {
            java.util.regex.Matcher htmlOnly = java.util.regex.Pattern.compile(
                    "(<html[\\s\\S]*?</html>)", java.util.regex.Pattern.CASE_INSENSITIVE
            ).matcher(text);
            if (htmlOnly.find()) {
                String html = htmlOnly.group(1);
                if (html.length() >= 200) {
                    rescued = html.trim();
                }
            }
        }
        // 3. reply_text 兜底：把整段非空文本作为 content（仅当文本含实质内容时）
        if (rescued == null && "reply_text".equals(toolName) && text.length() >= 10) {
            // 剥除常见 reasoning 前缀（"思考:" 等）
            rescued = text.replaceAll("^\\s*(思考|reasoning|分析)[:：]?\\s*", "").trim();
        }
        // 4. file_write 兜底：若文本看起来是 HTML 片段（含 <html 或 <div 标签且较长）也接受
        if (rescued == null && isFileWriteLike
                && text.length() >= 200
                && (text.toLowerCase().contains("<html") || text.toLowerCase().contains("<div"))) {
            rescued = text;
        }

        if (rescued != null && !rescued.isBlank()) {
            args.put("content", rescued);
            // file_write_chunk 工具契约读 chunk 字段（validateToolArgs 也校验 chunk），
            // rescue 命中后必须同步填 chunk，否则下游 validateToolArgs 取 chunk 为 null → 误抛"参数缺失"
            // → 触发不必要的纯文本回退（regression：function-call 路径文件生成中断）
            if ("file_write_chunk".equals(toolName)) {
                args.put("chunk", rescued);
            }
            log.info("ToolNode: content 兜底命中，从 LLM 文本提取 {} 字符作为 {} 的 content",
                    rescued.length(), toolName);
        }
    }

    private void validateToolArgs(String toolName, Map<String, Object> args) {
        if (args == null) return;
        // file_write_chunk 的单次写入模式（mode=write / mode=start 携带 chunk）走和 file_write 一致的 HTML 完整性校验
        // 历史 bug：LLM 输出被截断在 </head> 处（CSS 过长 + maxOutputTokens 上限），
        // 文件落盘成功但渲染黑屏——必须在校验层拦截，让 LLM 收到清晰错误反馈
        boolean isFileWrite = "file_write".equals(toolName);
        boolean isFileWriteChunkSingleShot = false;
        if ("file_write_chunk".equals(toolName)) {
            String mode = args.get("mode") == null ? "write" : args.get("mode").toString().toLowerCase();
            Object chunk = args.get("chunk");
            // mode=write 或 mode=start 携带 chunk（自动落盘）时启用 HTML 校验；
            // mode=append/end 不校验（缓冲阶段无完整内容）
            if (("write".equals(mode) && chunk != null && !chunk.toString().isBlank())
                    || ("start".equals(mode) && chunk != null && !chunk.toString().isBlank())) {
                isFileWriteChunkSingleShot = true;
            }
        }
        if (isFileWrite || isFileWriteChunkSingleShot) {
            // file_write 用 content 字段；file_write_chunk 用 chunk 字段
            Object content = isFileWrite ? args.get("content") : args.get("chunk");
            String cnLabel = isFileWrite ? "file_write content" : "file_write_chunk chunk";
            if (content == null || content.toString().isBlank()) {
                throw new com.hypersense.boot.common.exception.BusinessException(
                    cnLabel + " 参数缺失或为空。必须提供完整文件内容（HTML/CSS/JS 全部代码）。" +
                    "常见原因：1) 输出被 maxOutputTokens 截断，请确保 content 字段一次性写入完整源码；" +
                    "2) 把内容放到了思考文本里而非工具参数中，请直接放入 content 参数；" +
                    "3) 调用了 file_write 但没有传任何 arguments，请重新调用并附上完整的 content 字段。");
            }
            String c = content.toString();

            // 提取 filename 后缀，判断是否 HTML 文件（仅 .html/.htm 触发专项质量审计）
            Object filenameObj = args.get("filename");
            String filename = filenameObj == null ? "" : filenameObj.toString().toLowerCase();
            boolean isHtml = filename.endsWith(".html") || filename.endsWith(".htm");

            if (isHtml) {
                // HTML 专项质量审计：长度、占位符、结构完整性
                // 1. 最小长度校验：HTML 文件至少 500 字符（<!DOCTYPE> + <html> + <head> + <body> 最小骨架约 200+）
                if (c.length() < 500) {
                    throw new com.hypersense.boot.common.exception.BusinessException(
                        cnLabel + " 长度异常（仅 " + c.length() + " 字符，HTML 至少需 500 字符）。" +
                        "content 必须是完整的 HTML 源码（含 <!DOCTYPE>、<html>、<head>、<body> 等），" +
                        "禁止使用 '...' 占位符或省略代码。请直接在 content 参数中写入完整 HTML 源码。");
                }
                // 2. 占位符检测（不限长度）：检测代码省略信号。
                // 注意：CJK 单字符省略号「…」(U+2026) 在合法 UI 文案中常见（如「加载中…」「更多…」），
                // 不应触发回退；仅检测结构性省略短语（中文化码注释 / 三点代码占位）。
                if (c.contains("...") || c.contains("<原有代码>")
                        || c.contains("省略代码") || c.contains("代码省略") || c.contains("原有代码")
                        || c.contains("<!-- 代码省略") || c.contains("<!--省略") || c.contains("原代码")
                        || c.contains("<!-- 此处省略")) {
                    throw new com.hypersense.boot.common.exception.BusinessException(
                        cnLabel + " 含占位符（'...' / '省略' / '原有代码'），必须提供完整 HTML 源码，禁止省略任何部分。");
                }
                // 3. HTML 结构完整性：必须同时含 <html>/<body> 开闭合标签（防止假 HTML 空壳）
                //    生产 #12f/#12g：LLM 多次因 CSS 过长把 body 全部砍掉，停在 </head> 处，文件落盘但渲染黑屏
                String lower = c.toLowerCase();
                boolean hasHtmlOpen = lower.contains("<html");
                boolean hasHtmlClose = lower.contains("</html>");
                boolean hasBodyOpen = lower.contains("<body");
                boolean hasBodyClose = lower.contains("</body>");
                if (!hasHtmlOpen || !hasHtmlClose || !hasBodyOpen || !hasBodyClose) {
                    throw new com.hypersense.boot.common.exception.BusinessException(
                        cnLabel + " HTML 结构不完整（必须同时含 <html>/<head>/<body> 开闭合标签）。" +
                        "缺失: " +
                        (!hasHtmlOpen ? "<html " : "") +
                        (!hasHtmlClose ? "</html> " : "") +
                        (!hasBodyOpen ? "<body " : "") +
                        (!hasBodyClose ? "</body>" : "") +
                        "。常见原因：CSS 过长导致输出被截断在 </head> 处。" +
                        "解决：1) 砍掉冗余 design token 系统（直接写 color:#xxx 而非 var(--xxx)）；" +
                        "2) 砍 section 数量（保留 3-4 个核心 section）而非砍 body；" +
                        "3) CSS 控制在 200 行内，整体 HTML 控制在 6K tokens 内。");
                }
            } else {
                // 非 HTML 文件：保留原有占位符检测（仅 < 200 字符时触发）
                if (c.length() < 200 && (c.contains("...") || c.contains("…") || c.contains("<原有代码>"))) {
                    throw new com.hypersense.boot.common.exception.BusinessException(
                        "file_write content 疑似含占位符，必须提供完整内容");
                }
            }
        }
        if ("reply_text".equals(toolName)) {
            Object content = args.get("content");
            if (content == null || content.toString().isBlank()) {
                throw new com.hypersense.boot.common.exception.BusinessException(
                    "reply_text content 参数缺失，必须提供回复内容");
            }
        }
    }

    private Object executeWithRetry(ToolProvider tool, Map<String, Object> params) throws Exception {
        if (!retryConfig.isEnabled()) {
            return tool.execute(params);
        }

        int maxAttempts = Math.max(1, retryConfig.getMaxAttempts());
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return tool.execute(params);
            } catch (Exception e) {
                lastException = e;

                if (attempt >= maxAttempts) {
                    log.warn("ToolNode: 工具 [{}] 达到最大重试次数 {}，放弃重试",
                            tool.name(), maxAttempts);
                    break;
                }

                long delay = retryConfig.calculateDelay(attempt);
                log.warn("ToolNode: 工具 [{}] 第 {}/{} 次执行失败，{}ms 后重试: {}",
                        tool.name(), attempt, maxAttempts, delay, e.getMessage());

                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("ToolNode: 工具 [{}] 重试等待被中断", tool.name());
                    throw e;
                }
            }
        }

        throw lastException;
    }
}
