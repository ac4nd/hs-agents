package com.hypersense.boot.system.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 字典实体
 *
 * @author Ray.Hao
 * @since 2022/12/17
 */
@TableName("sys_dict")
@Data
public class Dict {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 字典编码
     */
    private String dictCode;

    /**
     * 字典名称
     */
    private String name;

    /**
     * 状态（1：启用, 0：停用）
     */
    private Integer status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 逻辑删除标识(0-未删除 1-已删除)
     */
    private Integer isDeleted;

}
