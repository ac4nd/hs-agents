package com.hypersense.boot.framework.agents.engine.node;

import com.hypersense.boot.framework.agents.enums.TodoStatus;
import com.hypersense.boot.framework.agents.exception.HitlInterruptedException;
import com.hypersense.boot.framework.agents.model.DeepAgentState;
import com.hypersense.boot.framework.agents.model.SubAgentContext;
import com.hypersense.boot.framework.agents.model.SubAgentDefinition;
import com.hypersense.boot.framework.agents.model.TodoItem;
import com.hypersense.boot.framework.agents.sandbox.SandboxManager;
import com.hypersense.boot.framework.agents.tool.ToolProvider;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 委派节点（真正子 Agent 委派）
 * <p>
 * 将任务委派给子 Agent 执行。子 Agent 拥有独立的上下文、可配置的工具子集、
 * 独立的 plan→execute→tool 执行循环。通过 {@link SubAgentExecutor} 构建嵌套
 * {@link com.hypersense.boot.framework.agents.GodlikeAgent} 实例同步执行。
 * </p>
 *
 * <h3>委派流程：</h3>
 * <ol>
 *   <li>从 state 获取当前 TodoItem，读取 assignedAgent 名称</li>
 *   <li>解析 SubAgentDefinition（按名称查找或使用默认）</li>
 *   <li>构建 SubAgentContext，委托给 SubAgentExecutor</li>
 *   <li>子 Agent 完成后，结果回传到 SUB_AGENT_RESULTS + TODOS</li>
 * </ol>
 *
 * @author Claude
 * @since 2026/5/15
 */
@Slf4j
public class DelegateNode implements NodeAction<DeepAgentState> {

    private final ChatModel chatModel;
    private final List<ToolProvider> allParentTools;
    private final List<SubAgentDefinition> subAgentDefinitions;
    private final SandboxManager sandboxManager;

    /**
     * 构造委派节点
     *
     * @param chatModel           LLM 模型（传递给子 Agent）
     * @param allParentTools      父 Agent 的全部工具（按 availableTools 过滤后传给子 Agent）
     * @param subAgentDefinitions 子 Agent 定义列表（可为空，使用默认定义）
     * @param sandboxManager      沙箱管理器（复用，通过派生 sessionId 隔离子沙箱）
     */
    public DelegateNode(ChatModel chatModel,
                        List<ToolProvider> allParentTools,
                        List<SubAgentDefinition> subAgentDefinitions,
                        SandboxManager sandboxManager) {
        this.chatModel = chatModel;
        this.allParentTools = allParentTools != null ? allParentTools : List.of();
        this.subAgentDefinitions = subAgentDefinitions != null ? subAgentDefinitions : List.of();
        this.sandboxManager = sandboxManager;
    }

    @Override
    public Map<String, Object> apply(DeepAgentState state) {
        Optional<TodoItem> currentTodoOpt = state.currentTodo();
        if (currentTodoOpt.isEmpty()) {
            log.warn("DelegateNode: 无当前 TODO，跳过");
            return Map.of();
        }

        TodoItem todo = currentTodoOpt.get();
        String agentName = todo.getAssignedAgent() != null ? todo.getAssignedAgent() : "default-agent";

        log.info("DelegateNode: 委派任务 [{}] 给子 Agent [{}]，深度={}",
                todo.getDescription(), agentName, state.delegationDepth());

        // 解析子 Agent 定义
        SubAgentDefinition definition = resolveDefinition(agentName);

        // 构建委派上下文
        SubAgentContext context = SubAgentContext.builder()
                .definition(definition)
                .taskDescription(todo.getDescription())
                .parentSessionId(state.sessionId())
                .parentInstructions(state.instructions())
                .currentDepth(state.delegationDepth())
                .previousSubAgentResults(state.subAgentResults())
                .build();

        // 通过 SubAgentExecutor 执行子 Agent
        SubAgentExecutor executor = new SubAgentExecutor(chatModel, allParentTools, sandboxManager);
        SubAgentExecutor.SubAgentResult result;
        try {
            result = executor.execute(context);
        } catch (HitlInterruptedException e) {
            // HITL 中断信号向上传播，不包装为失败结果
            log.info("DelegateNode: 子 Agent [{}] 触发 HITL 中断，向上传播", agentName);
            throw e;
        }

        // 更新 TODO 状态
        TodoStatus resultStatus = result.isSuccess() ? TodoStatus.COMPLETED : TodoStatus.FAILED;
        String resultText = result.getOutput();

        List<TodoItem> updatedTodos = new ArrayList<>(state.todos());
        int idx = -1;
        for (int i = 0; i < updatedTodos.size(); i++) {
            if (updatedTodos.get(i).getId().equals(todo.getId())) {
                idx = i;
                break;
            }
        }
        if (idx >= 0) {
            updatedTodos.set(idx, TodoItem.builder()
                    .id(todo.getId())
                    .description(todo.getDescription())
                    .status(resultStatus)
                    .result(resultText)
                    .assignedAgent(agentName)
                    .updatedAt(LocalDateTime.now())
                    .build());
        }

        // 记录子 Agent 结果
        Map<String, String> subResults = new HashMap<>(state.subAgentResults());
        subResults.put(todo.getId(), resultText);

        String statusMsg = result.isSuccess() ? "完成" : "失败";
        log.info("DelegateNode: 子 Agent [{}] {}，结果长度={}",
                agentName, statusMsg, resultText != null ? resultText.length() : 0);

        Map<String, Object> output = new HashMap<>();
        output.put(DeepAgentState.TODOS, updatedTodos);
        output.put(DeepAgentState.SUB_AGENT_RESULTS, subResults);
        output.put(DeepAgentState.CURRENT_TODO, null);
        output.put(DeepAgentState.MESSAGES, AiMessage.from(
                String.format("子 Agent [%s] %s: %s", agentName, statusMsg,
                        resultText != null && resultText.length() > 500
                                ? resultText.substring(0, 500) + "..."
                                : resultText)));
        return output;
    }

    // ========== 定义解析 ==========

    /**
     * 按 agentName 查找 SubAgentDefinition，找不到则使用默认
     */
    private SubAgentDefinition resolveDefinition(String agentName) {
        return subAgentDefinitions.stream()
                .filter(d -> d.getName().equals(agentName))
                .findFirst()
                .orElseGet(() -> defaultDefinition(agentName));
    }

    /**
     * 默认子 Agent 定义（无工具、无自定义 prompt）
     */
    private SubAgentDefinition defaultDefinition(String agentName) {
        return SubAgentDefinition.builder()
                .name(agentName)
                .description("默认子 Agent")
                .systemPrompt("你是一个专门的子 Agent（" + agentName + "），负责完成指定任务。请分析任务，制定计划，并逐步执行。")
                .availableTools(List.of())
                .timeoutSeconds(120L)
                .recursionLimit(15)
                .build();
    }
}
