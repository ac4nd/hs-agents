package com.hypersense.boot.agents.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hypersense.boot.common.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * Agent 模板实体
 *
 * @author Claude
 * @since 2026/6/16
 */
@TableName("sys_agent_template")
@Getter
@Setter
@Schema(description = "Agent 模板实体")
public class AgentTemplate extends BaseEntity {

    /**
     * 所属用户 ID
     */
    @Schema(description = "所属用户 ID")
    private Long ownerUserId;

    /**
     * 名称
     */
    @Schema(description = "名称")
    private String name;

    /**
     * 指令文本
     */
    @Schema(description = "指令文本")
    private String instructions;

    /**
     * 子 Agent 配置(JSON)
     */
    @Schema(description = "子 Agent 配置(JSON)")
    private String subAgents;

    /**
     * 启用的工具(JSON)
     */
    @Schema(description = "启用的工具(JSON)")
    private String enabledTools;

    /**
     * 是否启用 HITL
     */
    @Schema(description = "是否启用 HITL 审批")
    private Boolean hitlEnabled;

    /**
     * 沙箱配置(JSON)
     */
    @Schema(description = "沙箱配置(JSON)")
    private String sandboxConfig;

    /**
     * 创建人 ID
     */
    @Schema(description = "创建人 ID")
    private Long createBy;

    /**
     * 更新人 ID
     */
    @Schema(description = "更新人 ID")
    private Long updateBy;

    /**
     * 是否删除(0-否 1-是)
     */
    @Schema(description = "是否删除: 0-否, 1-是")
    private Integer isDeleted;

}
