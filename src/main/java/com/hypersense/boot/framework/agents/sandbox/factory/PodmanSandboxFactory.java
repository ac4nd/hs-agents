package com.hypersense.boot.framework.agents.sandbox.factory;

import com.hypersense.boot.framework.agents.config.AgentProperties;
import com.hypersense.boot.framework.agents.sandbox.PodmanSandbox;
import com.hypersense.boot.framework.agents.sandbox.Sandbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Podman 容器沙箱工厂
 * <p>
 * 为每个 Agent 会话创建独立的 Podman 容器沙箱实例。
 * 容器名称格式：agent-sandbox-{sessionId}
 * </p>
 *
 * @author Claude
 * @since 2026/5/20
 */
@Slf4j
@RequiredArgsConstructor
public class PodmanSandboxFactory implements SandboxFactory {

    private final AgentProperties agentProperties;

    @Override
    public Sandbox create(String sessionId) {
        log.info("PodmanSandboxFactory: 创建 Podman 沙箱，sessionId=[{}]", sessionId);
        PodmanSandbox sandbox = new PodmanSandbox(agentProperties, sessionId);
        sandbox.initialize();
        return sandbox;
    }
}
