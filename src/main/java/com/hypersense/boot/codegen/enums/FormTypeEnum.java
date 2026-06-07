package com.hypersense.boot.codegen.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.hypersense.boot.common.base.IBaseEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 表单类型枚举
 *
 * @author Ray
 * @since 2.10.0
 */
@Getter
@RequiredArgsConstructor
public enum FormTypeEnum implements IBaseEnum<Integer> {

    INPUT(1, "输入框"),
    SELECT(2, "下拉框"),
    RADIO(3, "单选框"),
    CHECK_BOX(4, "复选框"),
    INPUT_NUMBER(5, "数字输入框"),
    SWITCH(6, "开关"),
    TEXT_AREA(7, "文本域"),
    DATE(8, "日期框"),
    DATE_TIME(9, "日期时间框"),
    HIDDEN(10, "隐藏域");

    @EnumValue
    @JsonValue
    private final Integer value;
    private final String label;

    @JsonCreator
    public static FormTypeEnum fromValue(Integer value) {
        for (FormTypeEnum type : FormTypeEnum.values()) {
            if (type.getValue().equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("No enum constant with value " + value);
    }
}
