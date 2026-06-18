package com.hypersense.boot.framework.agents.engine;

import org.bsc.langgraph4j.GraphStateException;
import com.hypersense.boot.framework.agents.config.AgentProperties;
import com.hypersense.boot.framework.agents.engine.node.*;
import com.hypersense.boot.framework.agents.engine.route.RouteAfterExecute;
import com.hypersense.boot.framework.agents.engine.route.RouteAfterPlan;
import com.hypersense.boot.framework.agents.model.DeepAgentState;
import com.hypersense.boot.framework.agents.sandbox.SandboxManager;
import com.hypersense.boot.framework.agents.serializer.LangChain4jStateSerializer;
import com.hypersense.boot.framework.agents.skill.SkillsMiddleware;
import com.hypersense.boot.framework.agents.memory.MemoryMiddleware;
import com.hypersense.boot.framework.agents.tool.ToolProvider;
import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * Deep Agent 图构建器（Spring 管理路径）
 * <p>
 * 组装 DeepAgents 的核心执行图：
 * </p>
 * <pre>
 * START → plan → [route] → execute → [route] → delegate/tool → plan → ... → finalize → END
 * </pre>
 *
 * @author Claude
 * @since 2026/5/15
 */
@Slf4j
@Component
public class DeepAgentGraph {

    // toolNode 无 ChatModel 依赖，继续复用 Spring 单例
    private final ToolNode toolNode;
    private final RouteAfterPlan routeAfterPlan;
    private final RouteAfterExecute routeAfterExecute;
    private final AgentProperties agentProperties;
    private final Optional<BaseCheckpointSaver> checkpointSaver;

    // 兜底 ChatModel：session 未指定模型或 Registry 构建失败时使用
    private final ChatModel chatModel;

    // SkillsMiddleware（条件注入，通过 SkillAutoConfiguration 创建）
    private final Optional<SkillsMiddleware> skillsMiddleware;

    // MemoryMiddleware（条件注入，通过 MemoryAutoConfiguration 创建）
    private final Optional<MemoryMiddleware> memoryMiddleware;

    // 节点工厂：按 session 绑定的 ChatModel 实例化节点（支持会话级模型切换）
    private final NodeFactory nodeFactory;

    public DeepAgentGraph(ToolNode toolNode,
                          RouteAfterPlan routeAfterPlan,
                          RouteAfterExecute routeAfterExecute,
                          AgentProperties agentProperties,
                          Optional<BaseCheckpointSaver> checkpointSaver,
                          ChatModel chatModel,
                          Optional<SkillsMiddleware> skillsMiddleware,
                          Optional<MemoryMiddleware> memoryMiddleware,
                          NodeFactory nodeFactory) {
        this.toolNode = toolNode;
        this.routeAfterPlan = routeAfterPlan;
        this.routeAfterExecute = routeAfterExecute;
        this.agentProperties = agentProperties;
        this.checkpointSaver = checkpointSaver;
        this.chatModel = chatModel;
        this.skillsMiddleware = skillsMiddleware;
        this.memoryMiddleware = memoryMiddleware;
        this.nodeFactory = nodeFactory;
    }

    /**
     * 构建 Deep Agent 执行图（无 HITL，向后兼容；使用 Spring 注入的兜底 ChatModel）。
     */
    public CompiledGraph<DeepAgentState> build() throws GraphStateException {
        return build(null);
    }

    /**
     * 构建 Deep Agent 执行图（支持 HITL 中断配置；使用 Spring 注入的兜底 ChatModel）。
     * <p>
     * 旧路径，等价于 {@link #build(ChatModel, HitlBuildConfig)} 传入 {@code null} sessionModel。
     * </p>
     */
    public CompiledGraph<DeepAgentState> build(HitlBuildConfig hitlConfig) throws GraphStateException {
        return build(null, hitlConfig);
    }

