package com.hypersense.boot.framework.agents.engine.validator;

import java.util.*;
import java.util.regex.*;

/**
 * FinalizeNode 输出审计器
 * 检测 LLM 总结中的编造路径前缀、Windows 绝对路径、未生成文件引用
 */
public final class FinalizeAuditor {
    private FinalizeAuditor() {}

    /** 编造路径前缀（LLM 训练数据常见，但本系统不用） */
    private static final Set<String> FORBIDDEN_PREFIXES = Set.of(
        "/home/user/", "/home/", "/tmp/", "/var/workspace/", "/var/", "/root/", "~/"
    );

    /** Linux 风格文件路径 */
    private static final Pattern LINUX_PATH = Pattern.compile(
        "(/[\\w./-]+\\.(html?|md|txt|json|py|js|ts|css|csv|xml|yml|yaml|java))"
    );

    /** Windows 绝对路径（不应出现在用户输出中） */
    private static final Pattern WIN_PATH = Pattern.compile(
        "([A-Za-z]:\\\\[\\w\\\\.-]+\\.(html?|md|txt|json|py|js|ts|css|csv|xml|yml|yaml|java))"
    );

    /**
     * 审计 Finalize 输出
     * @param output LLM 生成的最终回复
     * @param stateFiles 已生成的文件名集合
     * @return 审计结果（含警告列表）
     */
    public static AuditResult audit(String output, Set<String> stateFiles) {
        if (output == null || output.isEmpty()) {
            return new AuditResult(Collections.emptyList());
        }
        List<String> warnings = new ArrayList<>();
        Set<String> existing = stateFiles == null ? Collections.emptySet() : stateFiles;

        for (String prefix : FORBIDDEN_PREFIXES) {
            if (output.contains(prefix)) {
                warnings.add("检测到编造路径前缀: " + prefix + "（请使用 workspace/ 相对路径）");
            }
        }

        Matcher winMatcher = WIN_PATH.matcher(output);
        while (winMatcher.find()) {
            warnings.add("输出中含 Windows 绝对路径: " + winMatcher.group() + "（应使用 workspace/ 相对路径）");
        }

        Matcher m = LINUX_PATH.matcher(output);
        Set<String> checked = new HashSet<>();
        while (m.find()) {
            String mentionedPath = m.group();
            if (checked.contains(mentionedPath)) continue;
            checked.add(mentionedPath);

            String filename = extractFilename(mentionedPath);
            boolean validAsWorkspacePath = mentionedPath.startsWith("/workspace/")
                || mentionedPath.startsWith("/uploads/");
            boolean inExistingFile = existing.contains(filename);

            if (!validAsWorkspacePath && !inExistingFile) {
                warnings.add("输出引用了未生成的文件: " + mentionedPath);
            }
        }

        return new AuditResult(warnings);
    }

    private static String extractFilename(String path) {
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    /** 审计结果 */
    public static class AuditResult {
        private final List<String> warnings;
        public AuditResult(List<String> warnings) {
            this.warnings = warnings == null ? Collections.emptyList() : Collections.unmodifiableList(warnings);
        }
        public boolean hasWarnings() { return !warnings.isEmpty(); }
        public List<String> getWarnings() { return warnings; }
        public String joinedWarnings() { return String.join("; ", warnings); }
    }
}
