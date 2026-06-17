package com.hypersense.boot.agents.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 项目视图对象
 *
 * @author Claude
 * @since 2026/6/16
 */
@Schema(description = "项目视图对象")
@Data
public class ProjectVO {

    @Schema(description = "项目ID")
    private Long id;

    @Schema(description = "所属用户ID")
    private Long ownerUserId;

    @Schema(description = "所属用户名")
    private String ownerUserName;

    @Schema(description = "项目名称")
    private String name;

    @Schema(description = "项目描述")
    private String description;

    @Schema(description = "沙箱类型")
    private String sandboxType;

    @Schema(description = "状态")
    private Integer status;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

}
