package com.hypersense.boot.agents.service;

import com.hypersense.boot.framework.agents.form.AgentSessionForm;
import com.hypersense.boot.framework.agents.form.ApprovalRequest;
import com.hypersense.boot.framework.agents.form.SessionMetaForm;
import com.hypersense.boot.framework.agents.model.InterruptContext;
import com.hypersense.boot.framework.agents.model.TodoItem;
import com.hypersense.boot.framework.agents.vo.AgentSessionVO;
import com.hypersense.boot.framework.agents.vo.AttachmentVO;
import com.hypersense.boot.framework.agents.vo.LlmModelOptionVO;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * Agent 服务接口
 *
 * @author Claude
 * @since 2026/5/15
 */
public interface AgentService {

    /**
     * 创建 Agent 会话
     *
     * @param form 创建表单
     * @return 会话信息
     */
    AgentSessionVO createSession(AgentSessionForm form);

    /**
     * 同步执行 Agent
     *
     * @param sessionId 会话 ID
     * @param userInput 用户输入
     * @return 会话信息（含最终结果）
     */
    AgentSessionVO execute(String sessionId, String userInput);

    /**
     * SSE 流式执行 Agent
     *
     * @param sessionId 会话 ID
     * @param userInput 用户输入
     * @return SSE 发射器
     */
    SseEmitter streamExecute(String sessionId, String userInput);

    /**
     * SSE 流式执行 Agent（可切换模型 + 附件路径透传）。
     * <p>
     * modelConfigId 不为空且与当前 session 不一致时，先切换模型（invalidate graphCache + 更新 session），
     * 再执行本轮对话。modelConfigId 为空时维持当前模型。
     * </p>
     * <p>
     * attachmentPaths 非空时，会把路径作为「已上传附件」上下文提示注入到 userInput 之前，
     * 引导 Agent 通过 read_file 工具读取沙箱中的附件。
     * </p>
     *
     * @param sessionId       会话 ID
     * @param userInput       用户输入
     * @param modelConfigId   可选，sys_llm_model_config.id
     * @param attachmentPaths 可选，沙箱工作目录中的附件相对路径列表
     * @param designSystemId 可选，设计系统主键；非空时把对应 brandSpec/codeSpec 拼到 System 指令前。
     *                        配合 designSystemType 一起使用：type=personal 查 sys_design_system_config，
     *                        type=template 查 sys_design_system_config_template
     * @param designSystemType 可选，设计系统类型：personal（个人）/ template（官方模板）。缺省时按 personal 处理
     * @return SSE 发射器
     */
    SseEmitter streamExecute(String sessionId, String userInput, Long modelConfigId, List<String> attachmentPaths,
                             Long designSystemId, String designSystemType);

    /**
     * 切换会话绑定的 LLM 模型。
     * <p>
     * 校验 modelConfigId 可用后：invalidate graphCache + ChatModelRegistry，
     * 更新 session.modelConfigId，清空 historySummary（缓解跨模型漂移），持久化。
     * </p>
     *
     * @param sessionId     会话 ID
     * @param modelConfigId 目标模型配置 ID（sys_llm_model_config.id）
     * @return 更新后的会话
     */
    AgentSessionVO switchModel(String sessionId, Long modelConfigId);

    /**
     * 查询会话状态
     *
     * @param sessionId 会话 ID
     * @return 会话信息
     */
    AgentSessionVO getSession(String sessionId);

    /**
     * 查询 TODO 列表
     *
     * @param sessionId 会话 ID
     * @return TODO 列表
     */
    List<TodoItem> getTodos(String sessionId);

    /**
     * 查询产物文件
     *
     * @param sessionId 会话 ID
     * @return 文件名 → 内容映射
     */
    Map<String, String> getFiles(String sessionId);

    // ========== HITL 审批方法 ==========

    /**
     * 提交人工审批决策
     * <p>
     * 注入审批结果到图状态，从 checkpoint 恢复执行。
     * </p>
     *
     * @param sessionId 会话 ID
     * @param request   审批请求（决策 + 反馈 + 可选修改参数）
     * @return 更新后的会话信息
     */
    AgentSessionVO submitApproval(String sessionId, ApprovalRequest request);

