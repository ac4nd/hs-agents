package com.hypersense.boot.agents.model.query;

import com.hypersense.boot.common.base.BaseQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Agent 模板分页查询对象
 *
 * @author Claude
 * @since 2026/6/16
 */
@Schema(description = "Agent 模板分页查询对象")
@Data
public class AgentTemplateQuery extends BaseQuery {

    @Schema(description = "关键字(名称)")
    private String keywords;

    @Schema(description = "是否启用 HITL 审批")
    private Boolean hitlEnabled;

}
