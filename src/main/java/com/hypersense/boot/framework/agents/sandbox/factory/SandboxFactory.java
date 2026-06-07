package com.hypersense.boot.framework.agents.sandbox.factory;

import com.hypersense.boot.framework.agents.sandbox.Sandbox;

/**
 * Sandbox 工厂接口
 * <p>
 * 为每个 agent 会话创建独立的 Sandbox 实例。
 * 实现类根据配置（local/remote/custom）创建对应类型的 Sandbox。
 * </p>
 *
 * @author Claude
 * @since 2026/5/20
 */
public interface SandboxFactory {

    /**
     * 为指定会话创建一个新的 Sandbox 实例
     *
     * @param sessionId 会话 ID，用于隔离工作目录
     * @return 新创建的 Sandbox 实例（已调用 initialize()）
     */
    Sandbox create(String sessionId);
}
