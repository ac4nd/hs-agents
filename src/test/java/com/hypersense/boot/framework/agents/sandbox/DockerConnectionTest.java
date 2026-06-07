package com.hypersense.boot.framework.agents.sandbox;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Info;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;

/**
 * Docker 连通性验证（手动执行）
 */
public class DockerConnectionTest {

    public static void main(String[] args) {
        // 连接到远程 Docker
        String dockerHost = "tcp://47.107.160.31:2375";
        System.out.println("正在连接 Docker: " + dockerHost);

        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(dockerHost)
                .build();

        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .build();

        try (DockerClient client = DockerClientImpl.getInstance(config, httpClient)) {
            // ping
            client.pingCmd().exec();
            System.out.println("Ping: OK");

            // info
            Info info = client.infoCmd().exec();
            System.out.println("Docker 版本: " + info.getServerVersion());
            System.out.println("操作系统: " + info.getOperatingSystem());
            System.out.println("容器数: " + info.getContainers());
            System.out.println("镜像数: " + info.getImages());
            System.out.println("CPU 核心数: " + info.getNCPU());
            System.out.println("总内存: " + (info.getMemTotal() / 1024 / 1024) + " MB");

            System.out.println("\nDocker 连接成功!");
        } catch (Exception e) {
            System.err.println("Docker 连接失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
