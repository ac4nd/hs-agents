package com.hypersense.boot.framework.agents.sandbox;

import com.hypersense.boot.framework.agents.config.AgentProperties;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 本地沙箱实现
 * <p>
 * 在宿主机上通过子进程执行代码和命令，通过 java.nio.file 操作文件系统。
 * 提供语言白名单、危险代码黑名单、超时控制、输出截断等安全措施。
 * </p>
 *
 * @author Claude
 * @since 2026/5/19
 */
@Slf4j
public class LocalSandbox extends Sandbox {

    private final AgentProperties.SandboxConfig config;
    private final Set<String> allowedLanguages;
    private final Path workDir;

    /** 语言 → 执行命令模板 */
    private static final Map<String, String[]> LANGUAGE_COMMANDS = Map.of(
            "python", new String[]{"python3", "-c"},
            "python3", new String[]{"python3", "-c"},
            "javascript", new String[]{"node", "-e"},
            "js", new String[]{"node", "-e"},
            "shell", new String[]{"bash", "-c"},
            "bash", new String[]{"bash", "-c"},
            "sh", new String[]{"sh", "-c"}
    );

    /** 危险代码模式黑名单 */
    private static final List<Pattern> DANGEROUS_PATTERNS = List.of(
            Pattern.compile("(?i)(rm\\s+-rf\\s+/|rm\\s+-r\\s+/)"),
            Pattern.compile("(?i)(mkfs\\.|dd\\s+if=|dd\\s+of=/dev)"),
            Pattern.compile("(?i)(shutdown|reboot|halt|poweroff)\\s"),
            Pattern.compile("(?i)(curl|wget)\\s+.*\\|\\s*(sh|bash)"),
            Pattern.compile("(?i)(/etc/passwd|/etc/shadow|/etc/sudoers)"),
            Pattern.compile("(?i):\\(\\{.*:\\|.*:&\\}"),  // fork bomb
            Pattern.compile("(?i)chmod\\s+[0-7]*777\\s+/"),
            Pattern.compile("(?i)(nc|ncat|netcat).*-e\\s+/bin/(sh|bash)")
    );

    public LocalSandbox(AgentProperties agentProperties) {
        this.config = agentProperties.getTools().getSandbox();
        this.allowedLanguages = parseAllowedLanguages();

        // 工作目录：配置指定或默认使用 java.io.tmpdir/agent-sandbox
        String configuredWorkDir = config.getWorkDir();
        if (configuredWorkDir != null && !configuredWorkDir.isBlank()) {
            this.workDir = Path.of(configuredWorkDir);
        } else {
            this.workDir = Path.of(System.getProperty("java.io.tmpdir"), "agent-sandbox");
        }
    }

    /**
     * 会话隔离构造器（由 SandboxFactory 调用）
     *
     * @param agentProperties 全局配置
     * @param sessionWorkDir  会话专属工作目录（baseWorkDir/{sessionId}/）
     */
    public LocalSandbox(AgentProperties agentProperties, Path sessionWorkDir) {
        this.config = agentProperties.getTools().getSandbox();
        this.allowedLanguages = parseAllowedLanguages();
        this.workDir = sessionWorkDir;
    }

    @Override
    public String type() {
        return "local";
    }

    @Override
    public void initialize() {
        try {
            Files.createDirectories(workDir);
            log.info("LocalSandbox: 工作目录已创建: {}", workDir.toAbsolutePath());
        } catch (IOException e) {
            log.error("LocalSandbox: 无法创建工作目录: {}", workDir, e);
            throw new RuntimeException("无法创建沙箱工作目录: " + workDir, e);
        }
    }

    @Override
    public void destroy() {
        try {
            if (Files.exists(workDir)) {
                try (Stream<Path> walk = Files.walk(workDir)) {
                    walk.sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(File::delete);
                }
                log.info("LocalSandbox: 工作目录已清理: {}", workDir.toAbsolutePath());
            }
        } catch (IOException e) {
            log.warn("LocalSandbox: 清理工作目录失败: {}", workDir, e);
        }
    }

    // ========== 代码执行 ==========

