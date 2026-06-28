package com.hypersense.boot.framework.agents;

import com.hypersense.boot.framework.agents.config.AgentProperties;
import com.hypersense.boot.framework.agents.config.ToolRetryConfig;
import com.hypersense.boot.framework.agents.engine.node.*;
import com.hypersense.boot.framework.agents.engine.route.RouteAfterExecute;
import com.hypersense.boot.framework.agents.engine.route.RouteAfterPlan;
import com.hypersense.boot.framework.agents.enums.AgentEventType;
import com.hypersense.boot.framework.agents.hitl.HitlGateChecker;
import com.hypersense.boot.framework.agents.middleware.AgentMiddleware;
import com.hypersense.boot.framework.agents.middleware.MiddlewarePipeline;
import com.hypersense.boot.framework.agents.middleware.impl.LoggingMiddleware;
import com.hypersense.boot.framework.agents.middleware.impl.LargeOutputOffloadMiddleware;
import com.hypersense.boot.framework.agents.middleware.impl.MessageCompressionMiddleware;
import com.hypersense.boot.framework.agents.model.AgentEvent;
import com.hypersense.boot.framework.agents.model.DeepAgentState;
import com.hypersense.boot.framework.agents.model.SubAgentDefinition;
import com.hypersense.boot.framework.agents.sandbox.Sandbox;
import com.hypersense.boot.framework.agents.sandbox.factory.SandboxFactory;
import com.hypersense.boot.framework.agents.sandbox.SandboxManager;
import com.hypersense.boot.framework.agents.skill.SkillLoadTool;
import com.hypersense.boot.framework.agents.skill.SkillRegistry;
import com.hypersense.boot.framework.agents.skill.SkillsMiddleware;
import com.hypersense.boot.framework.agents.tool.ToolProvider;
import com.hypersense.boot.framework.agents.tool.impl.SandboxTool;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * GodlikeAgent — 快速构造 AI Agent 的入口
 * <p>
 * 使用 Builder 模式一行代码创建可运行的 Agent，无需依赖 Spring 容器。
 * </p>
 *
 * <h3>最简用法：</h3>
 * <pre>{@code
 * String result = GodlikeAgent.builder()
 *     .model(chatModel)
 *     .build()
 *     .run("写一个冒泡排序");
 * }</pre>
 *
 * <h3>从配置快速创建：</h3>
 * <pre>{@code
 * String result = GodlikeAgent.builder()
 *     .apiKey("xxx").endpoint("https://...").modelName("glm-4")
 *     .build()
 *     .run("帮我写一份测试报告");
 * }</pre>
 *
 * <h3>带工具 + 沙箱：</h3>
 * <pre>{@code
 * GodlikeAgent agent = GodlikeAgent.builder()
 *     .model(chatModel)
 *     .addTool(new InternetSearchTool(...))
 *     .sandbox(sandboxFactory)
 *     .recursionLimit(25)
 *     .build();
 * String result = agent.run("搜索今天新闻并总结");
 * }</pre>
 *
 * @author Claude
 * @since 2026/5/20
 */
@Slf4j
public class GodlikeAgent {

    private final CompiledGraph<DeepAgentState> graph;
    private final SandboxManager sandboxManager;
    private final SkillsMiddleware skillsMiddleware;

    private GodlikeAgent(CompiledGraph<DeepAgentState> graph, SandboxManager sandboxManager,
                         SkillsMiddleware skillsMiddleware) {
        this.graph = graph;
        this.sandboxManager = sandboxManager;
        this.skillsMiddleware = skillsMiddleware;
    }

    public static Builder builder() {
        return new Builder();
    }

    // ========== 执行方法 ==========

    /**
     * 同步执行 Agent，返回最终响应文本
     *
     * @param input 用户输入
     * @return Agent 最终响应（null 表示执行失败或无响应）
     */
    public String run(String input) {
        return run(input, 0);
    }

