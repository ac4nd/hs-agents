package com.hypersense.boot.system.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 租户套餐实体
 *
 * @author Ray Hao
 * @since 4.0.0
 */
@Data
@TableName("sys_tenant_plan")
public class TenantPlan {

    /**
     * 套餐ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 套餐名称
     */
    private String name;

    /**
     * 套餐编码
     */
    private String code;

    /**
     * 状态(1-启用 0-停用)
     */
    private Integer status;

    /**
     * 排序
     */
    private Integer sort;

    /**
     * 备注
     */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
