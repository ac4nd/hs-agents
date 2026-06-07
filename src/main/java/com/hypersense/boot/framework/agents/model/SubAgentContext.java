package com.hypersense.boot.framework.agents.model;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

/**
 * 子 Agent 委派上下文
 * <p>
 * 从父 Agent 线程携带委派请求信息到子 Agent 执行路径。
 * 不可变值对象，仅用于方法间传递。
 * </p>
 *
 * @author Claude
 * @since 2026/5/23
 */
@Value
@Builder
public class SubAgentContext {

    /** 被调用的子 Agent 定义 */
    SubAgentDefinition definition;

    /** 具体任务描述（来自 TodoItem.description） */
    String taskDescription;

    /** 父 Agent 的 sessionId，用于派生子 sessionId */
    String parentSessionId;

    /** 父 Agent 的原始用户指令，用于上下文传递 */
    String parentInstructions;

    /** 当前递归深度（0 = 根 Agent，1 = 第一层子，2 = 上限） */
    int currentDepth;

    /** 同一会话中其他子 Agent 已完成的结果，用于上下文传递 */
    Map<String, String> previousSubAgentResults;
}
