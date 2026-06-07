package com.hypersense.boot.framework.agents.sandbox;

import com.hypersense.boot.framework.agents.config.AgentProperties;
import com.hypersense.boot.framework.agents.sandbox.factory.DockerSandboxFactory;
import com.hypersense.boot.framework.agents.sandbox.factory.LocalSandboxFactory;
import com.hypersense.boot.framework.agents.sandbox.factory.PodmanSandboxFactory;
import com.hypersense.boot.framework.agents.sandbox.factory.SandboxFactory;
import com.hypersense.boot.framework.agents.tool.impl.SandboxTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sandbox 自动配置
 * <p>
 * 通过 agent.tools.sandbox.type 切换工厂实现：
 * - local  → LocalSandboxFactory（本地子进程）
 * - remote → RemoteSandbox 工厂（远程云沙箱）
 * - custom → 容器沙箱工厂（根据 custom.runtime 路由到 Docker/Podman）
 * </p>
 * <p>
 * 每个会话通过 {@link SandboxManager} 获取独立的 Sandbox 实例（Thread-scoped）。
 * </p>
 *
 * @author Claude
 * @since 2026/5/19
 */
@Slf4j
@Configuration
public class SandboxAutoConfiguration {

    @Bean
    @ConditionalOnProperty(name = "agent.tools.sandbox.type",
            havingValue = "local",
            matchIfMissing = true)
    public SandboxFactory localSandboxFactory(AgentProperties agentProperties) {
        log.info("注册 LocalSandboxFactory（本地子进程沙箱工厂）");
        return new LocalSandboxFactory(agentProperties);
    }

    @Bean
    @ConditionalOnProperty(name = "agent.tools.sandbox.type",
            havingValue = "remote")
    public SandboxFactory remoteSandboxFactory(AgentProperties agentProperties) {
        AgentProperties.RemoteSandboxConfig remoteConfig = agentProperties.getTools().getSandbox().getRemote();
        log.info("注册 RemoteSandboxFactory（远程云沙箱工厂，provider={}）", remoteConfig.getProvider());
        return sessionId -> {
            RemoteSandbox sandbox = new RemoteSandbox(agentProperties, sessionId);
            sandbox.initialize();
            return sandbox;
        };
    }

    /**
     * 容器沙箱工厂：根据 custom.runtime 路由到 Docker 或 Podman
     */
    @Bean
    @ConditionalOnProperty(name = "agent.tools.sandbox.type",
            havingValue = "custom")
    public SandboxFactory customSandboxFactory(AgentProperties agentProperties) {
        AgentProperties.CustomSandboxConfig customConfig = agentProperties.getTools().getSandbox().getCustom();
        String runtime = customConfig.getRuntime();

        if ("podman".equalsIgnoreCase(runtime)) {
            log.info("注册 PodmanSandboxFactory（Podman 容器沙箱工厂，image={}）", customConfig.getImage());
            return new PodmanSandboxFactory(agentProperties);
        }

        log.info("注册 DockerSandboxFactory（Docker 容器沙箱工厂，image={}）", customConfig.getImage());
        return new DockerSandboxFactory(agentProperties);
    }

    /**
     * 注册 SandboxTool（仅在沙箱启用时）
     */
    @Bean
    @ConditionalOnProperty(name = "agent.tools.sandbox.enabled", havingValue = "true")
    public SandboxTool sandboxTool(AgentProperties agentProperties, SandboxManager sandboxManager) {
        log.info("注册 SandboxTool（沙箱工具）");
        return new SandboxTool(agentProperties, sandboxManager);
    }
}
