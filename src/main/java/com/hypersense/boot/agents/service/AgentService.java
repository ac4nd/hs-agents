package com.hypersense.boot.agents.service;

import com.hypersense.boot.framework.agents.form.AgentSessionForm;
import com.hypersense.boot.framework.agents.form.ApprovalRequest;
import com.hypersense.boot.framework.agents.model.InterruptContext;
import com.hypersense.boot.framework.agents.model.TodoItem;
import com.hypersense.boot.framework.agents.vo.AgentSessionVO;
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
}
