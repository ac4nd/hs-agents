package com.hypersense.boot.common.enums;

import com.hypersense.boot.common.base.IBaseEnum;
import lombok.Getter;

/**
 * 数据权限枚举
 *
 * @author Ray.Hao
 * @since 2.3.0
 */
@Getter
public enum DataScopeEnum implements IBaseEnum<Integer> {

    ALL(1, "所有数据"),
    DEPT_AND_SUB(2, "部门及子部门数据"),
    DEPT(3, "本部门数据"),
    SELF(4, "本人数据"),
    CUSTOM(5, "自定义部门数据");

    private final Integer value;

    private final String label;

    DataScopeEnum(Integer value, String label) {
        this.value = value;
        this.label = label;
    }

    public static boolean isAll(Integer value) {
        return ALL.getValue().equals(value);
    }

    public static DataScopeEnum getByValue(Integer value) {
        if (value == null) {
            return null;
        }
        for (DataScopeEnum dataScope : values()) {
            if (dataScope.getValue().equals(value)) {
                return dataScope;
            }
        }
        return null;
    }
}
