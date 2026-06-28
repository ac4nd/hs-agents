package com.hypersense.boot.framework.agents.profile.lint;

import com.hypersense.boot.framework.agents.profile.LintRule;
import com.hypersense.boot.framework.agents.tool.SandboxExecutor;

/**
 * Lint：sandbox 内测试套件通过。
 * <p>默认跑全部测试；可通过 {@code testFile} 指定单文件。</p>
 */
public class TestPassRule implements LintRule {

    private final SandboxExecutor sandbox;
    private final String language;
    private final String testFile;

    public TestPassRule(SandboxExecutor sandbox, String language, String testFile) {
        this.sandbox = sandbox;
        this.language = language;
        this.testFile = testFile;
    }

    @Override
    public String id() {
        return "test_pass";
    }

    @Override
    public String description() {
        return "sandbox 内测试套件全部通过";
    }

    @Override
    public String check(String input) {
        String cmd = switch (language.toLowerCase()) {
            case "python" -> "pytest " + testFile + " -v";
            case "java" -> "mvn test -Dtest=" + testFile;
            case "javascript", "typescript" -> "npm test -- " + testFile;
            case "go" -> "go test ./...";
            default -> null;
        };
        if (cmd == null) {
            return null;
        }

        SandboxExecutor.Result result = sandbox.exec(cmd, "");
        if (!result.success()) {
            String output = result.stderr().isBlank() ? result.stdout() : result.stderr();
            return "测试失败（exit=" + result.exitCode() + "）：\n" + output;
        }
        return null;
    }
}