    /**
     * 同步执行 Agent（指定委派深度），返回最终响应文本
     *
     * @param input           用户输入
     * @param delegationDepth 当前委派深度（0 = 根 Agent）
     * @return Agent 最终响应（null 表示执行失败或无响应）
     */
    public String run(String input, int delegationDepth) {
        String sessionId = generateSessionId();
        try {
            Map<String, Object> initialState = buildInitialState(sessionId, input);
            initialState.put(DeepAgentState.DELEGATION_DEPTH, delegationDepth);
            RunnableConfig config = RunnableConfig.builder().threadId(sessionId).build();
            var resultOpt = graph.invoke(initialState, config);
            if (resultOpt.isEmpty()) {
                log.warn("GodlikeAgent: 执行返回空结果，sessionId={}", sessionId);
                return null;
            }
            DeepAgentState finalState = resultOpt.get();
            return finalState.finalResponse().orElse(null);
        } catch (Exception e) {
            log.error("GodlikeAgent: 执行失败，sessionId={}", sessionId, e);
            throw new RuntimeException("Agent 执行失败: " + e.getMessage(), e);
        } finally {
            destroySandbox(sessionId);
        }
    }

    /**
     * 流式执行 Agent，通过回调推送节点执行事件
     *
     * @param input         用户输入
     * @param eventConsumer 事件消费者
     */
    public void stream(String input, Consumer<AgentEvent> eventConsumer) {
        String sessionId = generateSessionId();
        try {
            Map<String, Object> initialState = buildInitialState(sessionId, input);
            RunnableConfig config = RunnableConfig.builder().threadId(sessionId).build();
            var generator = graph.stream(initialState, config);
            for (var nodeOutput : generator) {
                AgentEvent event = AgentEvent.builder()
                        .type(AgentEventType.NODE_EXECUTION)
                        .message("节点执行: " + nodeOutput.node())
                        .data(nodeOutput.state())
                        .timestamp(System.currentTimeMillis())
                        .build();
                eventConsumer.accept(event);
            }
            eventConsumer.accept(AgentEvent.builder()
                    .type(AgentEventType.FINAL_RESPONSE)
                    .message("执行完成")
                    .timestamp(System.currentTimeMillis())
                    .build());
        } catch (Exception e) {
            log.error("GodlikeAgent: 流式执行失败，sessionId={}", sessionId, e);
            eventConsumer.accept(AgentEvent.builder()
                    .type(AgentEventType.ERROR)
                    .message("执行失败: " + e.getMessage())
                    .timestamp(System.currentTimeMillis())
                    .build());
        } finally {
            destroySandbox(sessionId);
        }
    }

    /**
     * 流式执行 Agent 并返回最终响应（子 Agent 专用）
     * <p>
     * 与 {@link #stream} 类似，但同时返回最终响应文本。
     * 专为 {@link com.hypersense.boot.framework.agents.engine.node.SubAgentExecutor} 设计，
     * 既将内部节点事件推送给 eventConsumer，又保留最终结果供调用方收集。
     * </p>
     *
     * @param input           用户输入
     * @param delegationDepth 当前委派深度
     * @param eventConsumer   事件消费者（子 Agent 事件包装后向上冒泡）
     * @return Agent 最终响应（null 表示执行失败或无响应）
     */
    public String streamAndReturn(String input, int delegationDepth, Consumer<AgentEvent> eventConsumer) {
        String sessionId = generateSessionId();
        try {
            Map<String, Object> initialState = buildInitialState(sessionId, input);
            initialState.put(DeepAgentState.DELEGATION_DEPTH, delegationDepth);
            RunnableConfig config = RunnableConfig.builder().threadId(sessionId).build();

            String finalResponse = null;
            var generator = graph.stream(initialState, config);
            for (var nodeOutput : generator) {
                AgentEvent event = AgentEvent.builder()
                        .type(AgentEventType.NODE_EXECUTION)
                        .message("节点执行: " + nodeOutput.node())
                        .data(nodeOutput.state())
                        .timestamp(System.currentTimeMillis())
                        .build();
                eventConsumer.accept(event);
                // 追踪最新的 finalResponse
                finalResponse = nodeOutput.state().finalResponse().orElse(finalResponse);
            }
            eventConsumer.accept(AgentEvent.builder()
                    .type(AgentEventType.FINAL_RESPONSE)
                    .message("执行完成")
                    .data(finalResponse)
                    .timestamp(System.currentTimeMillis())
                    .build());
            return finalResponse;
        } catch (Exception e) {
            log.error("GodlikeAgent: 流式执行失败，sessionId={}", sessionId, e);
            eventConsumer.accept(AgentEvent.builder()
                    .type(AgentEventType.ERROR)
                    .message("执行失败: " + e.getMessage())
                    .timestamp(System.currentTimeMillis())
                    .build());
            throw new RuntimeException("Agent 执行失败: " + e.getMessage(), e);
        } finally {
            destroySandbox(sessionId);
        }
    }

