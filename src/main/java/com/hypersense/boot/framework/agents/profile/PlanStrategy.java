package com.hypersense.boot.framework.agents.profile;

/**
 * 能力档位的 Plan 策略枚举。
 * 每个策略对应一类 PlanNode 的 TODO 拆分与 HITL 触发模式。
 */
public enum PlanStrategy {
    /** design-profile：先 outline + 1 页 demo → HITL → 批量 */
    OUTLINE_DEMO("OUTLINE_DEMO"),
    /** code-profile：读代码 → 写失败测试 → HITL → 实现 → 测试 → lint */
    TDD("TDD"),
    /** think-profile：发散调研 → 收敛结论 → 结构化计划 */
    DIVERGE_THEN_STRUCTURE("DIVERGE_THEN_STRUCTURE"),
    /** docs-profile：outline → 板块撰写 */
    OUTLINE_THEN_FILL("OUTLINE_THEN_FILL"),
    /** learning-profile：评估水平 → 路径 → 由浅入深 */
    LAYERED_LEARNING("LAYERED_LEARNING"),
    /** 通用兜底 */
    GENERIC("GENERIC");

    private final String strategy;

    PlanStrategy(String strategy) {
        this.strategy = strategy;
    }

    public String strategy() {
        return strategy;
    }

    public static PlanStrategy fromString(String value) {
        if (value == null) return GENERIC;
        for (PlanStrategy s : values()) {
            if (s.strategy().equalsIgnoreCase(value)) return s;
        }
        return GENERIC;
    }
}
