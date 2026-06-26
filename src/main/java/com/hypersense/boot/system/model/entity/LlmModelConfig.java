package com.hypersense.boot.system.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.hypersense.boot.common.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;

@TableName("sys_llm_model_config")
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "LLM模型配置实体")
public class LlmModelConfig extends BaseEntity {

    @Schema(description = "关联系统API-KEY配置ID")
    private Long apiKeyConfigId;

    @Schema(description = "模型名称")
    private String modelName;

    @Schema(description = "模型显示名称")
    private String modelDisplayName;

    @Schema(description = "上下文窗口大小(token数)")
    private Integer contextWindowSize;

    @Schema(description = "最大输出Token数")
    private Integer maxOutputTokens;

    @Schema(description = "模型能力标签(JSON数组)")
    private String modelCapabilities;

    @Schema(description = "默认温度参数")
    private BigDecimal temperature;

    @Schema(description = "默认Top-P参数")
    private BigDecimal topP;

    @Schema(description = "是否启用流式输出(0-否 1-是)")
    private Integer isStreaming;

    /** 是否支持多模态图片输入：1-支持 0-不支持 */
    @TableField("supports_vision")
    @Schema(description = "是否支持多模态图片输入(0-否 1-是)")
    private Integer supportsVision;

    @Schema(description = "状态(1-启用 0-禁用)")
    private Integer status;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "备注")
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    @TableLogic
    private Integer isDeleted;
}
