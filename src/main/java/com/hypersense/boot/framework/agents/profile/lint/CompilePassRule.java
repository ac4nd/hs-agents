package com.hypersense.boot.framework.agents.profile.lint;

import com.hypersense.boot.framework.agents.profile.LintRule;
import com.hypersense.boot.framework.agents.tool.SandboxExecutor;

/**
 * Lint：sandbox 内编译通过（spec §3.5）。
 * <ul>
 *   <li>python：py_compile</li>
 *   <li>java：javac</li>
 *   <li>go：go build</li>
 *   <li>javascript / typescript：跳过（解释型语言）</li>
 * </ul>
 */
public class CompilePassRule implements LintRule {

    private final SandboxExecutor sandbox;
    private final String language;
    private final String sourceFile;

    public CompilePassRule(SandboxExecutor sandbox, String language, String sourceFile) {
        this.sandbox = sandbox;
        this.language = language;
        this.sourceFile = sourceFile;
    }

    @Override
    public String id() {
        return "compile_pass";
    }

    @Override
    public String description() {
        return "sandbox 内编译通过（无语法错误）";
    }

    @Override
    public String check(String input) {
        if ("javascript".equalsIgnoreCase(language) || "typescript".equalsIgnoreCase(language)) {
            return null; // 解释型跳过编译检查
        }
        String cmd = switch (language.toLowerCase()) {
            case "python" -> "python -m py_compile " + sourceFile;
            case "java" -> "javac " + sourceFile;
            case "go" -> "go build ./...";
            default -> null;
        };
        if (cmd == null) {
            return null;
        }

        // 空串而非 null：保持与 Mockito anyString() 兼容
        SandboxExecutor.Result result = sandbox.exec(cmd, "");
        if (!result.success()) {
            return "编译失败（exit=" + result.exitCode() + "）：\n" +
                    (result.stderr().isBlank() ? result.stdout() : result.stderr());
        }
        return null;
    }
}