    /**
     * 获取底层编译图（高级用法）
     */
    public CompiledGraph<DeepAgentState> graph() {
        return graph;
    }

    // ========== 内部方法 ==========

    private String generateSessionId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private Map<String, Object> buildInitialState(String sessionId, String input) {
        Map<String, Object> state = new HashMap<>();
        state.put(DeepAgentState.SESSION_ID, sessionId);
        // 技能目录注入：在构建初始状态时将技能目录追加到 instructions 中
        String instructions = (skillsMiddleware != null && skillsMiddleware.hasSkills())
                ? skillsMiddleware.enhanceInstructions(input)
                : input;
        state.put(DeepAgentState.INSTRUCTIONS, instructions);
        state.put(DeepAgentState.MESSAGES, List.of(UserMessage.from(input)));
        return state;
    }

    private void destroySandbox(String sessionId) {
        if (sandboxManager != null) {
            try {
                sandboxManager.destroy(sessionId);
            } catch (Exception e) {
                log.warn("GodlikeAgent: 沙箱销毁失败，sessionId={}", sessionId, e);
            }
        }
    }

    // ========== Builder ==========

    /**
     * GodlikeAgent 构建器
     */
    public static class Builder {

        // ChatModel 配置（二选一）
        private ChatModel chatModel;
        /**
         * 流式 ChatModel 注入点（可选）。
         * <p>注入后传给 ToolNode，使 file_write 长内容生成走流式，规避同步整体超时。</p>
         */
        private dev.langchain4j.model.chat.StreamingChatModel streamingChatModel;
        private String apiKey;
        private String endpoint;
        private String modelName;
        private Double temperature;
        private Integer maxTokens;

        // 工具
        private final List<ToolProvider> tools = new ArrayList<>();

        // 沙箱（二选一）
        private SandboxFactory sandboxFactory;
        private Sandbox sandbox;

        // 高级配置
        private BaseCheckpointSaver checkpointSaver;
        private Integer recursionLimit;

        // 子 Agent 定义
        private final List<SubAgentDefinition> subAgentDefinitions = new ArrayList<>();

        // 复用已有 SandboxManager（子 Agent 场景）
        private SandboxManager existingSandboxManager;

        // 中间件
        private final MiddlewarePipeline middlewarePipeline = new MiddlewarePipeline();

        // HITL 配置
        private boolean hitlEnabled = false;
        private java.util.Set<String> hitlInterruptNodes = java.util.Set.of("tool");

        // 工具重试配置
        private ToolRetryConfig toolRetryConfig;

        // 技能目录
        private String[] skillDirs;
        private SkillsMiddleware skillsMiddleware;

        // ---- 链式方法 ----

        /**
         * 直接传入已构建的 ChatModel
         */
        public Builder model(ChatModel chatModel) {
            this.chatModel = chatModel;
            return this;
        }

        /**
         * OpenAI 兼容 API Key（自动构建 ChatModel）
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * API 端点地址
         */
        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        /**
         * 模型名称
         */
        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        /**
         * 温度参数（0.0 - 2.0）
         */
        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        /**
         * 最大生成 Token 数
         */
        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        /**
         * 批量设置工具
         */
        public Builder tools(List<ToolProvider> tools) {
            this.tools.clear();
            this.tools.addAll(tools);
            return this;
        }

