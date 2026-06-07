package com.hypersense.boot.framework.agents.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 子 Agent 定义
 *
 * @author Claude
 * @since 2026/5/15
 */
@Schema(description = "子 Agent 定义")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubAgentDefinition {

    /** 子 Agent 名称 */
    @Schema(description = "子 Agent 名称")
    private String name;

    /** 描述（用于路由决策） */
    @Schema(description = "描述")
    private String description;

    /** 系统提示词 */
    @Schema(description = "系统提示词")
    private String systemPrompt;

    /** 可用工具列表 */
    @Schema(description = "可用工具列表")
    private List<String> availableTools;

    /** 执行超时（秒），默认 120 */
    @Schema(description = "执行超时（秒）")
    private Long timeoutSeconds;

    /** 子 Agent 图递归限制，默认 15 */
    @Schema(description = "子 Agent 图递归限制")
    private Integer recursionLimit;

    /** 子 Agent 最大嵌套深度，默认 1（不可再委派） */
    @Schema(description = "子 Agent 最大嵌套深度")
    private Integer maxDepth;
}
