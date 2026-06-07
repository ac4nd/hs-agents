package com.hypersense.boot.framework.agents.sandbox;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.StreamType;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import com.hypersense.boot.framework.agents.config.AgentProperties;
import lombok.extern.slf4j.Slf4j;
import com.github.dockerjava.api.model.Bind;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 容器沙箱抽象基类
 * <p>
 * 使用 docker-java 的 {@link DockerClient} 实现所有 {@link Sandbox} 方法。
 * 子类仅需实现 {@link #createDockerClient()} 以连接不同的容器运行时（Docker / Podman）。
 * </p>
 *
 * <h3>容器生命周期：</h3>
 * <pre>
 * initialize() → createContainer → startContainer → [执行操作] → destroy() → stopContainer → removeContainer
 * </pre>
 *
 * @author Claude
 * @since 2026/5/20
 */
@Slf4j
public abstract class AbstractContainerSandbox extends Sandbox {

    /** 语言 → 执行命令模板 */
    private static final Map<String, String[]> LANGUAGE_COMMANDS = Map.of(
            "python", new String[]{"python3", "-c"},
            "python3", new String[]{"python3", "-c"},
            "javascript", new String[]{"node", "-e"},
            "js", new String[]{"node", "-e"},
            "node", new String[]{"node", "-e"},
            "shell", new String[]{"bash", "-c"},
            "bash", new String[]{"bash", "-c"},
            "sh", new String[]{"sh", "-c"}
    );

    /** 允许在容器内访问的路径前缀白名单 */
    private static final List<String> ALLOWED_PATH_PREFIXES = List.of("/workspace", "/tmp");

    /** readFile 单文件最大字节数（防止 OOM） */
    private static final int MAX_READ_FILE_BYTES = 10 * 1024 * 1024; // 10MB

    protected final AgentProperties.SandboxConfig sandboxConfig;
    protected final AgentProperties.CustomSandboxConfig containerConfig;
    protected final String sessionId;

    /** Docker/Podman 客户端（子类创建），volatile 保证多线程可见性 */
    protected volatile DockerClient dockerClient;

    /** 容器 ID（initialize 时赋值），volatile 保证多线程可见性 */
    protected volatile String containerId;

    /** 容器名称：agent-sandbox-{sessionId} */
    protected final String containerName;

    /** 容器内工作目录 */
    protected final String workspacePath;

    protected AbstractContainerSandbox(AgentProperties agentProperties, String sessionId) {
        if (agentProperties == null) {
            throw new IllegalArgumentException("agentProperties 不能为 null");
        }
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId 不能为空");
        }
        this.sandboxConfig = agentProperties.getTools().getSandbox();
        this.containerConfig = sandboxConfig.getCustom();
        this.sessionId = sessionId;
        this.containerName = "agent-sandbox-" + sessionId;
        this.workspacePath = containerConfig.getWorkspacePath();
    }

    /**
     * 子类实现：创建连接到对应容器运行时的 DockerClient
     */
    protected abstract DockerClient createDockerClient();

    /**
     * 构建 DockerClient 的公共逻辑（Docker/Podman 共用）
     *
     * @param host Docker/Podman daemon 地址（如 tcp://host:2375, unix:///var/run/docker.sock）
     * @param label 日志标签（如 "Docker" / "Podman"）
     * @return 已验证连接的 DockerClient
     */
    protected DockerClient buildDockerClient(String host, String label) {
        log.info("ContainerSandbox: 连接 {} daemon，host=[{}]", label, host);

        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(host)
                .build();

        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .build();

        DockerClient client = DockerClientImpl.getInstance(config, httpClient);

        try {
            client.pingCmd().exec();
            log.info("ContainerSandbox: {} daemon 连接成功", label);
        } catch (DockerException e) {
            throw new RuntimeException("无法连接 " + label + " daemon (" + host + "): " + e.getMessage(), e);
        }

        return client;
    }

    // ========== Sandbox 方法实现 ==========

    @Override
    public void initialize() {
        log.info("ContainerSandbox: 初始化容器沙箱，sessionId=[{}]，image=[{}]，runtime=[{}]",
                sessionId, containerConfig.getImage(), type());

        try {
            this.dockerClient = createDockerClient();
            ensureImageExists();
            createAndStartContainer();
            log.info("ContainerSandbox: 容器已就绪，containerId=[{}]，containerName=[{}]",
                    abbreviateId(containerId), containerName);
        } catch (Exception e) {
            log.error("ContainerSandbox: 容器初始化失败，sessionId=[{}]", sessionId, e);
            cleanupOnFailure();
            throw new RuntimeException("容器沙箱初始化失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void destroy() {
        log.info("ContainerSandbox: 销毁容器沙箱，sessionId=[{}]，containerName=[{}]",
                sessionId, containerName);

        DockerClient client = this.dockerClient;
        String cId = this.containerId;

        // 立即清空引用，防止并发重复销毁
        this.containerId = null;
        this.dockerClient = null;

        if (client == null || cId == null) {
            return;
        }

        try {
            // 停止容器（忽略已停止的错误）
            try {
                client.stopContainerCmd(cId).withTimeout(5).exec();
            } catch (DockerException e) {
                log.debug("ContainerSandbox: 停止容器时异常（可能已停止），忽略: {}", e.getMessage());
            }

            // 删除容器
            try {
                client.removeContainerCmd(cId)
                        .withForce(true)
                        .withRemoveVolumes(true)
                        .exec();
            } catch (NotFoundException e) {
                log.debug("ContainerSandbox: 容器已不存在，忽略");
            }
        } catch (Exception e) {
            log.warn("ContainerSandbox: 销毁容器时异常，sessionId=[{}]", sessionId, e);
        } finally {
            try {
                client.close();
            } catch (IOException e) {
                log.warn("ContainerSandbox: 关闭 DockerClient 异常", e);
            }
        }
    }

    @Override
    public SandboxResult executeCode(String language, String code, Integer timeout) {
        ensureContainerReady();
        if (language == null || language.isBlank()) {
            return SandboxResult.fail("language 不能为空", type());
        }
        if (code == null || code.isBlank()) {
            return SandboxResult.fail("code 不能为空", type());
        }

        String[] cmdPrefix = LANGUAGE_COMMANDS.getOrDefault(language.toLowerCase(),
                new String[]{"bash", "-c"});
        String[] fullCmd;
        if (cmdPrefix.length == 2) {
            fullCmd = new String[]{cmdPrefix[0], cmdPrefix[1], code};
        } else {
            fullCmd = new String[]{"bash", "-c", code};
        }

        int timeoutSec = (timeout != null && timeout > 0) ? timeout : sandboxConfig.getTimeout();
        return execInContainer(fullCmd, timeoutSec);
    }

    @Override
    public SandboxResult executeCommand(String command) {
        ensureContainerReady();
        if (command == null || command.isBlank()) {
            return SandboxResult.fail("command 不能为空", type());
        }
        return execInContainer(new String[]{"bash", "-c", command}, sandboxConfig.getTimeout());
    }

    @Override
    public SandboxResult readFile(String path) {
        ensureContainerReady();
        long start = System.currentTimeMillis();

        try {
            String containerPath = resolveContainerPath(path);
            try (InputStream tarStream = dockerClient.copyArchiveFromContainerCmd(containerId, containerPath).exec()) {
                String content = extractFileFromTar(tarStream);
                long elapsed = System.currentTimeMillis() - start;
                return SandboxResult.builder()
                        .success(true)
                        .content(content)
                        .elapsedMs(elapsed)
                        .sandboxType(type())
                        .build();
            }
        } catch (NotFoundException e) {
            return SandboxResult.fail("文件不存在: " + path, type());
        } catch (Exception e) {
            return SandboxResult.fail("读取文件失败: " + e.getMessage(), type());
        }
    }

    @Override
    public SandboxResult writeFile(String path, String content) {
        ensureContainerReady();
        long start = System.currentTimeMillis();

        try {
            String containerPath = resolveContainerPath(path);
            // 使用字符串操作避免 Windows Path.of() 引入反斜杠
            int lastSlash = containerPath.lastIndexOf('/');
            String fileName = lastSlash >= 0 ? containerPath.substring(lastSlash + 1) : containerPath;
            String parentDir = lastSlash > 0 ? containerPath.substring(0, lastSlash) : "/";

            // 构建 tar 包
            byte[] tarBytes = createTarBytes(fileName, content);

            // 确保父目录存在
            execInContainer(new String[]{"mkdir", "-p", parentDir}, 5);

            // 复制 tar 到容器
            try (InputStream tarStream = new ByteArrayInputStream(tarBytes)) {
                dockerClient.copyArchiveToContainerCmd(containerId)
                        .withTarInputStream(tarStream)
                        .withRemotePath(parentDir)
                        .exec();
            }

            // 修复文件权限：copyArchive 写入的文件属 root，需 chown 给 sandbox 用户
            execInContainer(new String[]{"chown", "sandbox:sandbox", containerPath}, 5, "root");

            long elapsed = System.currentTimeMillis() - start;
            return SandboxResult.builder()
                    .success(true)
                    .elapsedMs(elapsed)
                    .sandboxType(type())
                    .build();
        } catch (Exception e) {
            return SandboxResult.fail("写入文件失败: " + e.getMessage(), type());
        }
    }

    @Override
    public SandboxResult listDirectory(String path) {
        ensureContainerReady();
        String containerPath = resolveContainerPath(path);
        return execInContainer(new String[]{"ls", "-la", containerPath}, sandboxConfig.getTimeout());
    }

    @Override
    public SandboxResult searchFiles(String path, String pattern) {
        ensureContainerReady();
        if (pattern == null || pattern.isBlank()) {
            return SandboxResult.fail("pattern 不能为空", type());
        }

        String containerPath = resolveContainerPath(path);

        // 将 glob 模式转换为 find 命令的 -name 参数
        // "**/*.py" → 只取最后一段 "*.py" 用于 -name，-name 不支持 ** 递归通配
        // find 本身递归搜索，-name 用最末段模式即可
        String namePattern = extractGlobLeaf(pattern);

        // find {path} -type f -name "{namePattern}" 2>/dev/null
        String command = String.format(
                "find '%s' -type f -name '%s' 2>/dev/null | head -200",
                escapeShell(containerPath), escapeShell(namePattern));

        SandboxResult result = execInContainer(new String[]{"bash", "-c", command}, sandboxConfig.getTimeout());

        // 如果原始 pattern 含 "/"（如 "src/**/*.java"），对 find 结果做 glob 精确过滤
        if (result.isSuccess() && result.getStdout() != null && pattern.contains("/")) {
            String filtered = filterByGlob(result.getStdout(), containerPath, pattern);
            return SandboxResult.builder()
                    .success(true)
                    .content(filtered)
                    .elapsedMs(result.getElapsedMs())
                    .sandboxType(type())
                    .build();
        }

        return result.isSuccess()
                ? SandboxResult.builder()
                    .success(true)
                    .content(result.getStdout())
                    .elapsedMs(result.getElapsedMs())
                    .sandboxType(type())
                    .build()
                : result;
    }

    @Override
    public SandboxResult searchContent(String path, String pattern, String includePattern) {
        ensureContainerReady();
        if (pattern == null || pattern.isBlank()) {
            return SandboxResult.fail("pattern 不能为空", type());
        }

        String containerPath = resolveContainerPath(path);

        // grep -rn "{pattern}" {path} [--include="{includePattern}"] 2>/dev/null | head -200
        StringBuilder cmd = new StringBuilder();
        cmd.append("grep -rn '").append(escapeShell(pattern)).append("' '");
        cmd.append(escapeShell(containerPath)).append("'");
        if (includePattern != null && !includePattern.isBlank()) {
            cmd.append(" --include='").append(escapeShell(includePattern)).append("'");
        }
        cmd.append(" 2>/dev/null | head -200");

        SandboxResult result = execInContainer(new String[]{"bash", "-c", cmd.toString()}, sandboxConfig.getTimeout());

        // grep 退出码：0=有匹配，1=无匹配，>1=错误（权限不足、路径不存在等）
        // exitCode <= 1 均视为执行成功（无匹配不是错误）
        boolean success = result.getExitCode() != null && result.getExitCode() <= 1;
        if (!success) {
            return SandboxResult.builder()
                    .success(false)
                    .error("grep 执行失败: " + (result.getStderr() != null ? result.getStderr() : "未知错误"))
                    .exitCode(result.getExitCode())
                    .elapsedMs(result.getElapsedMs())
                    .sandboxType(type())
                    .build();
        }

        return SandboxResult.builder()
                .success(true)
                .content(result.getStdout() != null ? result.getStdout() : "")
                .elapsedMs(result.getElapsedMs())
                .sandboxType(type())
                .build();
    }

    // ========== 搜索工具方法 ==========

    /**
     * 提取 glob 模式最末段（不含斜杠）用于 find -name。
     * 例如 "src" + 双星 + 斜杠 + "*.java" 变为 "*.java"，"*.py" 不变。
     */
    static String extractGlobLeaf(String pattern) {
        int lastSlash = pattern.lastIndexOf('/');
        return lastSlash >= 0 ? pattern.substring(lastSlash + 1) : pattern;
    }

    /**
     * 对 find 结果按完整 glob 模式过滤（处理含目录的模式，如 "src" 下的递归 java 搜索）。
     * 将 find 输出的绝对路径去掉 basePath 前缀后，与 glob 模式匹配。
     * 简化实现：仅处理 * 通配符。
     */
    private String filterByGlob(String findOutput, String basePath, String globPattern) {
        StringBuilder sb = new StringBuilder();
        String[] lines = findOutput.split("\n");
        for (String line : lines) {
            if (line.isBlank()) continue;
            // 将路径转为相对路径
            String relative = line.startsWith(basePath)
                    ? line.substring(basePath.length()).replaceFirst("^/", "")
                    : line;
            if (matchesSimpleGlob(relative, globPattern)) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString().trim();
    }

    /**
     * 简单 glob 匹配：将 glob 中 ** 视为 "任意多级目录"，* 视为 "不含 / 的任意字符"。
     * 使用占位符避免 ** 被 * 替换规则误处理。
     */
    static boolean matchesSimpleGlob(String path, String glob) {
        final String DOUBLE_STAR_PLACEHOLDER = "\u0000";
        // 将 glob 转为正则
        String regex = glob
                .replace("**", DOUBLE_STAR_PLACEHOLDER)   // 先保护 **
                .replace("*", "[^/]*")                     // 单 * → 不含 / 的任意字符
                .replace("?", "[^/]")                      // 单 ? → 单个非 / 字符
                .replace(DOUBLE_STAR_PLACEHOLDER, "(?:.*/)?");  // ** → 任意层级目录（含零层）
        return path.matches(regex);
    }

    /**
     * Shell 参数转义（防止单引号注入）
     */
    private static String escapeShell(String s) {
        if (s == null) return "";
        return s.replace("'", "'\\''");
    }

    @Override
    public SandboxResult editFile(String path, String oldString, String newString,
                                  Integer startLine, Integer endLine, String newContent) {
        ensureContainerReady();
        long start = System.currentTimeMillis();

        try {
            // 1. 读取当前文件内容
            SandboxResult readResult = readFile(path);
            if (!readResult.isSuccess()) {
                return readResult;
            }
            String content = readResult.getContent();
            if (content == null) {
                content = "";
            }

            // 2. 在内存中应用编辑
            String edited;
            if (oldString != null && !oldString.isEmpty()) {
                // 文本替换模式
                edited = applyTextEdit(content, oldString, newString != null ? newString : "");
            } else if (startLine != null && endLine != null) {
                // 行级替换模式
                edited = applyLineEdit(content, startLine, endLine, newContent != null ? newContent : "");
            } else {
                return SandboxResult.fail("必须指定 oldString/newString 或 startLine/endLine", type());
            }

            // 3. 写回文件
            SandboxResult writeResult = writeFile(path, edited);
            if (!writeResult.isSuccess()) {
                return writeResult;
            }

            long elapsed = System.currentTimeMillis() - start;
            return SandboxResult.builder()
                    .success(true)
                    .elapsedMs(elapsed)
                    .sandboxType(type())
                    .build();
        } catch (IllegalArgumentException e) {
            return SandboxResult.fail(e.getMessage(), type());
        }
    }

    // ========== 容器操作 ==========

    /**
     * 确保镜像存在（本地不存在则拉取）
     */
    private void ensureImageExists() {
        String image = containerConfig.getImage();
        try {
            dockerClient.inspectImageCmd(image).exec();
            log.debug("ContainerSandbox: 镜像已存在: {}", image);
        } catch (NotFoundException e) {
            log.info("ContainerSandbox: 本地无镜像 [{}]，开始拉取...", image);
            try {
                dockerClient.pullImageCmd(image).start().awaitCompletion(5, TimeUnit.MINUTES);
                log.info("ContainerSandbox: 镜像拉取完成: {}", image);
            } catch (Exception ex) {
                throw new RuntimeException("拉取镜像失败: " + image, ex);
            }
        }
    }

    /**
     * 创建并启动容器
     */
    private void createAndStartContainer() {
        String image = containerConfig.getImage();
        long memoryBytes = parseMemoryLimit(containerConfig.getMemoryLimit());
        long cpuQuota = (long) (containerConfig.getCpuLimit() * 100000);

        HostConfig hostConfig = HostConfig.newHostConfig()
                .withMemory(memoryBytes)
                .withCpuQuota(cpuQuota)
                .withCpuPeriod(100000L)
                .withNetworkMode(containerConfig.getNetworkMode())
                .withAutoRemove(Boolean.TRUE.equals(containerConfig.getAutoRemove()))
                .withSecurityOpts(containerConfig.getSecurityOpts());

        // pidsLimit 安全处理 null
        if (containerConfig.getPidsLimit() != null) {
            hostConfig.withPidsLimit(containerConfig.getPidsLimit().longValue());
        }

        // 卷挂载：每个会话隔离的宿主机目录
        String volumeBasePath = containerConfig.getVolumeBasePath();
        if (volumeBasePath != null && !volumeBasePath.isBlank()) {
            String hostWorkspacePath = volumeBasePath + "/" + sessionId + "/workspace";
            String bindSpec = hostWorkspacePath + ":" + workspacePath;
            hostConfig.withBinds(Bind.parse(bindSpec));
            log.info("ContainerSandbox: 卷挂载 [{}] → 容器 [{}]", hostWorkspacePath, workspacePath);
        }

        CreateContainerResponse response = dockerClient.createContainerCmd(image)
                .withName(containerName)
                .withHostConfig(hostConfig)
                .withWorkingDir(workspacePath)
                .withCmd("tail", "-f", "/dev/null")  // 保持容器运行
                .withTty(false)
                .withStdinOpen(false)
                .exec();

        this.containerId = response.getId();

        // 启动容器
        dockerClient.startContainerCmd(containerId).exec();

        // 确保工作目录存在并以 root 身份修复权限（卷挂载目录默认属 root）
        execInContainer(new String[]{"mkdir", "-p", workspacePath}, 5, "root");
        execInContainer(new String[]{"chown", "-R", "sandbox:sandbox", workspacePath}, 5, "root");
    }

    /**
     * 在容器内执行命令并捕获输出（默认使用容器配置的用户）
     *
     * @param cmd        命令数组
     * @param timeoutSec 超时秒数（0 或负数表示不超时）
     */
    private SandboxResult execInContainer(String[] cmd, int timeoutSec) {
        return execInContainer(cmd, timeoutSec, null);
    }

    /**
     * 在容器内执行命令并捕获输出
     *
     * @param cmd        命令数组
     * @param timeoutSec 超时秒数（0 或负数表示不超时）
     * @param user       执行用户（null 使用容器默认用户）
     */
    private SandboxResult execInContainer(String[] cmd, int timeoutSec, String user) {
        long start = System.currentTimeMillis();

        try {
            var execCreateCmd = dockerClient.execCreateCmd(containerId)
                    .withCmd(cmd)
                    .withAttachStdout(true)
                    .withAttachStderr(true);
            if (user != null && !user.isBlank()) {
                execCreateCmd.withUser(user);
            }
            ExecCreateCmdResponse execCreate = execCreateCmd.exec();

            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();

            var frameCallback = new com.github.dockerjava.api.async.ResultCallbackTemplate<
                    com.github.dockerjava.api.async.ResultCallback<Frame>, Frame>() {
                @Override
                public void onNext(Frame frame) {
                    if (frame == null) return;
                    switch (frame.getStreamType()) {
                        case STDOUT -> stdout.writeBytes(frame.getPayload());
                        case STDERR -> stderr.writeBytes(frame.getPayload());
                        default -> stdout.writeBytes(frame.getPayload());
                    }
                }
            };

            dockerClient.execStartCmd(execCreate.getId()).exec(frameCallback);
            try {
                if (timeoutSec > 0) {
                    frameCallback.awaitCompletion(timeoutSec, TimeUnit.SECONDS);
                } else {
                    // 即使无显式超时，也加上沙箱全局超时作为兜底
                    frameCallback.awaitCompletion(sandboxConfig.getTimeout(), TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return SandboxResult.fail("执行被中断", type());
            }

            // 检查退出码
            Long exitCode = null;
            try {
                var inspectExec = dockerClient.inspectExecCmd(execCreate.getId()).exec();
                exitCode = inspectExec.getExitCodeLong();
            } catch (Exception e) {
                log.debug("ContainerSandbox: 获取退出码异常，忽略", e);
            }

            long elapsed = System.currentTimeMillis() - start;
            String stdoutStr = truncateOutput(stdout.toString(StandardCharsets.UTF_8));
            String stderrStr = truncateOutput(stderr.toString(StandardCharsets.UTF_8));

            // 安全处理 exitCode 为 null 的情况
            boolean success = exitCode != null && exitCode == 0L;
            return SandboxResult.builder()
                    .success(success)
                    .exitCode(exitCode != null ? exitCode.intValue() : -1)
                    .stdout(stdoutStr)
                    .stderr(stderrStr)
                    .elapsedMs(elapsed)
                    .sandboxType(type())
                    .build();

        } catch (DockerException e) {
            return SandboxResult.fail("容器执行异常: " + e.getMessage(), type());
        }
    }

    /**
     * 检查容器是否就绪
     */
    protected void ensureContainerReady() {
        if (containerId == null || dockerClient == null) {
            throw new IllegalStateException("容器沙箱未初始化，请先调用 initialize()");
        }
    }

    /**
     * 解析容器内路径（相对路径补全为工作目录下的绝对路径），同时防止路径穿越
     *
     * @throws IllegalArgumentException 如果路径包含穿越序列
     */
    private String resolveContainerPath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("路径不能为空");
        }

        // 标准化路径：去除 /./ 和 //
        String normalized = path.replaceAll("/+", "/").replaceAll("/\\./", "/");

        // 路径穿越检测
        if (normalized.contains("..")) {
            throw new IllegalArgumentException("路径不允许包含 '..': " + path);
        }

        // 绝对路径：校验是否在白名单内
        if (normalized.startsWith("/")) {
            boolean allowed = ALLOWED_PATH_PREFIXES.stream()
                    .anyMatch(prefix -> normalized.startsWith(prefix));
            if (!allowed) {
                throw new IllegalArgumentException("绝对路径不在允许范围内: " + path
                        + "，允许的前缀: " + ALLOWED_PATH_PREFIXES);
            }
            return normalized;
        }

        // 相对路径：拼接到 workspace
        return workspacePath + "/" + normalized;
    }

    /**
     * 解析内存限制字符串为字节数
     */
    private long parseMemoryLimit(String memoryLimit) {
        if (memoryLimit == null || memoryLimit.isBlank()) {
            return 512 * 1024 * 1024L; // 默认 512MB
        }
        memoryLimit = memoryLimit.trim().toLowerCase();
        long value = Long.parseLong(memoryLimit.replaceAll("[^0-9]", ""));
        if (memoryLimit.endsWith("g")) {
            return value * 1024 * 1024 * 1024;
        } else if (memoryLimit.endsWith("k")) {
            return value * 1024;
        }
        return value * 1024 * 1024; // 默认 MB
    }

    /**
     * 截断输出（防止超出最大输出限制）
     */
    private String truncateOutput(String output) {
        if (output == null) return "";
        int maxBytes = sandboxConfig.getMaxOutputBytes() != null ? sandboxConfig.getMaxOutputBytes() : 65536;
        byte[] bytes = output.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) {
            return output;
        }
        return new String(bytes, 0, maxBytes, StandardCharsets.UTF_8) + "\n... [输出截断]";
    }

    /**
     * 从 tar 输入流中提取文件内容（限制最大读取量防止 OOM）
     */
    private String extractFileFromTar(InputStream tarStream) throws IOException {
        try (TarArchiveInputStream tis = new TarArchiveInputStream(tarStream)) {
            TarArchiveEntry entry;
            while ((entry = tis.getNextTarEntry()) != null) {
                if (!entry.isDirectory()) {
                    // 检查文件大小，防止 OOM
                    long size = entry.getSize();
                    if (size > MAX_READ_FILE_BYTES) {
                        throw new IOException("文件过大: " + size + " bytes，最大允许: " + MAX_READ_FILE_BYTES);
                    }
                    return new String(tis.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new IOException("tar 包中未找到文件");
    }

    /**
     * 构建 tar 字节数组（用于写入文件到容器）
     */
    private byte[] createTarBytes(String fileName, String content) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tos = new TarArchiveOutputStream(baos)) {
            byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
            TarArchiveEntry entry = new TarArchiveEntry(fileName);
            entry.setSize(contentBytes.length);
            tos.putArchiveEntry(entry);
            tos.write(contentBytes);
            tos.closeArchiveEntry();
        }
        return baos.toByteArray();
    }

    /**
     * 初始化失败时清理已创建的资源
     */
    private void cleanupOnFailure() {
        if (containerId != null && dockerClient != null) {
            try {
                dockerClient.removeContainerCmd(containerId).withForce(true).withRemoveVolumes(true).exec();
            } catch (Exception e) {
                log.warn("ContainerSandbox: cleanupOnFailure 清理容器异常", e);
            }
        }
        if (dockerClient != null) {
            try {
                dockerClient.close();
            } catch (Exception e) {
                log.warn("ContainerSandbox: cleanupOnFailure 关闭 DockerClient 异常", e);
            }
        }
        containerId = null;
        dockerClient = null;
    }

    /**
     * 安全截断容器 ID 用于日志
     */
    private static String abbreviateId(String id) {
        if (id == null) return "null";
        return id.length() >= 12 ? id.substring(0, 12) : id;
    }

    /**
     * 查看容器状态（高级用法）
     *
     * @return 容器状态信息（running / exited / created 等）
     */
    public String inspectContainerStatus() {
        ensureContainerReady();
        try {
            InspectContainerResponse inspect = dockerClient.inspectContainerCmd(containerId).exec();
            return inspect.getState().getStatus();
        } catch (DockerException e) {
            return "unknown: " + e.getMessage();
        }
    }
}
