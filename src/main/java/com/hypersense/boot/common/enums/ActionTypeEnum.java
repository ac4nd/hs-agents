package com.hypersense.boot.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import com.hypersense.boot.common.base.IBaseEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/**
 * 操作类型枚举
 */
@Schema(enumAsRef = true)
@Getter
public enum ActionTypeEnum implements IBaseEnum<Integer> {

    LOGIN(1, "登录"),
    LOGOUT(2, "登出"),
    INSERT(3, "新增"),
    UPDATE(4, "修改"),
    DELETE(5, "删除"),
    GRANT(6, "授权"),
    EXPORT(7, "导出"),
    IMPORT(8, "导入"),
    UPLOAD(9, "上传"),
    DOWNLOAD(10, "下载"),
    CHANGE_PASSWORD(11, "修改密码"),
    RESET_PASSWORD(12, "重置密码"),
    ENABLE(13, "启用"),
    DISABLE(14, "禁用"),
    LIST(15, "查询列表"),
    OTHER(99, "其他");

    @EnumValue
    private final Integer value;

    @JsonValue
    private final String label;

    ActionTypeEnum(Integer value, String label) {
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
