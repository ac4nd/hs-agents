package com.hypersense.boot.system.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Schema(description = "LLM模型配置VO")
public class LlmModelConfigVO {
    @Schema(description = "ID")
    private Long id;

    @Schema(description = "关联系统API-KEY配置ID")
    private Long apiKeyConfigId;

    @Schema(description = "API-KEY名称")
    private String keyName;

    @Schema(description = "模型名称")
    private String modelName;

    @Schema(description = "模型显示名称")
    private String modelDisplayName;

    @Schema(description = "上下文窗口大小")
    private Integer contextWindowSize;

    @Schema(description = "最大输出Token数")
    private Integer maxOutputTokens;

    @Schema(description = "模型能力标签")
    private String modelCapabilities;

    @Schema(description = "默认温度参数")
    private BigDecimal temperature;

    @Schema(description = "默认Top-P参数")
    private BigDecimal topP;

    @Schema(description = "是否启用流式输出")
    private Integer isStreaming;

    @Schema(description = "状态")
    private Integer status;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
