package com.hypersense.boot.common.base;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 基础实体类
 *
 * <p>实体类的基类，包含了实体类的公共属性，如创建时间、更新时间、逻辑删除标识等</p>
 * <p>多租户模式下，会自动添加 tenant_id 字段（通过 MyMetaObjectHandler 自动填充）</p>
 *
 * @author Ray.Hao
 * @since 2024/6/23
 */
@Data
public class BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 租户ID（多租户模式下由拦截器自动填充）
     */
    @TableField("tenant_id")
    @JsonIgnore
    private Long tenantId;

    @TableField(fill = FieldFill.INSERT)
    @JsonInclude(value = JsonInclude.Include.NON_NULL)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonInclude(value = JsonInclude.Include.NON_NULL)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

}
