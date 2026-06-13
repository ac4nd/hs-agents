package com.hypersense.boot.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import com.hypersense.boot.common.base.IBaseEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/**
 * 日志模块枚举
 */
@Schema(enumAsRef = true)
@Getter
public enum LogModuleEnum implements IBaseEnum<Integer> {

    LOGIN(1, "登录"),
    USER(2, "用户管理"),
    ROLE(3, "角色管理"),
    DEPT(4, "部门管理"),
    MENU(5, "菜单管理"),
    DICT(6, "字典管理"),
    CONFIG(7, "系统配置"),
    FILE(8, "文件管理"),
    NOTICE(9, "通知公告"),
    LOG(10, "日志管理"),
    CODEGEN(11, "代码生成"),
    AGENT(12, "AI智能体"),
    LLM_CONFIG(13, "LLM配置"),
    OTHER(99, "其他");

    @EnumValue
    private final Integer value;

    @JsonValue
    private final String label;

    LogModuleEnum(Integer value, String label) {
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
