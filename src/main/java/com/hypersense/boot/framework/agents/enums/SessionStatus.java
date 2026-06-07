package com.hypersense.boot.framework.agents.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import com.hypersense.boot.common.base.IBaseEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/**
 * Agent 会话状态枚举
 *
 * @author Claude
 * @since 2026/5/18
 */
@Schema(enumAsRef = true)
@Getter
public enum SessionStatus implements IBaseEnum<Integer> {

    CREATED(0, "CREATED"),
    RUNNING(1, "RUNNING"),
    COMPLETED(2, "COMPLETED"),
    FAILED(3, "FAILED"),
    /** 图已暂停（HITL 中断） */
    INTERRUPTED(4, "INTERRUPTED"),
    /** 已通知前端，等待人工输入 */
    AWAITING_INPUT(5, "AWAITING_INPUT");

    @EnumValue
    private final Integer value;

    @JsonValue
    private final String label;

    SessionStatus(Integer value, String label) {
        this.value = value;
        this.label = label;
    }

    @Override
    public Integer getValue() {
        return this.value;
    }

    @Override
    public String getLabel() {
        return this.label;
    }
}
