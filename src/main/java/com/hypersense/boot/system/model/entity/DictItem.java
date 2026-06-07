package com.hypersense.boot.system.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 字典项实体对象
 *
 * @author Ray.Hao
 * @since 2022/12/17
 */
@TableName("sys_dict_item")
@Data
public class DictItem {

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
     * 字典项名称
     */
    private String label;

    /**
     * 字典项值
     */
    private String value;

    /**
     * 排序
     */
    private Integer sort;

    /**
     * 状态（1-正常，0-禁用）
     */
    private Integer status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 标签类型
     */
    private String tagType;

    /**
     * 创建人 ID
     */
    private Long createBy;

    /**
     * 更新人 ID
     */
    private Long updateBy;
}
