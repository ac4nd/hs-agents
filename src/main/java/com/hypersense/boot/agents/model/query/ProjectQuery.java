package com.hypersense.boot.agents.model.query;

import com.hypersense.boot.common.base.BaseQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 项目分页查询对象
 *
 * @author Claude
 * @since 2026/6/16
 */
@Schema(description = "项目分页查询对象")
@Data
public class ProjectQuery extends BaseQuery {

    @Schema(description = "关键字(项目名称)")
    private String keywords;

    @Schema(description = "沙箱类型")
    private String sandboxType;

    @Schema(description = "状态")
    private Integer status;

}
