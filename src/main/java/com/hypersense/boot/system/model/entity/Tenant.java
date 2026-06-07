package com.hypersense.boot.system.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 租户实体
 *
 * @author Ray Hao
 * @since 4.0.0
 */
@Data
@TableName(value = "sys_tenant")
public class Tenant {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 租户名称
     */
    private String name;

    /**
     * 租户编码（唯一）
     */
    private String code;

    /**
     * 联系人姓名
     */
    private String contactName;

    /**
     * 联系人电话
     */
    private String contactPhone;

    /**
     * 联系人邮箱
     */
    private String contactEmail;

    /**
     * 租户域名（用于域名识别）
     */
    private String domain;

    /**
     * 租户Logo
     */
    private String logo;

    /**
     * 套餐ID
     */
    @TableField(value = "plan_id")
    private Long planId;

    /**
     * 状态(1-正常 0-禁用)
     */
    private Integer status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 过期时间（NULL表示永不过期）
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime expireTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

