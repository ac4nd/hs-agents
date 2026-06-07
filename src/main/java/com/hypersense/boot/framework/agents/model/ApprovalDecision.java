package com.hypersense.boot.framework.agents.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 审批决策枚举
 * <p>
 * 人工审批 HITL 流程中的决策类型：
 * <ul>
 *   <li>APPROVED — 批准继续执行</li>
 *   <li>REJECTED — 拒绝，触发重新规划</li>
 *   <li>MODIFIED — 修改参数后批准</li>
 * </ul>
 *
 * @author Claude
 * @since 2026/5/23
 */
@Getter
public enum ApprovalDecision {

    APPROVED("approved"),
    REJECTED("rejected"),
    MODIFIED("modified");

    @JsonValue
    private final String value;

    ApprovalDecision(String value) {
        this.value = value;
    }

    /**
     * 从 value 反序列化
     */
    @JsonCreator
    public static ApprovalDecision fromValue(String value) {
        for (ApprovalDecision d : values()) {
            if (d.value.equals(value)) {
                return d;
            }
        }
        throw new IllegalArgumentException("Unknown ApprovalDecision: " + value);
    }
}
