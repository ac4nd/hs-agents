package com.hypersense.boot.framework.agents.tool;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;

import java.util.HashMap;
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

    /**
     * 声明工具的 JSON Schema，供 LangChain4j function-call 协议使用。
     * <p>
     * 默认无参数 schema；子类按需覆写，声明 filename / content / query 等参数。
     * </p>
     */
    default ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(name())
                .description(description())
                .build();
    }

    /**
     * 从 LLM function-call 结果路由到 execute。
     * <p>
     * 解析 arguments JSON → Map → 调用 {@link #execute(Map)}。
     * </p>
     */
    default Object executeFromRequest(ToolExecutionRequest req) {
        return execute(parseArguments(req.arguments()));
    }

    /**
     * 工具方法：解析 LLM 返回的 arguments JSON 字符串为 Map。
     * <p>
     * 解析失败时兜底返回 raw 字段，避免工具调用整体崩溃。
     * </p>
     */
    static Map<String, Object> parseArguments(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }
        try {
            return cn.hutool.json.JSONUtil.parseObj(json).toBean(Map.class);
        } catch (Exception e) {
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("raw", json);
            return fallback;
        }
    }
}
