package com.hypersense.boot.framework.agents.sandbox;

import com.github.dockerjava.api.DockerClient;
import com.hypersense.boot.framework.agents.config.AgentProperties;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Podman 容器沙箱
 * <p>
 * 通过 Podman 的 Docker 兼容 API 创建和管理容器。
 * Podman v2+ 提供完整的 Docker Engine API 兼容层，
 * 因此可以直接使用 docker-java 库连接。
 * </p>
 *
 * <h3>连接方式（按优先级）：</h3>
 * <ol>
 *   <li>配置文件指定的 socketPath</li>
 *   <li>环境变量 CONTAINER_HOST 或 DOCKER_HOST</li>
 *   <li>自动检测 Podman socket（rootless → root）</li>
 * </ol>
 *
 * @author Claude
 * @since 2026/5/20
 */
@Slf4j
public class PodmanSandbox extends AbstractContainerSandbox {

    public PodmanSandbox(AgentProperties agentProperties, String sessionId) {
        super(agentProperties, sessionId);
    }

    @Override
    public String type() {
        return "podman";
    }

    @Override
    protected DockerClient createDockerClient() {
        return buildDockerClient(resolvePodmanHost(), "Podman");
    }

    /**
     * 解析 Podman socket 地址
     */
    private String resolvePodmanHost() {
        // 1. 配置文件指定
        String configured = containerConfig.getSocketPath();
        if (configured != null && !configured.isBlank()) {
            return configured;
        }

        // 2. 环境变量（Podman 推荐用 CONTAINER_HOST，也兼容 DOCKER_HOST）
        String envHost = System.getenv("CONTAINER_HOST");
        if (envHost != null && !envHost.isBlank()) {
            return envHost;
        }
        envHost = System.getenv("DOCKER_HOST");
        if (envHost != null && !envHost.isBlank()) {
            return envHost;
        }

        // 3. 自动检测 Podman socket
        return detectPodmanSocket();
    }

    /**
     * 自动检测 Podman socket 路径（rootless → root）
     */
    private String detectPodmanSocket() {
        String uid = detectUserId();

        // Rootless: /run/user/{uid}/podman/podman.sock
        if (uid != null && socketExists("/run/user/" + uid + "/podman/podman.sock")) {
            log.debug("PodmanSandbox: 检测到 rootless Podman socket");
            return "unix:///run/user/" + uid + "/podman/podman.sock";
        }

        // XDG_RUNTIME_DIR
        String xdgRuntime = System.getenv("XDG_RUNTIME_DIR");
        if (xdgRuntime != null && socketExists(xdgRuntime + "/podman/podman.sock")) {
            log.debug("PodmanSandbox: 检测到 XDG Podman socket");
            return "unix://" + xdgRuntime + "/podman/podman.sock";
        }

        // Root: /run/podman/podman.sock
        if (socketExists("/run/podman/podman.sock")) {
            log.debug("PodmanSandbox: 检测到 root Podman socket");
            return "unix:///run/podman/podman.sock";
        }

        // 默认回退
        if (uid != null) {
            return "unix:///run/user/" + uid + "/podman/podman.sock";
        }
        return "unix:///run/podman/podman.sock";
    }

    private String detectUserId() {
        try {
            return String.valueOf(new com.sun.security.auth.module.UnixSystem().getUid());
        } catch (Throwable e) {
            log.debug("PodmanSandbox: UnixSystem 不可用，使用默认 UID");
            return "1000";
        }
    }

    private boolean socketExists(String path) {
        try {
            return Files.exists(Path.of(path));
        } catch (Exception e) {
            log.debug("PodmanSandbox: socket 检测异常 path={}", path, e);
            return false;
        }
    }
}