    @Override
    public SandboxResult executeCode(String language, String code, Integer timeout) {
        // 语言标准化
        String normalizedLang = normalizeLanguage(language);
        if (normalizedLang == null || normalizedLang.isBlank()) {
            return SandboxResult.fail("缺少 language 参数，支持: " + allowedLanguages, type());
        }
        if (code == null || code.isBlank()) {
            return SandboxResult.fail("缺少 code 参数", type());
        }

        // 语言白名单校验
        if (!allowedLanguages.contains(normalizedLang)) {
            return SandboxResult.fail("不允许的语言: " + normalizedLang + "，允许列表: " + allowedLanguages, type());
        }

        // 安全校验
        String securityWarning = validateCodeSafety(code);
        if (securityWarning != null) {
            return SandboxResult.fail(securityWarning, type());
        }

        // 查找执行命令
        String[] commandTemplate = LANGUAGE_COMMANDS.get(normalizedLang);
        if (commandTemplate == null) {
            return SandboxResult.fail("不支持的语言: " + normalizedLang, type());
        }

        log.info("LocalSandbox: 执行语言=[{}]，代码长度=[{}]", normalizedLang, code.length());

        int timeoutSeconds = timeout != null ? timeout : getConfigTimeout();
        int maxOutput = config.getMaxOutputBytes() != null ? config.getMaxOutputBytes() : 65536;

        long startTime = System.currentTimeMillis();
        try {
            ExecuteResult result = executeInProcess(commandTemplate, code, timeoutSeconds, maxOutput);
            long elapsed = System.currentTimeMillis() - startTime;

            log.info("LocalSandbox: 执行完成，耗时={}ms，退出码={}", elapsed, result.exitCode);

            return SandboxResult.builder()
                    .success(result.exitCode == 0)
                    .exitCode(result.exitCode)
                    .stdout(result.stdout)
                    .stderr(result.stderr)
                    .elapsedMs(elapsed)
                    .sandboxType(type())
                    .build();
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("LocalSandbox: 执行异常，耗时={}ms", elapsed, e);
            return SandboxResult.builder()
                    .success(false)
                    .error("执行失败: " + e.getMessage())
                    .elapsedMs(elapsed)
                    .sandboxType(type())
                    .build();
        }
    }

    // ========== 文件操作 ==========

    @Override
    public SandboxResult readFile(String path) {
        try {
            Path resolved = resolveSecurePath(path);
            if (resolved == null) {
                return SandboxResult.fail("路径不合法或越界: " + path, type());
            }
            if (!Files.exists(resolved)) {
                return SandboxResult.fail("文件不存在: " + path, type());
            }
            String content = Files.readString(resolved, StandardCharsets.UTF_8);
            return SandboxResult.builder()
                    .success(true)
                    .content(content)
                    .sandboxType(type())
                    .build();
        } catch (IOException e) {
            log.error("LocalSandbox: 读取文件失败 path={}", path, e);
            return SandboxResult.fail("读取文件失败: " + e.getMessage(), type());
        }
    }

    @Override
    public SandboxResult writeFile(String path, String content) {
        try {
            Path resolved = resolveSecurePath(path);
            if (resolved == null) {
                return SandboxResult.fail("路径不合法或越界: " + path, type());
            }
            // 确保父目录存在
            Files.createDirectories(resolved.getParent());
            Files.writeString(resolved, content != null ? content : "", StandardCharsets.UTF_8);
            return SandboxResult.builder()
                    .success(true)
                    .sandboxType(type())
                    .build();
        } catch (IOException e) {
            log.error("LocalSandbox: 写入文件失败 path={}", path, e);
            return SandboxResult.fail("写入文件失败: " + e.getMessage(), type());
        }
    }

    @Override
    public SandboxResult editFile(String path, String oldString, String newString,
                                  Integer startLine, Integer endLine, String newContent) {
        long start = System.currentTimeMillis();
        try {
            Path resolved = resolveSecurePath(path);
            if (resolved == null) {
                return SandboxResult.fail("路径不合法或越界: " + path, type());
            }
            if (!Files.exists(resolved)) {
                return SandboxResult.fail("文件不存在: " + path, type());
            }

            String content = Files.readString(resolved, StandardCharsets.UTF_8);

            // 应用编辑
            String edited;
            if (oldString != null && !oldString.isEmpty()) {
                edited = applyTextEdit(content, oldString, newString != null ? newString : "");
            } else if (startLine != null && endLine != null) {
                edited = applyLineEdit(content, startLine, endLine, newContent != null ? newContent : "");
            } else {
                return SandboxResult.fail("必须指定 oldString/newString 或 startLine/endLine", type());
            }

            Files.writeString(resolved, edited, StandardCharsets.UTF_8);

            long elapsed = System.currentTimeMillis() - start;
            return SandboxResult.builder()
                    .success(true)
                    .elapsedMs(elapsed)
                    .sandboxType(type())
                    .build();
        } catch (IllegalArgumentException e) {
            return SandboxResult.fail(e.getMessage(), type());
        } catch (IOException e) {
            log.error("LocalSandbox: 编辑文件失败 path={}", path, e);
            return SandboxResult.fail("编辑文件失败: " + e.getMessage(), type());
        }
    }

