package com.hypersense.boot.framework.agents.engine;

import com.hypersense.boot.framework.agents.config.AgentProperties;
import com.hypersense.boot.framework.agents.config.ToolRetryConfig;
import com.hypersense.boot.framework.agents.engine.node.DelegateNode;
import com.hypersense.boot.framework.agents.engine.node.ExecuteNode;
import com.hypersense.boot.framework.agents.engine.node.FinalizeNode;
import com.hypersense.boot.framework.agents.engine.node.PlanNode;
import com.hypersense.boot.framework.agents.engine.node.ToolNode;
import com.hypersense.boot.framework.agents.hitl.HitlGateChecker;
import com.hypersense.boot.framework.agents.sandbox.SandboxManager;
import com.hypersense.boot.framework.agents.serializer.AttachmentContext;
import com.hypersense.boot.framework.agents.tool.ToolProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 节点工厂：按 session 绑定的 ChatModel 实例化节点。
 * <p>
 * 节点本身是无状态/轻状态的（每次执行都从 DeepAgentState 取输入），
 * 把 ChatModel 作为构造参数注入是为了支持「会话级动态切换模型」：
 * 不同 session 可以绑定不同 ChatModel，DeepAgentGraph 在 build 时通过本工厂生成节点实例。
 * </p>
 * <p>
 * 其它依赖（HitlGateChecker / AgentProperties / ToolProviders / SandboxManager / AttachmentContext）
 * 由 Spring 注入并复用，避免每次重建造成的资源浪费。
 * </p>
 *
 * @author Claude
 * @since 2026/6/18
 */
@Component
@RequiredArgsConstructor
public class NodeFactory {

    private final HitlGateChecker hitlGateChecker;
    private final AgentProperties agentProperties;
    private final List<ToolProvider> toolProviders;
    private final SandboxManager sandboxManager;
    private final AttachmentContext attachmentContext;
    private final com.hypersense.boot.framework.agents.profile.CapabilityProfileRegistry profileRegistry;

    public PlanNode planNode(ChatModel chatModel) {
        return new PlanNode(chatModel, hitlGateChecker, agentProperties, attachmentContext, profileRegistry);
    }

    public ExecuteNode executeNode(ChatModel chatModel) {
        return new ExecuteNode(chatModel, hitlGateChecker, attachmentContext);
    }

    public FinalizeNode finalizeNode(ChatModel chatModel) {
        return new FinalizeNode(chatModel, attachmentContext);
    }

    public DelegateNode delegateNode(ChatModel chatModel) {
        return new DelegateNode(chatModel, toolProviders, List.of(), sandboxManager);
    }

    /**
     * 构造 session 级 ToolNode：注入同步 + 流式 ChatModel。
     * <p>streamingChatModel 非空时优先走流式分支（用于 file_write 长内容生成，规避同步整体超时）。</p>
     */
    public ToolNode toolNode(ChatModel chatModel, @Nullable StreamingChatModel streamingChatModel) {
        ToolRetryConfig retryConfig = ToolRetryConfig.fromProperties(agentProperties.getTools().getToolRetry());
        return ToolNode.create(toolProviders, retryConfig, chatModel, streamingChatModel);
    }
}
