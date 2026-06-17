package com.hypersense.boot.agents.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hypersense.boot.common.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 项目实体
 *
 * @author Claude
 * @since 2026/6/16
 */
@TableName("sys_project")
@Getter
@Setter
@Schema(description = "项目实体")
public class Project extends BaseEntity {

    /**
     * 所属用户 ID
     */
    @Schema(description = "所属用户 ID")
    private Long ownerUserId;

    /**
     * 项目名称
     */
    @Schema(description = "项目名称")
    private String name;

    /**
     * 项目描述
     */
    @Schema(description = "项目描述")
    private String description;

    /**
     * 沙箱类型
     */
    @Schema(description = "沙箱类型: local-sandbox-本地沙箱, remote-cloud-远程云端, third-party-api-第三方 API")
    private String sandboxType;

    /**
     * 状态(1-正常 0-禁用)
     */
    @Schema(description = "状态: 1-正常, 0-禁用")
    private Integer status;

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