    @Override
    public SandboxResult listDirectory(String path) {
        try {
            Path resolved = resolveSecurePath(path);
            if (resolved == null) {
                return SandboxResult.fail("路径不合法或越界: " + path, type());
            }
            if (!Files.isDirectory(resolved)) {
                return SandboxResult.fail("路径不是目录: " + path, type());
            }
            StringBuilder sb = new StringBuilder();
            try (Stream<Path> stream = Files.list(resolved)) {
                stream.forEach(p -> sb.append(p.getFileName()).append('\n'));
            }
            return SandboxResult.builder()
                    .success(true)
                    .content(sb.toString().trim())
                    .sandboxType(type())
                    .build();
        } catch (IOException e) {
            log.error("LocalSandbox: 列目录失败 path={}", path, e);
            return SandboxResult.fail("列目录失败: " + e.getMessage(), type());
        }
    }

    @Override
    public SandboxResult searchFiles(String path, String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return SandboxResult.fail("pattern 不能为空", type());
        }
        try {
            Path resolved = resolveSecurePath(path);
            if (resolved == null) {
                return SandboxResult.fail("路径不合法或越界: " + path, type());
            }
            if (!Files.exists(resolved)) {
                return SandboxResult.fail("路径不存在: " + path, type());
            }

            // 将 glob 转为 PathMatcher
            String globSyntax = pattern.startsWith("glob:") ? pattern : "glob:" + pattern;
            java.nio.file.PathMatcher matcher = resolved.getFileSystem().getPathMatcher(globSyntax);

            StringBuilder sb = new StringBuilder();
            int[] count = {0};
            int maxResults = 200;
            int maxDepth = 20;

            try (Stream<Path> walk = Files.walk(resolved, maxDepth)) {
                walk.filter(Files::isRegularFile)
                        .forEach(file -> {
                            if (count[0] >= maxResults) return;
                            Path relative = resolved.relativize(file);
                            if (matcher.matches(relative)) {
                                sb.append(relative).append('\n');
                                count[0]++;
                            }
                        });
            }

            return SandboxResult.builder()
                    .success(true)
                    .content(sb.toString().trim())
                    .sandboxType(type())
                    .build();
        } catch (Exception e) {
            log.error("LocalSandbox: glob 搜索失败 path={}, pattern={}", path, pattern, e);
            return SandboxResult.fail("glob 搜索失败: " + e.getMessage(), type());
        }
    }

    @Override
    public SandboxResult searchContent(String path, String pattern, String includePattern) {
        if (pattern == null || pattern.isBlank()) {
            return SandboxResult.fail("pattern 不能为空", type());
        }
        try {
            Path resolved = resolveSecurePath(path);
            if (resolved == null) {
                return SandboxResult.fail("路径不合法或越界: " + path, type());
            }
            if (!Files.exists(resolved)) {
                return SandboxResult.fail("路径不存在: " + path, type());
            }

            java.util.regex.Pattern regex = java.util.regex.Pattern.compile(pattern);
            // 文件名过滤（一次赋值，确保 effectively final）
            final java.nio.file.PathMatcher includeMatcher;
            if (includePattern != null && !includePattern.isBlank()) {
                String globSyntax = includePattern.startsWith("glob:") ? includePattern : "glob:" + includePattern;
                includeMatcher = resolved.getFileSystem().getPathMatcher(globSyntax);
            } else {
                includeMatcher = null;
            }

            StringBuilder sb = new StringBuilder();
            int[] count = {0};
            int maxResults = 200;
            int maxDepth = 20;

            // 单文件直接搜索
            if (Files.isRegularFile(resolved)) {
                grepInFile(resolved, resolved.getFileName().toString(), regex, sb, count, maxResults);
            } else {
                // 目录递归搜索
                try (Stream<Path> walk = Files.walk(resolved, maxDepth)) {
                    walk.filter(Files::isRegularFile)
                            .forEach(file -> {
                                if (count[0] >= maxResults) return;
                                // 文件名过滤
                                if (includeMatcher != null) {
                                    Path relative = resolved.relativize(file);
                                    if (!includeMatcher.matches(relative)) return;
                                }
                                String relativePath = resolved.relativize(file).toString();
                                try {
                                    grepInFile(file, relativePath, regex, sb, count, maxResults);
                                } catch (IOException e) {
                                    log.debug("LocalSandbox: 跳过不可读文件: {}", relativePath);
                                }
                            });
                }
            }

            return SandboxResult.builder()
                    .success(true)
                    .content(sb.toString().trim())
                    .sandboxType(type())
                    .build();
        } catch (java.util.regex.PatternSyntaxException e) {
            return SandboxResult.fail("正则表达式语法错误: " + e.getMessage(), type());
        } catch (Exception e) {
            log.error("LocalSandbox: grep 搜索失败 path={}, pattern={}", path, pattern, e);
            return SandboxResult.fail("grep 搜索失败: " + e.getMessage(), type());
        }
    }

    /** 单文件最大读取字节数（防止 OOM） */
    private static final long MAX_GREP_FILE_BYTES = 10 * 1024 * 1024; // 10MB

    /**
     * 在单个文件中搜索匹配行
     */
    private void grepInFile(Path file, String displayPath, java.util.regex.Pattern regex,
                            StringBuilder sb, int[] count, int maxResults) throws IOException {
        // 前置检查：跳过超大文件
        if (Files.size(file) > MAX_GREP_FILE_BYTES) {
            return;
        }
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++) {
            if (count[0] >= maxResults) return;
            if (regex.matcher(lines.get(i)).find()) {
                sb.append(displayPath).append(':').append(i + 1).append(':').append(lines.get(i)).append('\n');
                count[0]++;
            }
        }
    }

    // ========== 命令执行 ==========

    @Override
    public SandboxResult executeCommand(String command) {
        if (command == null || command.isBlank()) {
            return SandboxResult.fail("缺少 command 参数", type());
        }

        // 安全校验
        String securityWarning = validateCodeSafety(command);
        if (securityWarning != null) {
            return SandboxResult.fail(securityWarning, type());
        }

        log.info("LocalSandbox: 执行命令，长度=[{}]", command.length());

        int timeoutSeconds = getConfigTimeout();
        int maxOutput = config.getMaxOutputBytes() != null ? config.getMaxOutputBytes() : 65536;

        long startTime = System.currentTimeMillis();
        try {
            String[] cmd = new String[]{"bash", "-c", command};
            ExecuteResult result = runProcess(cmd, timeoutSeconds, maxOutput);
            long elapsed = System.currentTimeMillis() - startTime;

            return SandboxResult.builder()
                    .success(result.exitCode == 0)
                    .exitCode(result.exitCode)
                    .stdout(result.stdout)
                    .stderr(result.stderr)
                    .elapsedMs(elapsed)
                    .sandboxType(type())
                    .build();
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("LocalSandbox: 命令执行异常，耗时={}ms", elapsed, e);
            return SandboxResult.builder()
                    .success(false)
                    .error("命令执行失败: " + e.getMessage())
                    .elapsedMs(elapsed)
                    .sandboxType(type())
                    .build();
        }
    }

    // ========== 核心进程执行逻辑 ==========

    /**
     * 在子进程中执行代码（短代码直接传参，长代码写入临时文件）
     */
    private ExecuteResult executeInProcess(String[] commandTemplate, String code,
                                           int timeout, int maxOutput) throws Exception {
        if (code.length() < 8000) {
            return executeDirect(commandTemplate, code, timeout, maxOutput);
        } else {
            return executeViaTempFile(commandTemplate, code, timeout, maxOutput);
        }
    }

    private ExecuteResult executeDirect(String[] commandTemplate, String code,
                                        int timeout, int maxOutput) throws Exception {
        String[] command = new String[commandTemplate.length + 1];
        System.arraycopy(commandTemplate, 0, command, 0, commandTemplate.length);
        command[commandTemplate.length] = code;
        return runProcess(command, timeout, maxOutput);
    }

    private ExecuteResult executeViaTempFile(String[] commandTemplate, String code,
                                            int timeout, int maxOutput) throws Exception {
        String interpreter = commandTemplate[0];
        String suffix = getSuffix(interpreter);
        Path tempFile = Files.createTempFile("agent_sandbox_", suffix);
        try {
            Files.writeString(tempFile, code, StandardCharsets.UTF_8);
            String[] command = new String[]{commandTemplate[0], tempFile.toAbsolutePath().toString()};
            return runProcess(command, timeout, maxOutput);
        } finally {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * 执行进程并收集输出
     */
    private ExecuteResult runProcess(String[] command, int timeout, int maxOutput) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(false);
        pb.directory(workDir.toFile());

        // 环境变量隔离
        if (Boolean.TRUE.equals(config.getLocal().getSanitizeEnv())) {
            Map<String, String> env = pb.environment();
            env.clear();
            env.put("PATH", System.getenv().getOrDefault("PATH", "/usr/bin:/bin"));
            env.put("HOME", workDir.toString());
            env.put("TMPDIR", System.getProperty("java.io.tmpdir"));
        }

        Process process = pb.start();

        ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();

        Thread stdoutReader = new Thread(() -> readStream(process.getInputStream(), stdoutBuffer, maxOutput));
        Thread stderrReader = new Thread(() -> readStream(process.getErrorStream(), stderrBuffer, maxOutput));
        stdoutReader.setDaemon(true);
        stderrReader.setDaemon(true);
        stdoutReader.start();
        stderrReader.start();

        boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            stdoutReader.join(1000);
            stderrReader.join(1000);
            return new ExecuteResult(
                    -1,
                    truncateOutput(stdoutBuffer),
                    "执行超时（" + timeout + "秒），进程已强制终止\n" + truncateOutput(stderrBuffer)
            );
        }

        stdoutReader.join(2000);
        stderrReader.join(2000);

        return new ExecuteResult(
                process.exitValue(),
                truncateOutput(stdoutBuffer),
                truncateOutput(stderrBuffer)
        );
    }

    private void readStream(java.io.InputStream inputStream, ByteArrayOutputStream buffer, int maxOutput) {
        try {
            byte[] buf = new byte[4096];
            int totalRead = 0;
            int bytesRead;
            while ((bytesRead = inputStream.read(buf)) != -1) {
                int canWrite = Math.min(bytesRead, maxOutput - totalRead);
                if (canWrite > 0) {
                    buffer.write(buf, 0, canWrite);
                    totalRead += canWrite;
                }
                if (totalRead >= maxOutput) {
                    buffer.write("\n... [输出已截断]".getBytes(StandardCharsets.UTF_8));
                    break;
                }
            }
        } catch (IOException ignored) {
        }
    }

    private String truncateOutput(ByteArrayOutputStream buffer) {
        if (buffer.size() == 0) return "";
        return buffer.toString(StandardCharsets.UTF_8);
    }

    // ========== 路径安全 ==========

    /**
     * 安全解析路径，防止路径穿越
     *
     * @return 解析后的绝对路径，如果路径越界则返回 null
     */
    private Path resolveSecurePath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        Path resolved = workDir.resolve(path).normalize();
        // 确保解析后的路径在工作目录内
        if (!resolved.startsWith(workDir)) {
            // 允许绝对路径访问（限制在工作目录下）
            return null;
        }
        return resolved;
    }

    // ========== 参数处理 ==========

    private String normalizeLanguage(String lang) {
        if (lang == null) return null;
        String normalized = lang.trim().toLowerCase();
        return switch (normalized) {
            case "js" -> "javascript";
            case "python3", "py" -> "python";
            case "bash", "sh" -> "shell";
            default -> normalized;
        };
    }

    private Set<String> parseAllowedLanguages() {
        String raw = config.getAllowedLanguages();
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }

    private String getSuffix(String interpreter) {
        return switch (interpreter) {
            case "python3" -> ".py";
            case "node" -> ".js";
            case "bash", "sh" -> ".sh";
            default -> ".txt";
        };
    }

    private int getConfigTimeout() {
        return config.getTimeout() != null ? config.getTimeout() : 30;
    }

    private String validateCodeSafety(String code) {
        for (Pattern pattern : DANGEROUS_PATTERNS) {
            if (pattern.matcher(code).find()) {
                log.warn("LocalSandbox: 检测到危险代码模式，已拒绝执行");
                return "代码包含潜在危险操作，已被安全策略拒绝。";
            }
        }
        return null;
    }

    // ========== 内部记录 ==========

    private record ExecuteResult(int exitCode, String stdout, String stderr) {
    }
}
