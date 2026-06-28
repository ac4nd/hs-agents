// src/main/java/com/hypersense/boot/framework/agents/profile/LintRule.java
package com.hypersense.boot.framework.agents.profile;

/**
 * 输出 lint 规则接口。
 * 各 Profile 可注册多条规则，对 LLM 输出（spec/源码/HTML）做自检。
 */
public interface LintRule {

    /** 规则 ID，例如 "no_purple_gradient" */
    String id();

    /** 规则说明（用于错误提示） */
    String description();

    /**
     * 执行 lint 检查。
     * @param input 待检文本（HTML/源码/JSON）
     * @return 通过返回 null；不通过返回错误描述
     */
    String check(String input);
}
