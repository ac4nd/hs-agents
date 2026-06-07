package com.hypersense.boot.framework.agents.sandbox.factory;

import com.hypersense.boot.framework.agents.config.AgentProperties;
import com.hypersense.boot.framework.agents.sandbox.LocalSandbox;
import com.hypersense.boot.framework.agents.sandbox.Sandbox;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;

/**
 * 本地沙箱工厂
 * <p>
 * 为每个会话创建独立的 LocalSandbox 实例，工作目录隔离为 baseWorkDir/{sessionId}/
 * </p>
 *
 * @author Claude
 * @since 2026/5/20
 */
@Slf4j
public class LocalSandboxFactory implements SandboxFactory {

    private final AgentProperties agentProperties;
    private final Path baseWorkDir;

    public LocalSandboxFactory(AgentProperties agentProperties) {
        this.agentProperties = agentProperties;
        AgentProperties.SandboxConfig config = agentProperties.getTools().getSandbox();
        String configuredWorkDir = config.getWorkDir();
        if (configuredWorkDir != null && !configuredWorkDir.isBlank()) {
            this.baseWorkDir = Path.of(configuredWorkDir);
        } else {
            this.baseWorkDir = Path.of(System.getProperty("java.io.tmpdir"), "agent-sandbox");
        }
    }

    @Override
    public Sandbox create(String sessionId) {
        Path sessionWorkDir = baseWorkDir.resolve(sessionId);
        log.info("LocalSandboxFactory: 为会话 [{}] 创建沙箱，workDir={}", sessionId, sessionWorkDir);
        LocalSandbox sandbox = new LocalSandbox(agentProperties, sessionWorkDir);
        sandbox.initialize();
        return sandbox;
    }
}
