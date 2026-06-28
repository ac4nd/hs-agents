package com.hypersense.boot.agents.controller;

import com.hypersense.boot.common.annotation.RepeatSubmit;
import com.hypersense.boot.common.result.Result;
import com.hypersense.boot.framework.agents.form.AgentSessionForm;
import com.hypersense.boot.framework.agents.form.ApprovalRequest;
import com.hypersense.boot.framework.agents.form.SessionMetaForm;
import com.hypersense.boot.framework.agents.model.InterruptContext;
import com.hypersense.boot.framework.agents.model.TodoItem;
import com.hypersense.boot.agents.service.AgentService;
import com.hypersense.boot.framework.agents.vo.AgentSessionVO;
import com.hypersense.boot.framework.agents.vo.AttachmentVO;
import com.hypersense.boot.framework.agents.vo.LlmModelOptionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Arrays;
import java.util.Collections;
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
                                    @RequestParam String input,
                                    @RequestParam(required = false) Long modelConfigId,
                                    @RequestParam(required = false) List<String> attachmentPaths,
                                    @RequestParam(required = false) Long designSystemId,
                                    @RequestParam(required = false) String designSystemType,
                                    @RequestParam(required = false, defaultValue = "false") Boolean designSystemEnabled) {
        return agentService.streamExecute(sessionId, input, modelConfigId, attachmentPaths, designSystemId, designSystemType, designSystemEnabled);
    }

    @Operation(summary = "上传会话附件",
            description = "把文件写入 sessionId 对应的沙箱工作目录 uploads/ 子目录，单文件上限 10MB，单次最多 5 个。返回的 path 列表可在 streamExecute 时通过 attachmentPaths 参数透传，引导 Agent 通过 read_file 工具读取。")
    @PostMapping(value = "/sessions/{sessionId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RepeatSubmit(expire = 5, message = "文件提交频繁，请5秒后再试")
    public Result<List<AttachmentVO>> uploadAttachments(@PathVariable String sessionId,
                                                        @RequestParam("files") MultipartFile[] files) {
        List<MultipartFile> fileList = files == null ? Collections.emptyList() : Arrays.asList(files);
        return Result.success(agentService.uploadAttachments(sessionId, fileList));
    }

    @Operation(summary = "查询会话附件列表", description = "返回沙箱工作目录 uploads/ 子目录下的所有附件元信息")
    @GetMapping("/sessions/{sessionId}/attachments")
    public Result<List<AttachmentVO>> listAttachments(@PathVariable String sessionId) {
        return Result.success(agentService.listAttachments(sessionId));
    }

    @Operation(summary = "下载会话沙箱文件二进制内容",
            description = "返回沙箱工作目录下任意文件（uploads/、output/ 等）的字节流，用于前端图片/PDF/视频/文本等预览。路径相对沙箱工作目录，底层做越界防护。")
    @GetMapping("/sessions/{sessionId}/attachments/file")
    public ResponseEntity<byte[]> downloadAttachment(@PathVariable String sessionId,
                                                     @RequestParam String path) {
        byte[] data = agentService.readFileBytes(sessionId, path);
        String fileName = path == null ? "attachment" : path.substring(path.lastIndexOf('/') + 1);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                .header(HttpHeaders.CACHE_CONTROL, CacheControl.maxAge(java.time.Duration.ofHours(1)).cachePublic().getHeaderValue())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(data);
    }

    @Operation(summary = "写入会话沙箱文件文本内容",
            description = "把前端 textarea 编辑的文本内容覆盖写入沙箱工作目录内指定文件，用于文本/代码 tab 保存。路径相对沙箱工作目录，底层做越界防护。")
    @PutMapping(value = "/sessions/{sessionId}/files/content", consumes = MediaType.TEXT_PLAIN_VALUE)
    public Result<Void> writeFileContent(@PathVariable String sessionId,
                                         @RequestParam String path,
                                         @RequestBody String content) {
        agentService.writeFileText(sessionId, path, content);
        return Result.success();
    }

    @Operation(summary = "查询可用模型列表", description = "返回当前租户下启用的 LLM 模型，供前端切换使用")
    @GetMapping("/models")
    public Result<List<LlmModelOptionVO>> listModels() {
        return Result.success(agentService.listAvailableModels());
    }

    @Operation(summary = "切换会话模型", description = "切换会话绑定的 LLM 模型（session 级，影响后续所有轮次）")
    @PostMapping("/sessions/{sessionId}/switch-model")
    public Result<AgentSessionVO> switchModel(@PathVariable String sessionId,
                                              @RequestParam Long modelConfigId) {
        return Result.success(agentService.switchModel(sessionId, modelConfigId));
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

    @Operation(summary = "当前用户会话列表",
            description = "查询当前登录用户的 Agent 会话列表。")
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

    @Operation(summary = "更新会话元数据", description = "部分更新会话的 title 和 pinned 状态（任一字段为 null 表示不更新），回写 Redis 实现跨设备同步")
    @PatchMapping("/sessions/{sessionId}/meta")
    public Result<AgentSessionVO> updateSessionMeta(@PathVariable String sessionId,
                                                     @RequestBody SessionMetaForm form) {
        return Result.success(agentService.updateSessionMeta(sessionId, form.getTitle(), form.getPinned()));
    }
}
