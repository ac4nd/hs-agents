package com.hypersense.boot.framework.agents.tool;

import java.util.Map;

/**
 * 工具提供者接口
 * <p>
 * 实现此接口注册自定义工具，供 Deep Agent 图引擎调用。
 * </p>
 *
 * @author Claude
 * @since 2026/5/15
 */
public interface ToolProvider {

    /**
     * 工具名称（全局唯一标识）
     */
    String name();

    /**
     * 工具描述（供 LLM 理解工具用途）
     */
    String description();

    /**
     * 执行工具
     *
     * @param params 输入参数
     * @return 执行结果
     */
    Object execute(Map<String, Object> params);
}
