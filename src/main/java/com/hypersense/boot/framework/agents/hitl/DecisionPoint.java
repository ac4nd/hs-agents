package com.hypersense.boot.framework.agents.hitl;

/**
 * HITL Gate 决策点
 *
 * @author Claude
 * @since 2026/6/17
 */
public enum DecisionPoint {

    /** PlanNode 输出 TODO 计划后 */
    PLAN_COMPLETED("plan_completed"),

    /** ExecuteNode 选定 TODO 后、实际执行前 */
    BEFORE_TODO_EXECUTE("before_todo_execute");

    private final String value;

    DecisionPoint(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
