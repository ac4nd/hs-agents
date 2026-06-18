package com.hypersense.boot.framework.agents.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * Agent 模型选择列表项（前端切换模型用）。
 * <p>
 * 由 sys_llm_model_config + sys_llm_api_key_config + sys_llm_vendor_config 联合查询得出，
 * 仅包含前端选择模型所需的字段，避免暴露 API Key / endpoint 等敏感信息。
 * </p>
 *
 * @author Claude
 * @since 2026/6/18
 */
@Data
@Builder
@Schema(description = "Agent 模型选项")
public class LlmModelOptionVO {

    @Schema(description = "模型配置 ID（sys_llm_model_config.id）")
    private Long modelConfigId;

    @Schema(description = "模型名称（厂商原始名，如 glm-4）")
    private String modelName;

    @Schema(description = "模型显示名称（如 GLM-4旗舰模型）")
    private String modelDisplayName;

    @Schema(description = "厂商编码（如 ZHIPU/DEEPSEEK/OPENAI）")
    private String vendorCode;

    @Schema(description = "厂商名称（如 智谱AI）")
    private String vendorName;

    @Schema(description = "模型能力标签 JSON 数组（如 [\"chat\",\"function_call\"]）")
    private String modelCapabilities;

    @Schema(description = "上下文窗口大小（token）")
    private Integer contextWindowSize;

    @Schema(description = "最大输出 token 数")
    private Integer maxOutputTokens;
}
