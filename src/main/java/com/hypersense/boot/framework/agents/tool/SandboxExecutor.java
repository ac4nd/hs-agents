package com.hypersense.boot.framework.agents.tool;

/**
 * Sandbox 执行抽象。SandboxExecTool 等具体实现包装为统一接口供 Lint 规则调用。
 *
 * <p>注意：与 {@code SandboxManager}（基于会话生命周期的沙箱池）不同，
 * 此接口为「单条命令同步执行」的最小契约，便于在 Lint 规则中 mock 与替换。</p>
 */
public interface SandboxExecutor {

    /**
     * 执行一条 shell 命令。
     *
     * @param command 命令字符串
     * @param workDir 工作目录（可为空字符串表示默认目录）
     * @return 执行结果（exit code + stdout + stderr）
     */
    Result exec(String command, String workDir);

    /**
     * 执行结果。
     */
    record Result(int exitCode, String stdout, String stderr) {
        public boolean success() {
            return exitCode == 0;
        }
    }
}
