package com.hypersense.boot.codegen.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.hypersense.boot.common.base.IBaseEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 查询类型枚举
 *
 * @author Ray
 * @since 2.10.0
 */
@Getter
@RequiredArgsConstructor
public enum QueryTypeEnum implements IBaseEnum<Integer> {

    EQ(1, "="),
    LIKE(2, "LIKE '%s%'"),
    IN(3, "IN"),
    BETWEEN(4, "BETWEEN"),
    GT(5, ">"),
    GE(6, ">="),
    LT(7, "<"),
    LE(8, "<="),
    NE(9, "!="),
    LIKE_LEFT(10, "LIKE '%s'"),
    LIKE_RIGHT(11, "LIKE 's%'");

    @EnumValue
    @JsonValue
    private final Integer value;
    private final String label;

    @JsonCreator
    public static QueryTypeEnum fromValue(Integer value) {
        for (QueryTypeEnum type : QueryTypeEnum.values()) {
            if (type.getValue().equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("No enum constant with value " + value);
    }
}
