package com.hypersense.boot.framework.agents.sandbox;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Sandbox 操作结果
 *
 * @author Claude
 * @since 2026/5/19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SandboxResult {

    /** 操作是否成功 */
    private boolean success;

    /** 退出码（执行命令时有效） */
    private Integer exitCode;

    /** 标准输出 */
    private String stdout;

    /** 标准错误 */
    private String stderr;

    /** 错误信息（操作失败时的描述） */
    private String error;

    /** 执行耗时（毫秒） */
    private Long elapsedMs;

    /** 文件内容（读取文件时有效） */
    private String content;

    /** 沙箱类型标识 */
    private String sandboxType;

    // ========== 静态工厂方法 ==========

    /**
     * 构建成功结果
     */
    public static SandboxResult ok(String stdout, String sandboxType) {
        return SandboxResult.builder()
                .success(true)
                .exitCode(0)
                .stdout(stdout)
                .sandboxType(sandboxType)
                .build();
    }

    /**
     * 构建失败结果
     */
    public static SandboxResult fail(String error, String sandboxType) {
        return SandboxResult.builder()
                .success(false)
                .error(error)
                .sandboxType(sandboxType)
                .build();
    }

    /**
     * 构建未实现结果
     */
    public static SandboxResult notImplemented(String sandboxType) {
        return SandboxResult.builder()
                .success(false)
                .error("该操作在 " + sandboxType + " 沙箱中暂未实现")
                .sandboxType(sandboxType)
                .build();
    }
}
