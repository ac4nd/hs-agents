package com.hypersense.boot.framework.agents.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import com.hypersense.boot.common.base.IBaseEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/**
 * TODO 状态枚举
 *
 * @author Claude
 * @since 2026/5/15
 */
@Schema(enumAsRef = true)
@Getter
public enum TodoStatus implements IBaseEnum<Integer> {

    PENDING(0, "待处理"),
    IN_PROGRESS(1, "进行中"),
    COMPLETED(2, "已完成"),
    FAILED(3, "失败");

    @EnumValue
    private final Integer value;

    @JsonValue
    private final String label;

    TodoStatus(Integer value, String label) {
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
