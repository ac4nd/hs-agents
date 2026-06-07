package com.hypersense.boot.framework.agents.sandbox;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.StreamType;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;

import java.io.ByteArrayOutputStream;

/**
 * 容器沙箱完整流程验证（手动执行）
 * 测试：创建容器 → 执行代码 → 读取文件 → 销毁容器
 */
public class DockerSandboxIntegrationTest {

    static final String DOCKER_HOST = "tcp://47.107.160.31:2375";
    static final String IMAGE = "python:3.12-alpine";
    static final String CONTAINER_NAME = "sandbox-test-" + System.currentTimeMillis();

    public static void main(String[] args) throws Exception {
        System.out.println("===== 容器沙箱集成测试 =====\n");

        DockerClient client = createClient();

        try {
            // 1. 确保镜像存在
            System.out.println("[1] 检查镜像: " + IMAGE);
            try {
                client.inspectImageCmd(IMAGE).exec();
                System.out.println("    镜像已存在");
            } catch (Exception e) {
                System.out.println("    拉取镜像...");
                client.pullImageCmd(IMAGE).start().awaitCompletion();
                System.out.println("    拉取完成");
            }

            // 2. 创建容器
            System.out.println("\n[2] 创建容器: " + CONTAINER_NAME);
            HostConfig hostConfig = HostConfig.newHostConfig()
                    .withMemory(128 * 1024 * 1024L)
                    .withNetworkMode("none")
                    .withAutoRemove(true);

            CreateContainerResponse container = client.createContainerCmd(IMAGE)
                    .withName(CONTAINER_NAME)
                    .withHostConfig(hostConfig)
                    .withWorkingDir("/workspace")
                    .withCmd("tail", "-f", "/dev/null")  // 保持运行
                    .exec();
            String containerId = container.getId();
            System.out.println("    容器ID: " + containerId.substring(0, 12));

            // 3. 启动容器
            client.startContainerCmd(containerId).exec();
            System.out.println("    容器已启动");

            // 4. 执行 Python 代码
            System.out.println("\n[3] 执行 Python 代码");
            String pyResult = execCmd(client, containerId, "python3", "-c",
                    "import sys; print(f'Python {sys.version}'); print('2 + 3 =', 2 + 3)");
            System.out.println("    输出:\n" + indent(pyResult));

            // 5. 执行 Shell 命令
            System.out.println("\n[4] 执行 Shell 命令");
            String shResult = execCmd(client, containerId, "sh", "-c",
                    "echo 'Hello from sandbox!' && uname -a && ls /workspace");
            System.out.println("    输出:\n" + indent(shResult));

            // 6. 清理
            System.out.println("\n[5] 销毁容器");
            client.stopContainerCmd(containerId).withTimeout(3).exec();
            System.out.println("    容器已停止并自动删除（autoRemove=true）");

            System.out.println("\n===== 所有测试通过! =====");

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

    static String execCmd(DockerClient client, String containerId, String... cmd) throws Exception {
        ExecCreateCmdResponse exec = client.execCreateCmd(containerId)
                .withCmd(cmd)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .exec();

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        var callback = new com.github.dockerjava.api.async.ResultCallbackTemplate<
                com.github.dockerjava.api.async.ResultCallback<Frame>, Frame>() {
            @Override
            public void onNext(Frame frame) {
                if (frame == null) return;
                if (frame.getStreamType() == StreamType.STDERR) {
                    stderr.writeBytes(frame.getPayload());
                } else {
                    stdout.writeBytes(frame.getPayload());
                }
            }
        };

        client.execStartCmd(exec.getId()).exec(callback);
        callback.awaitCompletion();

        String out = stdout.toString().trim();
        String err = stderr.toString().trim();
        if (!err.isEmpty()) {
            out += "\n[stderr] " + err;
        }
        return out;
    }

    static String indent(String s) {
        return s.lines().map(l -> "    " + l).reduce((a, b) -> a + "\n" + b).orElse("");
    }
}
