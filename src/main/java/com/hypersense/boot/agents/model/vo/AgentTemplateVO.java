package com.hypersense.boot.agents.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 模板视图对象
 *
 * @author Claude
 * @since 2026/6/16
 */
@Schema(description = "Agent 模板视图对象")
@Data
public class AgentTemplateVO {

    @Schema(description = "模板ID")
    private Long id;

    @Schema(description = "所属用户ID")
    private Long ownerUserId;

    @Schema(description = "所属用户名")
    private String ownerUserName;

    @Schema(description = "名称")
    private String name;

    @Schema(description = "指令文本")
    private String instructions;

    @Schema(description = "子 Agent 配置(JSON)")
    private String subAgents;

    @Schema(description = "启用的工具(JSON)")
    private String enabledTools;

    @Schema(description = "是否启用 HITL 审批")
    private Boolean hitlEnabled;

    @Schema(description = "沙箱配置(JSON)")
    private String sandboxConfig;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

}