        /**
         * 添加单个工具
         */
        public Builder addTool(ToolProvider tool) {
            this.tools.add(tool);
            return this;
        }

        /**
         * 设置沙箱工厂（Thread-scoped 模式）
         */
        public Builder sandbox(SandboxFactory sandboxFactory) {
            this.sandboxFactory = sandboxFactory;
            this.sandbox = null;
            return this;
        }

        /**
         * 设置单个沙箱实例（简单模式）
         */
        public Builder sandbox(Sandbox sandbox) {
            this.sandbox = sandbox;
            this.sandboxFactory = null;
            return this;
        }

        /**
         * 设置检查点持久化（默认 MemorySaver）
         */
        public Builder checkpointSaver(BaseCheckpointSaver checkpointSaver) {
            this.checkpointSaver = checkpointSaver;
            return this;
        }

        /**
         * 图递归限制（默认 50）
         */
        public Builder recursionLimit(int recursionLimit) {
            this.recursionLimit = recursionLimit;
            return this;
        }

        /**
         * 添加中间件
         * <p>
         * 中间件在节点执行前后拦截，可用于日志、消息压缩、大输出卸载等。
         * before 按添加顺序执行，after 按逆序执行（洋葱模型）。
         * </p>
         */
        public Builder addMiddleware(AgentMiddleware middleware) {
            this.middlewarePipeline.add(middleware);
            return this;
        }

        /**
         * 启用消息压缩（便捷方法）
         * <p>
         * 当 MESSAGES 累积超过阈值时自动压缩旧消息为摘要。
         * 内部创建 {@link MessageCompressionMiddleware} 并添加到管道。
         * </p>
         *
         * @param maxMessages   最大消息条数（超过触发压缩），默认 20
         * @param maxTotalChars 最大总字符数（超过触发压缩），默认 50000
         * @param keepRecent    保留最近 N 条消息不压缩，默认 4
         */
        public Builder enableMessageCompression(int maxMessages, int maxTotalChars, int keepRecent) {
            // 移除已有的 pending 压缩中间件（防止多次调用残留损坏实例）
            middlewarePipeline.getMiddlewares().removeIf(
                    mw -> mw instanceof MessageCompressionMiddleware
                            && mw.name().equals("message-compression-pending"));
            // ChatModel 在 build() 时才确定，先用占位符，build() 中替换
            this.middlewarePipeline.add(new MessageCompressionMiddleware(null, maxMessages, maxTotalChars, keepRecent) {
                @Override
                public String name() {
                    return "message-compression-pending";
                }
            });
            return this;
        }

        /**
         * 启用消息压缩（使用默认参数）
         */
        public Builder enableMessageCompression() {
            return enableMessageCompression(20, 50000, 4);
        }

        /**
         * 启用结构化日志（便捷方法）
         */
        public Builder enableLogging() {
            this.middlewarePipeline.add(new LoggingMiddleware());
            return this;
        }

        /**
         * 启用大输出卸载（便捷方法，使用默认阈值 10KB）
         * <p>
         * 当节点 MESSAGES 输出超过阈值时，自动卸载到 FILES 通道，
         * 原位置替换为文件引用标记。防止 MESSAGES 因大内容而膨胀。
         * </p>
         */
        public Builder enableLargeOutputOffload() {
            return enableLargeOutputOffload(10 * 1024);
        }

        /**
         * 启用大输出卸载（自定义阈值）
         *
         * @param thresholdBytes 触发卸载的字节数阈值
         */
        public Builder enableLargeOutputOffload(int thresholdBytes) {
            this.middlewarePipeline.add(new LargeOutputOffloadMiddleware(thresholdBytes));
            return this;
        }

        /**
         * 启用 HITL（Human-in-the-Loop）审批
         * <p>
         * 图执行到指定节点前自动暂停，等待人工审批后恢复。
         * 需要配合 checkpoint 使用（默认使用 MemorySaver）。
         * </p>
         */
        public Builder enableHitl() {
            this.hitlEnabled = true;
            return this;
        }

