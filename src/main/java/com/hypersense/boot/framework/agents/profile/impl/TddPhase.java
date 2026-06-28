// src/main/java/com/hypersense/boot/framework/agents/profile/impl/TddPhase.java
package com.hypersense.boot.framework.agents.profile.impl;

/**
 * code-profile 的 TDD 状态机阶段（spec §3.5 + §4.4）。
 *
 * READ  → TEST  → TEST_HITL → IMPL  → EXEC  → LINT
 *                                       ↑       ↓
 *                                       └── 失败 (≤3 次)
 *
 * 状态机约束：
 * - READ 后才能进入 TEST（必须先读已有代码）
 * - TEST → TEST_HITL 强制中断等待用户审批测试方向
 * - LINT 失败 → 回 IMPL（≤3 次）→ HITL
 */
public enum TddPhase {
    /** 读现有相关代码 */
    READ("读现有相关代码"),
    /** 写失败测试 */
    TEST("写失败测试"),
    /** 测试方向 HITL 审批 */
    TEST_HITL("测试方向等待用户审批"),
    /** 实现源码 */
    IMPL("实现源码"),
    /** sandbox_exec 跑测试 */
    EXEC("sandbox 执行测试"),
    /** lint + 失败回 IMPL ≤3 次 */
    LINT("lint 校验"),
    /** 完成 */
    DONE("完成"),
    /** HITL 用户放弃或失败超阈值 */
    ABORTED("中止");

    private final String description;

    TddPhase(String desc) { this.description = desc; }
    public String description() { return description; }
}
