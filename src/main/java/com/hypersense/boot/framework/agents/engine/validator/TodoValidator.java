package com.hypersense.boot.framework.agents.engine.validator;

import com.hypersense.boot.framework.agents.model.TodoItem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * TODO 校验器
 * <p>
 * 在 PlanNode 解析 LLM 输出的 TODO 列表后、写入 state 之前，对每个 TODO 执行两项校验：
 * </p>
 * <ol>
 *   <li><b>工具引用校验</b>：TODO 的描述必须显式引用一个工具名（file_write / file_read /
 *       internet_search / sandbox / reply_text / delegate），且该工具必须在
 *       「系统内置工具 + 当前 Agent 启用工具」的允许集合内。引用未注册工具会被拒绝。</li>
 *   <li><b>越界动词检测</b>：TODO 描述不得包含「直接保存 / 直接生成 / 直接执行 / 直接调用 /
 *       直接写入 / 直接返回」等动词。这些动词意味着 LLM 试图绕过工具直接产出结果，
 *       必须改为「使用 xxx 工具 ...」的表述。</li>
 * </ol>
 * <p>
 * 本类是纯静态工具类，无 Spring 依赖，便于单测与离线复用。
 * </p>
 *
 * @author Claude
 * @since 2026/6/25
 */
public final class TodoValidator {

    private TodoValidator() {
        // 工具类禁止实例化
    }

    /**
     * 系统内置工具白名单（reply_text 永远可用，因为废除 direct 后它是所有回复场景的兜底通道）。
     * delegate 是委派策略的虚拟工具名，不算实际工具但允许在 TODO 中引用。
     */
    private static final Set<String> BUILT_IN_TOOLS = Set.of(
            "file_write", "file_read", "read_file", "internet_search",
            "sandbox", "reply_text", "delegate"
    );

    /**
     * 越界动词列表。这些动词暗示 LLM 试图绕过工具直接产出结果，与「所有输出必经工具」原则相违背。
     * 检测到时给出修正提示，引导改为「使用 xxx 工具」。
     */
    private static final Set<String> FORBIDDEN_VERBS = Set.of(
            "直接保存", "直接生成", "直接执行", "直接调用", "直接写入", "直接返回"
    );

    /**
     * 禁止的路径前缀。LLM 编造 Linux 风格绝对路径时常见的开头。
     * 检测到则要求改为「保存为 xxx.html」（仅文件名，由沙箱自动安置）。
     */
    private static final List<String> FORBIDDEN_PATH_PREFIXES = List.of(
            "/home/user/", "/home/", "/tmp/", "/var/workspace/", "/var/",
            "/root/", "/opt/", "/workspace/", "~/", "/Users/"
    );

    /**
     * 校验 TODO 列表
     *
     * @param todos        待校验列表（可为 null 或空，返回 ok）
     * @param enabledTools 当前 Agent 启用的工具集合（可为 null，仅按内置白名单校验）
     * @return 校验结果（含错误信息列表）
     */
    public static ValidationResult validate(List<TodoItem> todos, Set<String> enabledTools) {
        if (todos == null || todos.isEmpty()) {
            return ValidationResult.ok();
        }

        // 允许的工具集合 = 内置工具 + 当前 Agent 启用工具
        Set<String> allowedTools = new HashSet<>(BUILT_IN_TOOLS);
        if (enabledTools != null) {
            allowedTools.addAll(enabledTools);
        }

        List<String> errors = new ArrayList<>();

        for (TodoItem t : todos) {
            if (t == null) {
                // 防御：列表中混入 null（理论上不会发生，但作为兜底）
                errors.add("TODO 列表包含 null 元素");
                continue;
            }
            // TodoItem 只有 description 字段，title 与 description 同义复用
            String description = safeStr(t.getDescription());
            String tool = extractToolName(description);

            // 工具引用校验
            if (tool == null) {
                errors.add("TODO [" + description + "] 未引用任何工具，必须明确使用工具完成"
                        + "（file_write / file_read / internet_search / sandbox / reply_text / delegate）");
            } else if (!allowedTools.contains(tool)) {
                errors.add("TODO [" + description + "] 引用了未注册的工具: " + tool
                        + "；请在系统内置工具集或当前 Agent 启用工具中选择");
            }

            // 越界动词检测
            for (String verb : FORBIDDEN_VERBS) {
                if (description.contains(verb)) {
                    errors.add("TODO [" + description + "] 含越界动词「" + verb + "」，"
                            + "请改为「使用 xxx 工具」的表述，所有操作必须经工具执行");
                }
            }

            // 路径前缀检测：禁止 LLM 编造 /home/user/ 等绝对路径
            for (String prefix : FORBIDDEN_PATH_PREFIXES) {
                if (description.contains(prefix)) {
                    errors.add("TODO [" + description + "] 含编造路径「" + prefix + "」，"
                            + "禁止使用任何路径前缀；改为「保存为 xxx.html」（仅文件名），"
                            + "系统会自动写入沙箱工作目录");
                }
            }
            // 兜底：检测反引号包裹的 Unix 绝对路径（如 `/xxx/yyy.html`）
            if (description.matches(".*`/[\\w./-]+\\.(html?|md|txt|json|py|js|css|csv)`.*")) {
                errors.add("TODO [" + description + "] 含编造的绝对路径，"
                        + "禁止使用任何路径前缀；改为「保存为 xxx.html」（仅文件名）");
            }
        }

        return errors.isEmpty() ? ValidationResult.ok() : ValidationResult.fail(errors);
    }

    /**
     * 从 TODO 描述中启发式提取工具名：扫描描述文本，匹配内置工具白名单中的任一工具名即返回。
     *
     * @param description TODO 描述
     * @return 命中的工具名；未命中返回 null
     */
    private static String extractToolName(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        for (String tool : BUILT_IN_TOOLS) {
            if (description.contains(tool)) {
                return tool;
            }
        }
        return null;
    }

    private static String safeStr(String s) {
        return s == null ? "" : s;
    }
}
