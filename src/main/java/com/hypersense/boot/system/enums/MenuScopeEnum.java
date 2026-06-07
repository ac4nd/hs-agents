package com.hypersense.boot.system.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.hypersense.boot.common.base.IBaseEnum;
import lombok.Getter;

/**
 * 菜单范围枚举
 *
 * @author Ray.Hao
 * @since 3.0.0
 */
@Getter
public enum MenuScopeEnum implements IBaseEnum<Integer> {

    PLATFORM(1, "平台菜单"),
    TENANT(2, "业务菜单");

    @EnumValue
    private final Integer value;

    private final String label;

    MenuScopeEnum(Integer value, String label) {
        this.value = value;
        this.label = label;
    }
}