        /**
         * 设置 HITL 中断节点（默认 ["tool"]）
         *
         * @param nodes 中断节点名称列表
         */
        public Builder hitlInterruptNodes(String... nodes) {
            this.hitlInterruptNodes = java.util.Set.of(nodes);
            return this;
        }

        /**
         * 启用工具重试（便捷方法，使用默认重试配置：3次重试，1秒初始退避，2倍增长）
         * <p>
         * 工具执行失败时自动按指数退避重试，提高瞬态故障容忍度。
         * </p>
         */
        public Builder enableToolRetry() {
            this.toolRetryConfig = ToolRetryConfig.defaults();
            return this;
        }

        /**
         * 自定义工具重试配置
         *
         * @param config 重试配置（null 表示不重试）
         */
        public Builder toolRetryConfig(ToolRetryConfig config) {
            this.toolRetryConfig = config;
            return this;
        }

        /**
         * 设置技能目录
         * <p>
         * 框架会扫描每个目录下的子文件夹，查找 SKILL.md 文件并解析元数据。
         * LLM 在规划时看到技能目录，通过 skill_load 工具按需加载详细说明。
         * </p>
         *
         * @param dirs 技能目录路径（支持多个目录）
         */
        public Builder skills(String... dirs) {
            this.skillDirs = dirs;
            return this;
        }

        /**
         * 添加子 Agent 定义
         * <p>
         * 子 Agent 在委派时拥有独立的上下文、可配置的工具子集和独立的执行循环。
         * </p>
         */
        public Builder addSubAgent(SubAgentDefinition definition) {
            this.subAgentDefinitions.add(definition);
            return this;
        }

        /**
         * 批量设置子 Agent 定义
         */
        public Builder subAgentDefinitions(List<SubAgentDefinition> definitions) {
            this.subAgentDefinitions.clear();
            this.subAgentDefinitions.addAll(definitions);
            return this;
        }

        /**
         * 传入已有的 SandboxManager（用于子 Agent 复用父 Agent 的沙箱管理器）
         */
        public Builder sandboxManager(SandboxManager sandboxManager) {
            this.existingSandboxManager = sandboxManager;
            this.sandboxFactory = null;
            this.sandbox = null;
            return this;
        }

        /**
         * 构建 GodlikeAgent 实例
         */
        public GodlikeAgent build() {
            log.info("GodlikeAgent.Builder: 开始构建 Agent");

            // 1. 创建 ChatModel
            ChatModel model = resolveChatModel();
            // 1.5 解析流式 ChatModel（用于 ToolNode 长输出场景，规避整体 timeout）
            streamingChatModel = resolveStreamingChatModel();

            // 2. 初始化消息压缩中间件（需要 ChatModel）
            initializeCompressionMiddleware(model);

            // 2.5. 初始化技能系统
            initializeSkillSystem();

            // 3. 创建 SandboxManager
            SandboxManager sandboxManager = resolveSandboxManager();

            // 4. 自动注册 SandboxTool（防重复：检查是否已存在）
            if (sandboxManager != null && tools.stream().noneMatch(t -> t instanceof SandboxTool)) {
                tools.add(new SandboxTool(sandboxManager));
                log.info("GodlikeAgent.Builder: 自动注册 SandboxTool");
            }

            // 5. 构建执行图（注入中间件管道）
            try {
                CompiledGraph<DeepAgentState> graph = buildGraph(model, middlewarePipeline, sandboxManager);
                log.info("GodlikeAgent.Builder: Agent 构建完成, 中间件数量={}, streamingEnabled={}",
                        middlewarePipeline.getMiddlewares().size(), streamingChatModel != null);
                return new GodlikeAgent(graph, sandboxManager, skillsMiddleware);
            } catch (Exception e) {
                throw new RuntimeException("Agent 图构建失败: " + e.getMessage(), e);
            }
        }

