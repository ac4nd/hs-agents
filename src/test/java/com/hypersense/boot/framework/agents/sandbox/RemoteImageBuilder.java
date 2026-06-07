package com.hypersense.boot.framework.agents.sandbox;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.BuildResponseItem;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 远程构建 Docker 镜像
 * 通过 docker-java API 将 Dockerfile 发送到远程 Docker daemon 构建
 */
public class RemoteImageBuilder {

    static final String DOCKER_HOST = "tcp://47.107.160.31:2375";
    static final String IMAGE_TAG = "godlikeagents/sandbox:1.0.0";

    public static void main(String[] args) throws Exception {
        System.out.println("===== 远程构建沙箱镜像 =====");
        System.out.println("Docker Host: " + DOCKER_HOST);
        System.out.println("目标镜像:   " + IMAGE_TAG);
        System.out.println();

        DockerClient client = createClient();

        try {
            // 读取 Dockerfile
            String dockerfile = loadDockerfile();
            System.out.println("[1] Dockerfile 已加载 (" + dockerfile.split("\n").length + " 行)");

            // 构建 tar 上下文（仅包含 Dockerfile）
            byte[] buildContext = createTarContext(dockerfile);
            System.out.println("[2] 构建上下文已创建 (" + (buildContext.length / 1024) + " KB)");

            // 远程构建
            System.out.println("[3] 开始远程构建（可能需要几分钟拉取基础镜像和安装依赖）...\n");

            AtomicBoolean success = new AtomicBoolean(true);
            long start = System.currentTimeMillis();

            try (InputStream contextStream = new ByteArrayInputStream(buildContext)) {
                var callback = new com.github.dockerjava.api.async.ResultCallbackTemplate<
                        com.github.dockerjava.api.async.ResultCallback<BuildResponseItem>, BuildResponseItem>() {
                    @Override
                    public void onNext(BuildResponseItem item) {
                        if (item == null) return;
                        String stream = item.getStream();
                        if (stream != null && !stream.isBlank()) {
                            System.out.print("  " + stream);
                        }
                        String error = item.getError();
                        if (error != null && !error.isBlank()) {
                            System.out.println("  [ERROR] " + error);
                            success.set(false);
                        }
                    }
                };

                client.buildImageCmd(contextStream)
                        .withTags(java.util.Set.of(IMAGE_TAG))
                        .withDockerfilePath("Dockerfile")
                        .exec(callback);

                callback.awaitCompletion();
            }

            long elapsed = (System.currentTimeMillis() - start) / 1000;
            System.out.println();

            if (success.get()) {
                System.out.println("[4] 构建完成! 耗时: " + elapsed + "s");

                // 验证镜像
                var inspect = client.inspectImageCmd(IMAGE_TAG).exec();
                System.out.println("[5] 镜像验证:");
                System.out.println("    ID:     " + inspect.getId().substring(0, 19));
                System.out.println("    大小:   " + (inspect.getSize() / 1024 / 1024) + " MB");
                System.out.println("    标签:   " + IMAGE_TAG);
                System.out.println("\n===== 构建成功! =====");
            } else {
                System.out.println("[!] 构建失败，请检查上方错误信息");
            }

        } finally {
            client.close();
        }
    }

    static DockerClient createClient() {
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(DOCKER_HOST)
                .build();
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .build();
        return DockerClientImpl.getInstance(config, httpClient);
    }

    static String loadDockerfile() {
        // 内嵌 Dockerfile（与 docker/sandbox/Dockerfile 一致）
        return """
# =============================================================================
# GodlikeAgents 多语言沙箱镜像
# 基于 Alpine Linux，预装 Python / Node.js / Bash 等常用运行时
# =============================================================================

# ---- 阶段 1：安装依赖 ----
FROM python:3.12-alpine AS builder

RUN apk add --no-cache --virtual .build-deps \\
    gcc g++ musl-dev libffi-dev openssl-dev

RUN pip install --no-cache-dir --prefix=/install \\
    numpy==2.2.6 \\
    pandas==2.2.3 \\
    requests==2.32.3 \\
    httpx==0.28.1 \\
    pyyaml==6.0.2

# ---- 阶段 2：最终镜像 ----
FROM python:3.12-alpine

LABEL maintainer="GodlikeAgents"
LABEL description="GodlikeAgents 多语言沙箱镜像"
LABEL version="1.0.0"

ENV TZ=Asia/Shanghai
RUN apk add --no-cache tzdata && \\
    cp /usr/share/zoneinfo/$TZ /etc/localtime && \\
    echo "$TZ" > /etc/timezone && \\
    apk del tzdata

RUN apk add --no-cache \\
    bash \\
    nodejs \\
    npm \\
    curl \\
    wget \\
    git \\
    ca-certificates \\
    util-linux \\
    procps \\
    coreutils

COPY --from=builder /install /usr/local

RUN npm install -g \\
    mathjs \\
    axios \\
    dayjs \\
    && npm cache clean --force

RUN addgroup -S sandbox && \\
    adduser -S -G sandbox -h /workspace -s /bin/bash sandbox && \\
    chown sandbox:sandbox /workspace

WORKDIR /workspace
USER sandbox

HEALTHCHECK --interval=60s --timeout=5s --retries=1 CMD \\
    python3 -c "print('python ok')" && \\
    node -e "console.log('node ok')" && \\
    bash -c "echo 'bash ok'" || exit 1
""";
    }

    static byte[] createTarContext(String dockerfile) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        var tos = new org.apache.commons.compress.archivers.tar.TarArchiveOutputStream(baos);
        byte[] content = dockerfile.getBytes(StandardCharsets.UTF_8);
        var entry = new org.apache.commons.compress.archivers.tar.TarArchiveEntry("Dockerfile");
        entry.setSize(content.length);
        tos.putArchiveEntry(entry);
        tos.write(content);
        tos.closeArchiveEntry();
        tos.close();
        return baos.toByteArray();
    }
}