    /**
     * 构建 Deep Agent 执行图（支持会话级 ChatModel 绑定 + HITL 中断配置）。
     * <p>
     * sessionModel 为 null 时回退 Spring 注入的兜底 ChatModel（向后兼容旧调用方）。
     * 节点通过 {@link NodeFactory} 按 sessionModel 实例化，避免单例 ChatModel 强耦合。
     * </p>
     *
     * @param sessionModel session 绑定的 ChatModel（可为 null，回退兜底）
     * @param hitlConfig   HITL 配置（null 或 hitlEnabled=false 时不配置中断）
     */
    public CompiledGraph<DeepAgentState> build(ChatModel sessionModel, HitlBuildConfig hitlConfig) throws GraphStateException {
        ChatModel effectiveModel = sessionModel != null ? sessionModel : this.chatModel;
        log.info("构建 Deep Agent 执行图, hitlEnabled={}, sessionModelBound={}",
                hitlConfig != null && hitlConfig.hitlEnabled, sessionModel != null);

        // 通过 NodeFactory 按 session 模型构造节点；sessionModel 为空时使用兜底单例
        PlanNode sessionPlanNode = nodeFactory.planNode(effectiveModel);
        ExecuteNode sessionExecuteNode = nodeFactory.executeNode(effectiveModel);
        FinalizeNode sessionFinalizeNode = nodeFactory.finalizeNode(effectiveModel);
        DelegateNode delegateNode = nodeFactory.delegateNode(effectiveModel);

        // 构建编译配置
        var configBuilder = CompileConfig.builder()
                .recursionLimit(agentProperties.getDeep().getRecursionLimit());

        if (hitlConfig != null && hitlConfig.hitlEnabled) {
            List<String> interruptNodes = resolveInterruptNodes(hitlConfig);
            if (!interruptNodes.isEmpty()) {
                configBuilder.interruptBefore(interruptNodes.toArray(new String[0]));
                log.info("HITL 已启用，中断节点: {}", interruptNodes);
            }
        }

        checkpointSaver.ifPresent(configBuilder::checkpointSaver);
        CompileConfig compileConfig = configBuilder.build();

        StateGraph<DeepAgentState> graph = new StateGraph<>(
                DeepAgentState.SCHEMA,
                LangChain4jStateSerializer.create()
        );

        // 添加节点（plan 节点条件包装 SkillsMiddleware + MemoryMiddleware）
        NodeAction<DeepAgentState> planBase = sessionPlanNode;
        NodeAction<DeepAgentState> planWithSkills = skillsMiddleware
                .map(mw -> wrapWithSkillMiddleware("plan", planBase, mw))
                .orElse(planBase);
        NodeAction<DeepAgentState> planWithMemory = memoryMiddleware
                .map(mw -> wrapWithMemoryMiddleware("plan", planWithSkills, mw))
                .orElse(planWithSkills);
        graph.addNode("plan", node_async(planWithMemory));
        graph.addNode("execute", node_async(sessionExecuteNode));
        graph.addNode("delegate", node_async(delegateNode));
        graph.addNode("tool", node_async(toolNode));

        NodeAction<DeepAgentState> wrappedFinalizeNode = memoryMiddleware
                .map(mw -> wrapWithMemoryMiddleware("finalize", sessionFinalizeNode, mw))
                .orElse(sessionFinalizeNode);
        graph.addNode("finalize", node_async(wrappedFinalizeNode));

        graph.addEdge(START, "plan");
        graph.addConditionalEdges("plan",
                edge_async(routeAfterPlan),
                Map.of("execute", "execute", "finalize", "finalize", END, END));
        graph.addConditionalEdges("execute",
                edge_async(routeAfterExecute),
                Map.of("delegate", "delegate", "tool", "tool", "plan", "plan"));
        graph.addEdge("delegate", "plan");
        graph.addEdge("tool", "plan");
        graph.addEdge("finalize", END);

        CompiledGraph<DeepAgentState> compiled = graph.compile(compileConfig);
        log.info("Deep Agent 执行图构建完成");
        return compiled;
    }

    /**
     * 包装节点，注入 SkillsMiddleware 的 before 钩子
     * <p>
     * Spring 路径不经过 MiddlewarePipeline，需要手动包装。
     * 仅在 SkillsMiddleware 存在时（即配置了技能目录）才生效。
     * </p>
     */
    private NodeAction<DeepAgentState> wrapWithSkillMiddleware(
            String nodeName, NodeAction<DeepAgentState> delegate, SkillsMiddleware mw) {
        return state -> {
            mw.before(nodeName, state);
            return delegate.apply(state);
        };
    }

    /**
     * 包装节点，注入 MemoryMiddleware 的 before/after 钩子
     * <p>
     * plan 节点：before 注入记忆上下文
     * finalize 节点：after 提取事实并存储
     * </p>
     */
    private NodeAction<DeepAgentState> wrapWithMemoryMiddleware(
            String nodeName, NodeAction<DeepAgentState> delegate, MemoryMiddleware mw) {
        return state -> {
            // before() 通过 ThreadLocal 旁路传递记忆增强（state.data() 节点执行期间不可修改），
            // 必须用 try-finally 保证 ThreadLocal 清理，避免节点抛异常时线程池复用导致串话。
            try {
                mw.before(nodeName, state);
                Map<String, Object> output = delegate.apply(state);
                return mw.after(nodeName, state, output);
            } finally {
                com.hypersense.boot.framework.agents.memory.MemoryMiddleware.ENHANCED_INSTRUCTIONS.remove();
            }
        };
    }

    /** 有效节点白名单（用于中断节点配置校验） */
    private static final Set<String> VALID_INTERRUPT_NODES =
            Set.of("plan", "execute", "delegate", "tool", "finalize");

    /**
     * 解析 HITL 中断节点列表
     * <p>
     * 优先使用会话级配置，回退到全局配置。校验节点名有效性。
     * </p>
     */
    private List<String> resolveInterruptNodes(HitlBuildConfig hitlConfig) {
        List<String> nodes = hitlConfig.interruptNodes;
        if (nodes == null || nodes.isEmpty()) {
            // 回退到全局配置
            nodes = agentProperties.getHitl().getInterruptNodes();
        }
        if (nodes == null || nodes.isEmpty()) {
            // 默认中断 tool 节点
            return List.of("tool");
        }

        // 校验节点名有效性
        List<String> invalidNodes = nodes.stream()
                .filter(n -> !VALID_INTERRUPT_NODES.contains(n))
                .toList();
        if (!invalidNodes.isEmpty()) {
            throw new IllegalArgumentException(
                    "无效的 HITL 中断节点: " + invalidNodes + "，有效值为: " + VALID_INTERRUPT_NODES);
        }
        return nodes;
    }

    /**
     * HITL 构建配置（per-session）
     * <p>
     * 每个会话可以独立配置 HITL 行为，不影响其他会话。
     * </p>
     */
    public static class HitlBuildConfig {
        public final boolean hitlEnabled;
        public final List<String> interruptNodes;

        public HitlBuildConfig(boolean hitlEnabled, List<String> interruptNodes) {
            this.hitlEnabled = hitlEnabled;
            this.interruptNodes = interruptNodes;
        }

        /** HITL 关闭的默认配置 */
        public static HitlBuildConfig disabled() {
            return new HitlBuildConfig(false, null);
        }
    }
}