        // ---- 内部构建逻辑 ----

        private ChatModel resolveChatModel() {
            if (chatModel != null) {
                return chatModel;
            }
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalArgumentException(
                        "必须通过 model(ChatModel) 提供已有实例，或通过 apiKey/endpoint/modelName 构建 ChatModel");
            }
            log.info("GodlikeAgent.Builder: 构建 OpenAiChatModel，endpoint={}, model={}", endpoint, modelName);
            return OpenAiChatModel.builder()
                    .baseUrl(endpoint)
                    .apiKey(apiKey)
                    .modelName(modelName)
                    .temperature(temperature != null ? temperature : 0.7)
                    .maxTokens(maxTokens != null ? maxTokens : 4096)
                    .timeout(Duration.ofSeconds(120))
                    .build();
        }

        /**
         * 解析流式 ChatModel：仅当调用方通过 {@link #streamingModel(StreamingChatModel)} 显式注入时启用。
         * 默认返回 null，ToolNode 自动回退同步路径。
         */
        private dev.langchain4j.model.chat.StreamingChatModel resolveStreamingChatModel() {
            return streamingChatModel;
        }

        /**
         * 注入流式 ChatModel，使 ToolNode 长输出场景走流式（推荐生产环境启用）。
         */
        public Builder streamingModel(dev.langchain4j.model.chat.StreamingChatModel streamingChatModel) {
            this.streamingChatModel = streamingChatModel;
            return this;
        }

        private SandboxManager resolveSandboxManager() {
            // 优先使用已有的 SandboxManager（子 Agent 复用场景）
            if (existingSandboxManager != null) {
                return existingSandboxManager;
            }
            SandboxFactory factory = this.sandboxFactory;
            if (factory == null && sandbox != null) {
                // 将单个 Sandbox 实例包装为工厂
                factory = sessionId -> sandbox;
                log.info("GodlikeAgent.Builder: 单个 Sandbox 包装为工厂");
            }
            if (factory == null) {
                return null;
            }
            // 构建最小 AgentProperties（使用默认 sessionTtl=1800）
            AgentProperties props = new AgentProperties();
            return new SandboxManager(factory, props);
        }

        /**
         * 初始化消息压缩中间件
         * <p>
         * enableMessageCompression() 创建的中间件 chatModel 为 null（因为 Builder 时 ChatModel 尚未创建），
         * 此方法在 ChatModel 创建后替换为真实实例。
         * </p>
         */
        private void initializeCompressionMiddleware(ChatModel model) {
            List<AgentMiddleware> list = middlewarePipeline.getMiddlewares();
            for (int i = 0; i < list.size(); i++) {
                AgentMiddleware mw = list.get(i);
                if (mw instanceof MessageCompressionMiddleware && mw.name().equals("message-compression-pending")) {
                    middlewarePipeline.replace(i, new MessageCompressionMiddleware(model));
                    log.info("GodlikeAgent.Builder: 消息压缩中间件已初始化 (index={})", i);
                }
            }
        }

        /**
         * 初始化技能系统
         * <p>
         * 扫描配置的技能目录，注册 SkillsMiddleware 和 SkillLoadTool。
         * 未配置技能目录时不执行任何操作。
         * </p>
         */
        private void initializeSkillSystem() {
            if (skillDirs == null || skillDirs.length == 0) return;

            SkillRegistry skillRegistry = new SkillRegistry();
            skillRegistry.scan(skillDirs);

            if (skillRegistry.isEmpty()) {
                log.warn("GodlikeAgent.Builder: 技能目录已配置但未发现任何技能: {}",
                         java.util.Arrays.toString(skillDirs));
                return;
            }

            this.skillsMiddleware = new SkillsMiddleware(skillRegistry);
            middlewarePipeline.add(skillsMiddleware);
            tools.add(new SkillLoadTool(skillRegistry));
            log.info("GodlikeAgent.Builder: 技能系统已加载, 共 {} 个技能",
                     skillRegistry.getAll().size());
        }

