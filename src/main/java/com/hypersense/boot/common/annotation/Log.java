package com.hypersense.boot.common.annotation;

import com.hypersense.boot.common.enums.ActionTypeEnum;
import com.hypersense.boot.common.enums.LogModuleEnum;

import java.lang.annotation.*;

/**
 * 日志注解
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface Log {

    /**
     * 模块
     */
    LogModuleEnum module();

    /**
     * 操作类型
     */
    ActionTypeEnum value();

    /**
     * 操作标题（可选，默认使用枚举描述）
     */
    String title() default "";

    /**
     * 自定义日志内容（可选，用于记录操作细节）
     */
    String content() default "";

}
