package com.hypersense.boot.framework.agents.engine.node;

import com.hypersense.boot.framework.agents.engine.SubAgentEventBus;
import com.hypersense.boot.framework.agents.engine.validator.FinalizeAuditor;
import com.hypersense.boot.framework.agents.enums.AgentEventType;
import com.hypersense.boot.framework.agents.model.AgentEvent;
import com.hypersense.boot.framework.agents.model.DeepAgentState;
import com.hypersense.boot.framework.agents.model.TodoItem;
import com.hypersense.boot.framework.agents.serializer.AttachmentContext;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 最终汇总节点
 * <p>
 * 汇总所有 TODO 执行结果，调用 LLM 生成最终响应。
 * 完成后推送 {@link AgentEventType#FINAL_RESPONSE} 事件（data 带 finalResponse 文本）。
 * </p>
 *
 * @author Claude
 * @since 2026/5/15
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FinalizeNode implements NodeAction<DeepAgentState> {

    private final ChatModel chatModel;
    private final AttachmentContext attachmentContext;

    private static final String FINALIZE_SYSTEM_PROMPT = """
            你是一个结果汇总专家。根据任务执行情况，生成简洁、清晰的最终报告。

            报告要求：
            1. 总结完成的任务
            2. 列出关键发现和结果
            3. 如有未完成或失败的任务，说明原因

            路径引用规则（必须遵守）：
            1. 文件保存位置必须使用上下文中提供的真实路径（如 "workspace/{sessionId}/uploads/{filename}"），严禁凭空编造
            2. 严禁编造路径前缀，例如禁止使用 "/home/user/"、"/tmp/"、"/var/workspace/"、"/root/" 等 LLM 训练数据中的常见路径
            3. 严禁将 Windows 路径（如 "D:/..."、"C:\\\\"）改写为 Linux 风格或反之；只引用工具结果中实际返回的 relativePath / workspacePath 字段
            4. 如果上下文未提供明确路径，使用 "工作空间" 作为占位描述（如 "已保存到工作空间"），不要编造具体路径
            5. 引用示例：
               - 正确：「文件已保存至 workspace/abc123/uploads/pet_adoption.html」（与工具返回的 workspacePath 一致）
               - 正确：「已保存到工作空间，文件名 pet_adoption.html」（无具体路径时使用占位）
               - 错误：「文件已保存至 /home/user/pet_adoption.html」（编造 Linux 路径）
               - 错误：「文件已保存至 /tmp/pet_adoption.html」（编造系统临时目录）

            总结生成规则（强制）：
            1. 所有路径必须来自 todo.result.workspacePath 或 todo.result.relativePath
            2. 所有操作结果（"已保存/已执行/已调用"）必须有对应工具调用记录
            3. 禁止编造路径前缀：/home/user/、/tmp/、/var/workspace/、/root/、~/
            4. 禁止输出 Windows 绝对路径（如 D:/...）
            5. 若上下文未提供明确信息，使用"工作空间"作为占位，禁止编造具体路径
            6. 系统会自动审计输出，违反规则的总结会被标记警告并通知用户
            """;

    @Override
    public Map<String, Object> apply(DeepAgentState state) {
        log.info("FinalizeNode: 开始汇总结果");

        // 短路：PlanNode 已直接回复（如简单问候），跳过 LLM 重新汇总，避免重复输出
        Optional<String> existing = state.finalResponse();
        if (existing.isPresent() && !existing.get().isBlank()) {
            String direct = existing.get();
            log.info("FinalizeNode: 复用 PlanNode 的直接回复（跳过汇总）");
            return Map.of(
                    DeepAgentState.FINAL_RESPONSE, direct,
                    DeepAgentState.MESSAGES, AiMessage.from(direct)
            );
        }

        List<TodoItem> todos = state.todos();
        String todoSummary = todos.stream()
                .map(t -> String.format("- [%s] %s: %s",
                        t.getStatus().getLabel(),
                        t.getDescription(),
                        t.getResult() != null ? t.getResult() : ""))
                .collect(Collectors.joining("\n"));

        // 产物文件摘要：拼出可引用的相对路径，供 LLM 在报告中准确引用（避免编造 /home/user/ 等路径）
        String sessionId = state.sessionId();
        Map<String, String> files = state.files();
        String filesSummary;
        if (files.isEmpty()) {
            filesSummary = "（无产物文件）";
        } else {
            // state.files() 的 key 是 filename，value 是文件内容；此处仅取 key 拼路径，避免把内容塞进 prompt
            String sid = (sessionId == null || sessionId.isBlank()) ? "default" : sessionId;
            filesSummary = files.keySet().stream()
                    .map(fn -> "- " + fn + " (工作空间路径: workspace/" + sid + "/uploads/" + fn + ")")
                    .collect(Collectors.joining("\n"));
        }

        String userPrompt = String.format("""
                原始指令：%s

                任务执行情况：
                %s

                产物文件：
                %s

                请生成最终报告。
                """, state.instructions(), todoSummary, filesSummary);

        List<ChatMessage> messages = List.of(
                SystemMessage.from(FINALIZE_SYSTEM_PROMPT),
                attachmentContext
                        .buildMultimodal(state.sessionId(), userPrompt, state.attachmentPaths())
                        .orElseGet(() -> UserMessage.from(userPrompt))
        );

        String finalResponse;
        try {
            ChatResponse response = attachmentContext.chatWithVisionFallback(
                    chatModel, messages, state.sessionId(), userPrompt);
            finalResponse = response.aiMessage().text();
            log.info("FinalizeNode: 最终报告生成完成");
        } catch (Exception e) {
            finalResponse = "报告生成失败: " + e.getMessage();
            log.error("FinalizeNode: 报告生成异常", e);
        }

        // 审计 LLM 输出（检测编造路径前缀、Windows 绝对路径、未生成文件引用）
        try {
            java.util.Set<String> stateFilesKeys = files == null
                    ? java.util.Collections.emptySet()
                    : files.keySet();
            FinalizeAuditor.AuditResult auditResult = FinalizeAuditor.audit(finalResponse, stateFilesKeys);
            if (auditResult.hasWarnings()) {
                log.warn("Finalize 审计警告 sessionId={}: {}", sessionId, auditResult.joinedWarnings());
                java.util.Map<String, Object> payload = new java.util.HashMap<>();
                payload.put("sessionId", sessionId);
                payload.put("warnings", auditResult.getWarnings());
                emit(AgentEventType.AUDIT_WARNING, "审计警告", payload);
            }
        } catch (Exception ignored) {
            // 审计失败不影响主流程
        }

        // 流式推送 FINAL_RESPONSE（带 finalResponse 文本）
        var consumer = SubAgentEventBus.get();
        if (consumer != null) {
            AgentEvent event = AgentEvent.builder()
                    .type(AgentEventType.FINAL_RESPONSE)
                    .message("任务全部完成")
                    .data(Map.of("finalResponse", finalResponse))
                    .timestamp(System.currentTimeMillis())
                    .build();
            consumer.accept(event);
        }

        return Map.of(
                DeepAgentState.FINAL_RESPONSE, finalResponse,
                DeepAgentState.MESSAGES, AiMessage.from("任务全部完成。\n\n" + finalResponse)
        );
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
}

