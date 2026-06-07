package com.hypersense.boot.system.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.hypersense.boot.common.base.BaseEntity;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 系统配置对象
 *
 * @author Theo
 * @since 2024-07-29 11:17:26
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "系统配置")
@TableName("sys_config")
public class Config  {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 配置名称
     */
    private String configName;

    /**
     * 配置键
     */
    private String configKey;

    /**
     * 配置值
     */
    private String configValue;

    /**
     * 描述、备注
     */
    private String remark;

    /**
     * 创建人ID
     */
    private Long createBy;

    /**
     * 更新人ID
     */
    private Long updateBy;

    /**
     * 逻辑删除标识(0-未删除 1-已删除)
     */
    private Integer isDeleted;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    @JsonInclude(value = JsonInclude.Include.NON_NULL)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonInclude(value = JsonInclude.Include.NON_NULL)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;


}
