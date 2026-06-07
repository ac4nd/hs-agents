package com.hypersense.boot.framework.agents.sandbox;

/**
 * 沙箱抽象类
 * <p>
 * 提供代码执行、文件读写、命令执行的统一抽象。
 * 多种实现：LocalSandbox（本地子进程）、RemoteSandbox（远程云沙箱）、DockerSandbox / PodmanSandbox（容器沙箱）。
 * </p>
 *
 * @author Claude
 * @since 2026/5/19
 */
public abstract class Sandbox {

    /**
     * 沙箱类型标识（local / remote / custom）
     */
    public abstract String type();

    // ========== 代码执行 ==========

    /**
     * 在沙箱中执行代码
     *
     * @param language 编程语言（python / javascript / shell 等）
     * @param code     待执行代码
     * @param timeout  超时秒数（null 使用默认值）
     * @return 执行结果
     */
    public abstract SandboxResult executeCode(String language, String code, Integer timeout);

    // ========== 文件操作 ==========

    /**
     * 读取文件内容
     *
     * @param path 文件路径（沙箱内的相对路径或绝对路径）
     * @return 读取结果
     */
    public abstract SandboxResult readFile(String path);

    /**
     * 写入文件
     *
     * @param path    文件路径
     * @param content 文件内容
     * @return 写入结果
     */
    public abstract SandboxResult writeFile(String path, String content);

    /**
     * 编辑文件（行级编辑）
     * <p>
     * 支持两种编辑模式（互斥，优先使用 oldString/newString）：
     * </p>
     * <ol>
     *   <li><b>文本替换</b>：指定 oldString 和 newString，将文件中首次出现的 oldString 替换为 newString</li>
     *   <li><b>行级替换</b>：指定 startLine/endLine/newContent，将第 startLine 到第 endLine 行替换为 newContent</li>
     * </ol>
     *
     * @param path       文件路径
     * @param oldString  待替换的原始文本（文本替换模式，null 则使用行级模式）
     * @param newString  替换后的新文本（文本替换模式）
     * @param startLine  起始行号（1-based，行级替换模式）
     * @param endLine    结束行号（1-based，包含该行，行级替换模式）
     * @param newContent 新内容（行级替换模式，替换 startLine 到 endLine 的内容）
     * @return 编辑结果
     */
    public abstract SandboxResult editFile(String path, String oldString, String newString,
                                           Integer startLine, Integer endLine, String newContent);

    /**
     * 列出目录内容
     *
     * @param path 目录路径
     * @return 目录列表结果
     */
    public abstract SandboxResult listDirectory(String path);

    /**
     * 文件名模式搜索（glob）
     * <p>
     * 在指定目录下递归搜索匹配 glob 模式的文件。
     * </p>
     *
     * @param path    搜索起始目录
     * @param pattern glob 模式（如 <code>**&#47;*.py</code>, <code>*.json</code>, <code>src&#47;**&#47;*.java</code>）
     * @return 匹配的文件路径列表（每行一个路径，存放在 content 中）
     */
    public abstract SandboxResult searchFiles(String path, String pattern);

    /**
     * 文件内容正则搜索（grep）
     * <p>
     * 在指定路径下搜索包含匹配正则表达式内容的文件。
     * 当 path 为目录时递归搜索，为文件时只搜索该文件。
     * </p>
     *
     * @param path            搜索路径（文件或目录）
     * @param pattern         正则表达式
     * @param includePattern  文件名过滤（可选，如 <code>*.py</code>，null 表示不过滤）
     * @return 匹配结果（每行格式：文件路径:行号:匹配内容，存放在 content 中）
     */
    public abstract SandboxResult searchContent(String path, String pattern, String includePattern);

    // ========== 命令执行 ==========

    /**
     * 执行 Shell 命令
     *
     * @param command Shell 命令
     * @return 执行结果
     */
    public abstract SandboxResult executeCommand(String command);

    // ========== 生命周期 ==========

    /**
     * 初始化沙箱环境（可选覆写）
     */
    public void initialize() {
        // 默认空实现，子类按需覆写
    }

    /**
     * 销毁沙箱环境，释放资源（可选覆写）
     */
    public void destroy() {
        // 默认空实现，子类按需覆写
    }

    // ========== 编辑工具方法（所有 Sandbox 实现共用） ==========

    /**
     * 文本替换：将 content 中首次出现的 oldString 替换为 newString
     *
     * @throws IllegalArgumentException 如果 oldString 未找到
     */
    protected static String applyTextEdit(String content, String oldString, String newString) {
        int idx = content.indexOf(oldString);
        if (idx < 0) {
            throw new IllegalArgumentException("未找到要替换的文本，请确认 oldString 与文件内容完全一致");
        }
        return content.substring(0, idx) + newString + content.substring(idx + oldString.length());
    }

    /**
     * 行级替换：将第 startLine 到第 endLine 行（1-based，含两端）替换为 newContent
     *
     * @throws IllegalArgumentException 行号越界
     */
    protected static String applyLineEdit(String content, int startLine, int endLine, String newContent) {
        if (startLine < 1) {
            throw new IllegalArgumentException("startLine 不能小于 1，传入: " + startLine);
        }
        if (endLine < startLine) {
            throw new IllegalArgumentException(
                    "endLine 不能小于 startLine，startLine=" + startLine + ", endLine=" + endLine);
        }

        // 使用 split("\n", -1) 保留末尾空行，行数 = 分割后数组长度
        // 特殊情况：空文件 "" → [""] → 1 行（空行）
        String[] lines = content.split("\n", -1);
        if (startLine > lines.length) {
            throw new IllegalArgumentException(
                    "startLine 超出文件行数，文件共 " + lines.length + " 行，startLine=" + startLine);
        }

        int actualEnd = Math.min(endLine, lines.length);

        StringBuilder sb = new StringBuilder();
        // 保留 startLine 之前的行 [1, startLine-1]
        for (int i = 0; i < startLine - 1; i++) {
            sb.append(lines[i]).append('\n');
        }
        // 插入新内容
        sb.append(newContent);
        // 保留 endLine 之后的行 [actualEnd+1, lines.length]
        if (actualEnd < lines.length) {
            sb.append('\n');
            for (int i = actualEnd; i < lines.length; i++) {
                sb.append(lines[i]);
                if (i < lines.length - 1) {
                    sb.append('\n');
                }
            }
        }

        return sb.toString();
    }
}
