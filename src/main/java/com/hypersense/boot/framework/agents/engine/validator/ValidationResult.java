package com.hypersense.boot.framework.agents.engine.validator;

import java.util.Collections;
import java.util.List;

/**
 * TODO 校验结果
 * <p>
 * 不可变值对象，承载 {@link TodoValidator#validate} 的判定输出：
 * </p>
 * <ul>
 *   <li>{@link #isValid()} — 是否通过校验（true=无错误）</li>
 *   <li>{@link #getErrors()} — 错误信息列表（不可变视图，无错误时为空列表）</li>
 *   <li>{@link #joinedErrors()} — 错误信息用分号拼接的单行字符串（便于反馈给 LLM）</li>
 * </ul>
 *
 * @author Claude
 * @since 2026/6/25
 */
public class ValidationResult {

    private final boolean valid;
    private final List<String> errors;

    /**
     * @param valid  是否通过校验
     * @param errors 错误信息列表；null 会被替换为空列表，非 null 会被包装为不可变视图
     */
    public ValidationResult(boolean valid, List<String> errors) {
        this.valid = valid;
        this.errors = errors == null ? Collections.emptyList() : Collections.unmodifiableList(errors);
    }

    /** 快捷工厂：通过校验（无错误） */
    public static ValidationResult ok() {
        return new ValidationResult(true, Collections.emptyList());
    }

    /** 快捷工厂：未通过（带错误列表） */
    public static ValidationResult fail(List<String> errors) {
        return new ValidationResult(false, errors);
    }

    public boolean isValid() {
        return valid;
    }

    public List<String> getErrors() {
        return errors;
    }

    /**
     * 把所有错误用分号 + 空格拼接为单行字符串，便于作为 feedback 注入 LLM prompt。
     *
     * @return 拼接后的错误描述；无错误时返回空串
     */
    public String joinedErrors() {
        return errors.isEmpty() ? "" : String.join("; ", errors);
    }
}
