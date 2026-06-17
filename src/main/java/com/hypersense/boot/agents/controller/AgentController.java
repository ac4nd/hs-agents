package com.hypersense.boot.agents.controller;

import com.hypersense.boot.common.result.Result;
import com.hypersense.boot.framework.agents.form.AgentSessionForm;
import com.hypersense.boot.framework.agents.form.ApprovalRequest;
import com.hypersense.boot.framework.agents.model.InterruptContext;
import com.hypersense.boot.framework.agents.model.TodoItem;
import com.hypersense.boot.agents.service.AgentService;
import com.hypersense.boot.framework.agents.vo.AgentSessionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * AI Agent 控制器
 *
 * @author Claude
 * @since 2026/5/15
 */
@Tag(name = "AI智能体")
@RestController
@RequestMapping("/api/v1/agent")
@RequiredArgsConstructor
@Slf4j
public class AgentController {

    private final AgentService agentService;

    @Operation(summary = "创建 Agent 会话")
    @PostMapping("/sessions")
    public Result<AgentSessionVO> createSession(@RequestBody @Valid AgentSessionForm form) {
        return Result.success(agentService.createSession(form));
    }

    @Operation(summary = "同步执行 Agent")
    @PostMapping("/sessions/{sessionId}/run")
    public Result<AgentSessionVO> execute(@PathVariable String sessionId,
                                          @RequestParam String input) {
        return Result.success(agentService.execute(sessionId, input));
    }

    @Operation(summary = "SSE 流式执行 Agent")
    @GetMapping(value = "/sessions/{sessionId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamExecute(@PathVariable String sessionId,
                                    @RequestParam String input) {
        return agentService.streamExecute(sessionId, input);
    }

    @Operation(summary = "查询会话状态")
    @GetMapping("/sessions/{sessionId}")
    public Result<AgentSessionVO> getSession(@PathVariable String sessionId) {
        return Result.success(agentService.getSession(sessionId));
    }

    @Operation(summary = "查询 TODO 列表")
    @GetMapping("/sessions/{sessionId}/todos")
    public Result<List<TodoItem>> getTodos(@PathVariable String sessionId) {
        return Result.success(agentService.getTodos(sessionId));
    }

    @Operation(summary = "查询产物文件")
    @GetMapping("/sessions/{sessionId}/files")
    public Result<Map<String, String>> getFiles(@PathVariable String sessionId) {
        return Result.success(agentService.getFiles(sessionId));
    }

    // ========== HITL 审批端点 ==========

    @Operation(summary = "提交人工审批", description = "当会话因 HITL 暂停时，提交审批决策（APPROVED/REJECTED/MODIFIED）恢复执行")
    @PostMapping("/sessions/{sessionId}/approve")
    public Result<AgentSessionVO> submitApproval(@PathVariable String sessionId,
                                                   @RequestBody @Valid ApprovalRequest request) {
        return Result.success(agentService.submitApproval(sessionId, request));
    }

    @Operation(summary = "获取中断上下文", description = "查询当前 HITL 中断的上下文信息")
    @GetMapping("/sessions/{sessionId}/interrupt-context")
    public Result<InterruptContext> getInterruptContext(@PathVariable String sessionId) {
        return Result.success(agentService.getInterruptContext(sessionId));
    }

    @Operation(summary = "当前用户会话列表", description = "查询当前登录用户的所有 Agent 会话（从 PostgreSQL 查询）")
    @GetMapping("/sessions")
    public Result<java.util.List<AgentSessionVO>> listSessions() {
        return Result.success(agentService.listSessions());
    }

    @Operation(summary = "删除会话", description = "删除指定的 Agent 会话")
    @DeleteMapping("/sessions/{sessionId}")
    public Result<Void> deleteSession(@PathVariable String sessionId) {
        agentService.deleteSession(sessionId);
        return Result.success();
    }
}