    /**
     * 获取当前中断上下文
     *
     * @param sessionId 会话 ID
     * @return 中断上下文（包含节点名、当前 TODO、摘要等）
     */
    InterruptContext getInterruptContext(String sessionId);

    /**
     * 查询当前登录用户的全部会话列表.
     *
     * @return 会话列表
     */
    java.util.List<AgentSessionVO> listSessions();

    /**
     * 更新会话元数据（title / pinned 部分更新，回写 Redis）。
     * <p>
     * 任一参数为 null 表示不更新该字段；会话不存在或越权访问时抛业务异常。
     * </p>
     *
     * @param sessionId 会话 ID
     * @param title     新标题（可空，null 表示不更新）
     * @param pinned    新置顶状态（可空，null 表示不更新）
     * @return 更新后的会话
     */
    AgentSessionVO updateSessionMeta(String sessionId, String title, Boolean pinned);

    /**
     * 删除会话
     *
     * @param sessionId 会话 ID
     */
    void deleteSession(String sessionId);

    /**
     * 列出当前租户可用的 LLM 模型（前端切换模型下拉用）。
     *
     * @return 模型选项列表（不含敏感信息）
     */
    List<LlmModelOptionVO> listAvailableModels();

    /**
     * 上传会话附件到沙箱工作目录。
     * <p>
     * 把每个文件以 {@code uploads/<filename>} 写入 sessionId 对应的沙箱工作目录，
     * 同名文件自动加序号避免覆盖。前端在调用 streamExecute 时把返回的 path 列表
     * 通过 {@code attachmentPaths} 参数透传，后端会把路径作为上下文提示注入到 input。
     * </p>
     *
     * @param sessionId 会话 ID
     * @param files     multipart 文件列表
     * @return 写入沙箱后的附件信息
     */
    List<AttachmentVO> uploadAttachments(String sessionId, List<MultipartFile> files);

    /**
     * 列出当前会话沙箱工作目录下 {@code uploads/} 子目录的所有附件元信息。
     *
     * @param sessionId 会话 ID
     * @return 附件列表
     */
    List<AttachmentVO> listAttachments(String sessionId);

    /**
     * 读取沙箱工作目录下任意文件的字节内容（用于图片/文本/视频等预览下载）。
     * <p>
     * 路径相对 sessionId 对应的沙箱工作目录，底层 {@code LocalSandbox.readAllBytes} 通过
     * {@code resolveSecurePath} 防越界（normalize 后必须 startsWith workDir）。
     * </p>
     *
     * @param sessionId 会话 ID
     * @param path      沙箱工作目录内的相对路径（如 {@code uploads/logo.png}、{@code output/result.txt}）
     * @return 文件字节内容
     */
    byte[] readFileBytes(String sessionId, String path);

    /**
     * 向沙箱工作目录内指定文件写入文本内容（覆盖写入）。
     * <p>
     * 用于前端文本/代码 tab 编辑后保存。底层 {@code LocalSandbox.writeFile} 同样做越界防护。
     * </p>
     *
     * @param sessionId 会话 ID
     * @param path      沙箱工作目录内的相对路径
     * @param content   文本内容
     */
    void writeFileText(String sessionId, String path, String content);

    /**
     * @deprecated 改用 {@link #readFileBytes(String, String)}，旧方法仅做委托以兼容现有调用方。
     */
    @Deprecated
    byte[] readAttachmentBytes(String sessionId, String path);

    /**
     * 读取会话当前绑定的 LLM 模型配置 ID（sys_llm_model_config.id）。
     * <p>
     * 供沙箱内工具（如 FileReadTool）按模型能力（supports_vision 等）分流处理使用。
     * session 不存在或未绑定模型时返回 null。
     * </p>
     *
     * @param sessionId 会话 ID
     * @return modelConfigId，可能为 null
     */
    Long getSessionModelConfigId(String sessionId);
}
