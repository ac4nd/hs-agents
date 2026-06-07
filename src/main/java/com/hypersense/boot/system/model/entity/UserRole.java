package com.hypersense.boot.system.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


/**
 * 用户和角色关联表
 *
 * @author Rya.Hao
 * @since 2022/12/17
 */
@TableName("sys_user_role")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserRole {

    public UserRole(Long userId,Long roleId){
        this.userId = userId;
        this.roleId = roleId;
    }

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 角色ID
     */
    private Long roleId;

    /**
     * 租户ID（多租户模式）
     */
    @TableField("tenant_id")
    @JsonInclude(value = JsonInclude.Include.NON_NULL)
    private Long tenantId;
}