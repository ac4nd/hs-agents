package com.hypersense.boot.framework.agents.sandbox.factory;

import com.hypersense.boot.framework.agents.config.AgentProperties;
import com.hypersense.boot.framework.agents.sandbox.DockerSandbox;
import com.hypersense.boot.framework.agents.sandbox.Sandbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Docker 容器沙箱工厂
 * <p>
 * 为每个 Agent 会话创建独立的 Docker 容器沙箱实例。
 * 容器名称格式：agent-sandbox-{sessionId}
 * </p>
 *
 * @author Claude
 * @since 2026/5/20
 */
@Slf4j
@RequiredArgsConstructor
public class DockerSandboxFactory implements SandboxFactory {

    private final AgentProperties agentProperties;

    @Override
    public Sandbox create(String sessionId) {
        log.info("DockerSandboxFactory: 创建 Docker 沙箱，sessionId=[{}]", sessionId);
        DockerSandbox sandbox = new DockerSandbox(agentProperties, sessionId);
        sandbox.initialize();
        return sandbox;
    }
}