        private CompiledGraph<DeepAgentState> buildGraph(ChatModel model, MiddlewarePipeline pipeline,
                                                          SandboxManager sandboxManager) throws Exception {
            // 创建节点（非 Spring 路径：使用禁用智能门控的 HitlGateChecker，避免误触发 LLM 中断）
            AgentProperties defaultProps = new AgentProperties();
            HitlGateChecker disabledGate = HitlGateChecker.disabled(model);
            // 非 Spring 路径下构造 AttachmentContext：节点可按需把附件图片以 ImageContent 附加到 LLM 调用
            com.hypersense.boot.framework.agents.serializer.AttachmentContext attachmentCtx =
                    new com.hypersense.boot.framework.agents.serializer.AttachmentContext(
                            sandboxManager, null, null);
            // 非 Spring 构建路径：profileRegistry 传 null，PlanNode.buildProfilePromptPrefix 捕获 NPE 后
            // 返回空串，降级走原 PLAN_SYSTEM_PROMPT（独立 Builder 不接入 CapabilityProfile 框架）
            PlanNode planNode = new PlanNode(model, disabledGate, defaultProps, attachmentCtx, null);
            ExecuteNode executeNode = new ExecuteNode(model, disabledGate, attachmentCtx);
            DelegateNode delegateNode = new DelegateNode(model, tools, subAgentDefinitions, sandboxManager);
            ToolNode toolNode = ToolNode.create(tools, toolRetryConfig, model, streamingChatModel);
            FinalizeNode finalizeNode = new FinalizeNode(model, attachmentCtx);

            // 创建路由
            RouteAfterPlan routeAfterPlan = new RouteAfterPlan();
            RouteAfterExecute routeAfterExecute = new RouteAfterExecute();

            // 构建图 — 使用兼容 langchain4j 1.0.0 的自定义序列化器
            var stateSerializer = com.hypersense.boot.framework.agents.serializer.LangChain4jStateSerializer.create();
            StateGraph<DeepAgentState> graph = new StateGraph<>(
                    DeepAgentState.SCHEMA,
                    stateSerializer
            );

            // 添加节点（通过中间件管道包装）
            graph.addNode("plan", node_async(pipeline.wrap("plan", planNode)));
            graph.addNode("execute", node_async(pipeline.wrap("execute", executeNode)));
            graph.addNode("delegate", node_async(pipeline.wrap("delegate", delegateNode)));
            graph.addNode("tool", node_async(pipeline.wrap("tool", toolNode)));
            graph.addNode("finalize", node_async(pipeline.wrap("finalize", finalizeNode)));

            // 添加边
            graph.addEdge(START, "plan");
            graph.addConditionalEdges("plan",
                    edge_async(routeAfterPlan),
                    Map.of("execute", "execute", "finalize", "finalize"));
            graph.addConditionalEdges("execute",
                    edge_async(routeAfterExecute),
                    Map.of("delegate", "delegate", "tool", "tool", "plan", "plan"));
            graph.addEdge("delegate", "plan");
            graph.addEdge("tool", "plan");
            graph.addEdge("finalize", END);

            // 编译配置
            int limit = recursionLimit != null ? recursionLimit : 50;
            var configBuilder = CompileConfig.builder()
                    .recursionLimit(limit);

            if (checkpointSaver != null) {
                configBuilder.checkpointSaver(checkpointSaver);
            }

            // HITL 中断配置
            if (hitlEnabled && !hitlInterruptNodes.isEmpty()) {
                configBuilder.interruptBefore(hitlInterruptNodes.toArray(new String[0]));
                // HITL 需要检查点支持才能恢复，自动添加 MemorySaver
                if (checkpointSaver == null) {
                    configBuilder.checkpointSaver(new org.bsc.langgraph4j.checkpoint.MemorySaver());
                }
            }

            // 不设置默认 MemorySaver — LangChain4j ChatMessage 不可序列化，
            // 且 GodlikeAgent 的单次执行模式不需要 checkpoint

            return graph.compile(configBuilder.build());
        }
    }
}
