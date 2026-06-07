package com.hypersense.boot.framework.agents.sandbox;

import com.github.dockerjava.api.DockerClient;
import com.hypersense.boot.framework.agents.config.AgentProperties;
import lombok.extern.slf4j.Slf4j;

/**
 * Docker 容器沙箱
 * <p>
 * 通过 Docker Engine API 创建和管理容器实例。支持 Docker Desktop、
 * Docker Engine 以及所有兼容 Docker API 的运行时。
 * </p>
 *
 * <h3>连接方式（按优先级）：</h3>
 * <ol>
 *   <li>配置文件指定的 socketPath</li>
 *   <li>环境变量 DOCKER_HOST</li>
 *   <li>默认 Unix socket: unix:///var/run/docker.sock</li>
 * </ol>
 *
 * @author Claude
 * @since 2026/5/20
 */
@Slf4j
public class DockerSandbox extends AbstractContainerSandbox {

    public DockerSandbox(AgentProperties agentProperties, String sessionId) {
        super(agentProperties, sessionId);
    }

    @Override
    public String type() {
        return "docker";
    }

    @Override
    protected DockerClient createDockerClient() {
        return buildDockerClient(resolveDockerHost(), "Docker");
    }

    /**
     * 解析 Docker daemon 地址
     */
    private String resolveDockerHost() {
        // 1. 配置文件指定
        String configured = containerConfig.getSocketPath();
        if (configured != null && !configured.isBlank()) {
            return configured;
        }

        // 2. 环境变量
        String envHost = System.getenv("DOCKER_HOST");
        if (envHost != null && !envHost.isBlank()) {
            return envHost;
        }

        // 3. 默认值
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return "tcp://localhost:2375";
        }
        return "unix:///var/run/docker.sock";
    }
}
