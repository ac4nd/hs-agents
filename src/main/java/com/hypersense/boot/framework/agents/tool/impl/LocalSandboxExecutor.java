package com.hypersense.boot.framework.agents.tool.impl;

import com.hypersense.boot.framework.agents.tool.SandboxExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * 本地 ProcessBuilder 实现的 SandboxExecutor（code-profile lint 的执行后端）。
 *
 * <p>spec §5.5：CompilePassRule / TestPassRule 调用本执行器跑 py_compile / pytest / mvn / npm / go 等。
 * 与 session-scoped 的 {@code SandboxManager}（基于会话生命周期的沙箱池）不同 ——
 * 本执行器是无状态、无会话的 shell 调用，专门服务于 lint 规则的轻量编译/测试验证。</p>
 *
 * <p>安全性：本执行器直接调用宿主进程，<b>仅在开发/CI 环境使用</b>。生产环境应替换为
 * 容器化实现。本类不在用户输入上 shell-escape，调用方（CompilePassRule / TestPassRule）
 * 传入的 command 必须是固定模板 + 受控路径。</p>
 *
 * <h3>超时</h3>
 * 默认 30 秒，超时返回 exitCode=124（与 UNIX timeout 命令一致）+ stderr 提示。
 *
 * @author Claude
 * @since 2026/6/29
 */
@Slf4j
@Component
public class LocalSandboxExecutor implements SandboxExecutor {

    /** 默认执行超时（秒）。lint 不应跑长任务，30s 充分。 */
    private static final long DEFAULT_TIMEOUT_SECONDS = 30;

    @Override
    public Result exec(String command, String workDir) {
        if (command == null || command.isBlank()) {
            return new Result(2, "", "command 为空");
        }

        ProcessBuilder pb = new ProcessBuilder(shlex(command));
        if (workDir != null && !workDir.isBlank()) {
            pb.directory(new File(workDir));
        }
        pb.redirectErrorStream(false);

        Process process = null;
        try {
            process = pb.start();
            String stdout = readAll(process.getInputStream());
            String stderr = readAll(process.getErrorStream());
            boolean finished = process.waitFor(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("LocalSandboxExecutor: 命令超时({}s) 被强杀: {}", DEFAULT_TIMEOUT_SECONDS, command);
                return new Result(124, stdout, "执行超时（>" + DEFAULT_TIMEOUT_SECONDS + "s）");
            }
            int code = process.exitValue();
            return new Result(code, stdout, stderr);
        } catch (IOException | InterruptedException e) {
            log.warn("LocalSandboxExecutor: 执行失败 cmd={}, err={}", command, e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new Result(127, "", e.getMessage() == null ? "执行异常" : e.getMessage());
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    /**
     * POSIX 风格 shlex：支持单引号 / 双引号包裹含空格的参数。
     *
     * <p>语义：</p>
     * <ul>
     *   <li>未加引号的空白为分隔符</li>
     *   <li>单引号内内容原样保留（不转义）</li>
     *   <li>双引号内支持 {@code \"} 和 {@code \\} 转义，其他原样保留</li>
     *   <li>引号外支持 {@code \} 转义下一字符</li>
     * </ul>
     *
     * <p>边界：未闭合引号按剩余全部内容归入当前参数（宽容，与 shell 一致）。
     * 空 command 返回空数组（上层已在 {@link #exec} 入口拦截）。</p>
     */
    static String[] shlex(String command) {
        java.util.List<String> tokens = new java.util.ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inSingle = false;
        boolean inDouble = false;
        boolean hasToken = false;

        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);

            if (inSingle) {
                if (c == '\'') {
                    inSingle = false;
                } else {
                    cur.append(c);
                }
                continue;
            }

            if (c == '\\' && !inDouble) {
                // 双引号外或无引号：转义下一字符
                if (i + 1 < command.length()) {
                    cur.append(command.charAt(++i));
                    hasToken = true;
                }
                continue;
            }

            if (c == '\\' && inDouble) {
                if (i + 1 < command.length()) {
                    char next = command.charAt(i + 1);
                    if (next == '"' || next == '\\') {
                        cur.append(next);
                        i++;
                        hasToken = true;
                        continue;
                    }
                }
                cur.append(c);
                continue;
            }

            if (c == '"') {
                inDouble = !inDouble;
                hasToken = true;
                continue;
            }
            if (c == '\'') {
                inSingle = true;
                hasToken = true;
                continue;
            }

            if (Character.isWhitespace(c) && !inDouble) {
                if (hasToken) {
                    tokens.add(cur.toString());
                    cur.setLength(0);
                    hasToken = false;
                }
                continue;
            }

            cur.append(c);
            hasToken = true;
        }

        if (hasToken) {
            tokens.add(cur.toString());
        }
        return tokens.toArray(new String[0]);
    }

    private static String readAll(InputStream is) throws IOException {
        if (is == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(line);
            }
        }
        return sb.toString();
    }
}
