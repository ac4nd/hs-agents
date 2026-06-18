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
import dev.langchain4j.data.message.SystemMessage;
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

    private final List<ToolProvider> toolProviders;
    private final ToolRetryConfig retryConfig;
    /** 可选：LangChain4j function-call 决策模型；为 null 时走旧遍历逻辑 */
    private final ChatModel chatModel;

    @Autowired
    public ToolNode(@Nullable List<ToolProvider> toolProviders,
                    @Nullable AgentProperties agentProperties,
                    @Nullable ChatModel chatModel) {
        this.toolProviders = toolProviders != null ? toolProviders : List.of();
        this.retryConfig = resolveRetryConfig(agentProperties);
        this.chatModel = chatModel;
    }

    /** 旧签名兼容：Spring 自动注入时如未显式注入 ChatModel 仍可工作（走旧遍历逻辑）。 */
    public ToolNode(@Nullable List<ToolProvider> toolProviders,
                    @Nullable AgentProperties agentProperties) {
        this(toolProviders, agentProperties, null);
    }

    /**
     * Builder 路径：创建带自定义重试配置的 ToolNode（不经过 Spring）。
     * 旧二参版本，保留以兼容既有调用方（如 ToolRetryTest）。
     */
    public static ToolNode create(List<ToolProvider> toolProviders, ToolRetryConfig retryConfig) {
        return new ToolNode(toolProviders, retryConfig != null ? retryConfig : ToolRetryConfig.disabled(), null);
    }

    /**
     * Builder 路径：创建带 ChatModel 的 ToolNode，启用 LangChain4j function-call 决策。
     */
    public static ToolNode create(List<ToolProvider> toolProviders, ToolRetryConfig retryConfig, ChatModel chatModel) {
        return new ToolNode(toolProviders,
                retryConfig != null ? retryConfig : ToolRetryConfig.disabled(),
                chatModel);
    }

    private ToolNode(List<ToolProvider> toolProviders, ToolRetryConfig retryConfig, ChatModel chatModel) {
        this.toolProviders = toolProviders;
        this.retryConfig = retryConfig;
        this.chatModel = chatModel;
    }

    private static ToolRetryConfig resolveRetryConfig(AgentProperties props) {
        if (props == null) {
            return ToolRetryConfig.disabled();
        }
        return ToolRetryConfig.fromProperties(props.getTools().getToolRetry());
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
        List<String> enabledTools = state.enabledTools();
        List<ToolProvider> candidates = new ArrayList<>();
        for (ToolProvider tool : toolProviders) {
            if (!enabledTools.isEmpty() && !enabledTools.contains(tool.name())) {
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

        // 优先：LangChain4j function-call 决策（chatModel 已注入且候选非空）
        if (chatModel != null && !candidates.isEmpty()) {
            LlmDecisionOutcome outcome = decideByLlm(candidates, todo, state);
            if (outcome != null && outcome.chosen != null) {
                llmDecisionUsed = true;
                String chosenName = outcome.chosen.name();
                try {
                    Object result = executeWithRetry(outcome.chosen, outcome.args);
                    toolResults.put(chosenName, result);
                    handleFileWriteSideEffect(outcome.chosen, result, state, todo, files);
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
            emit(AgentEventType.TOOL_CALL, "调用工具: " + todo.getDescription(),
                    Map.of("todo", todo, "tools", candidates.stream().map(ToolProvider::name).toList()));
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

        return Map.of(
                DeepAgentState.TODOS, updatedTodos,
                DeepAgentState.FILES, files,
                DeepAgentState.MESSAGES, AiMessage.from(
                        String.format("工具调用完成: %s", toolResults.keySet()))
        );
    }

    /**
     * 调用 LangChain4j ChatModel 让 LLM 选定工具 + 填写参数。
     * 失败返回 null（由 apply 回退旧逻辑）；成功但 LLM 没选工具时返回带 failReason 的 outcome。
     */
    private LlmDecisionOutcome decideByLlm(List<ToolProvider> candidates, TodoItem todo, DeepAgentState state) {
        try {
            List<ToolSpecification> specs = new ArrayList<>();
            for (ToolProvider t : candidates) {
                specs.add(t.specification());
            }

            String systemPrompt =
                    "你是工具选择器。根据当前 TODO 和完整上下文，选择一个最合适的工具并填写其参数。" +
                    "只输出工具调用，不要任何解释或额外文本。\n" +
                    "重要约束：\n" +
                    "1. 必须基于前序步骤的实际产出（前序 TODO 的 result）来填写参数，严禁凭空编造或臆测内容。\n" +
                    "2. 如果当前 TODO 是保存/写入文件（file_write），content 参数必须完整复制前序步骤产出的原文，" +
                    "不允许做任何总结、改写、截断、压缩或重新组织；保留原文的全部格式、换行、标记符号。\n" +
                    "3. 如果上下文中没有可直接使用的前序产出且 TODO 需要内容参数，应选择不调用任何工具（让上层回退）。";
            String userPrompt = buildUserPrompt(todo, state);

            List<ChatMessage> messages = List.of(
                    SystemMessage.from(systemPrompt),
                    UserMessage.from(userPrompt)
            );

            ChatResponse resp = chatModel.chat(ChatRequest.builder()
                    .messages(messages)
                    .toolSpecifications(specs)
                    .maxOutputTokens(4096)
                    .build());
            AiMessage ai = resp.aiMessage();

            if (!ai.hasToolExecutionRequests()) {
                LlmDecisionOutcome o = new LlmDecisionOutcome();
                o.failReason = "LLM 未调用任何工具（可能 TODO 不需要工具或上下文不足）";
                return o;
            }

            ToolExecutionRequest req = ai.toolExecutionRequests().get(0);
            String reqName = req.name();
            Optional<ToolProvider> chosenOpt = candidates.stream()
                    .filter(t -> t.name().equals(reqName))
                    .findFirst();
            if (chosenOpt.isEmpty()) {
                LlmDecisionOutcome o = new LlmDecisionOutcome();
                o.failReason = "LLM 调用了未注册的工具: " + reqName;
                return o;
            }

            LlmDecisionOutcome o = new LlmDecisionOutcome();
            o.chosen = chosenOpt.get();
            o.args = ToolProvider.parseArguments(req.arguments());
            // 透传 sessionId 供沙箱工具等需要会话隔离的组件使用
            String sessionId = state.sessionId();
            if (sessionId != null && !sessionId.isBlank()) {
                o.args.put("sessionId", sessionId);
            }
            // 兜底保留 todo_description，供 FileWriteTool 自动命名时取用
            o.args.put("todo_description", todo.getDescription());
            o.args.put("instructions", state.instructions());
            return o;
        } catch (Exception e) {
            log.warn("ToolNode: LLM 决策调用失败，将回退旧遍历逻辑: {}", e.getMessage());
            return null;
        }
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

        // 已有文件列表，提示 LLM 不要重复写
        Map<String, String> files = state.files();
        if (files != null && !files.isEmpty()) {
            sb.append("已有文件:\n");
            for (String name : files.keySet()) {
                sb.append("- ").append(name).append("\n");
            }
            sb.append("\n");
        }

        sb.append("请基于以上上下文，选择合适的工具并填写参数。")
          .append("如果当前 TODO 是保存文件，content 参数必须直接取自前序步骤产出的完整内容，")
          .append("不要总结、改写、截断或压缩；")
          .append("如无前序产出且当前任务必须有内容参数，请不调用任何工具。");
        return sb.toString();
    }

    /** file_write 成功时把 content 落盘到 files 通道 */
    private void handleFileWriteSideEffect(ToolProvider tool, Object result, DeepAgentState state,
                                           TodoItem todo, Map<String, String> files) {
        if (!"file_write".equals(tool.name()) || !(result instanceof Map)) {
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap = (Map<String, Object>) result;
        if (!Boolean.TRUE.equals(resultMap.get("success"))) {
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
    }

    /** 构造 TOOL_CALL 事件的 data，新增 toolName/arguments 字段 */
    private Map<String, Object> buildCallData(TodoItem todo, List<ToolProvider> candidates,
                                              ToolProvider chosen, Map<String, Object> args,
                                              boolean matched, String reason) {
        Map<String, Object> data = new HashMap<>();
        data.put("todo", todo);
        data.put("tools", candidates.stream().map(ToolProvider::name).toList());
        data.put("matched", matched);
        if (chosen != null) {
            data.put("toolName", chosen.name());
            data.put("arguments", args);
            // file_write 时透传 fileName 给前端切设计模式（前端 useAgentSSE 匹配此字段）
            if ("file_write".equals(chosen.name()) && args.get("filename") instanceof String fn && !fn.isBlank()) {
                data.put("fileName", fn);
            }
        }
        if (reason != null) {
            data.put("reason", reason);
        }
        return data;
    }

    /** LLM 决策结果载体 */
    private static class LlmDecisionOutcome {
        ToolProvider chosen;
        Map<String, Object> args = new HashMap<>();
        String failReason;
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
            case "internet_search":
                return desc.matches(".*\\b(search the (web|internet)|web search|google)\\b.*")
                        || desc.contains("网络搜索") || desc.contains("互联网搜索")
                        || desc.contains("搜索引擎") || desc.contains("最新新闻")
                        || desc.contains("实时数据") || desc.contains("实时信息")
                        || desc.contains("天气") || desc.contains("股价") || desc.contains("汇率")
                        || (desc.contains("搜索") && !desc.contains("搜索文件") && !desc.contains("文件搜索"));
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
            default:
                return false;
        }
    }

    // ========== 重试逻辑 ==========

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
