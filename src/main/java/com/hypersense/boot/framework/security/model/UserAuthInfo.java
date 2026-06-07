package com.hypersense.boot.framework.security.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;

/**
 * 用户认证信息
 *
 * @author Ray
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAuthInfo {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 头像
     */
    private String avatar;

    /**
     * 手机号
     */
    private String mobile;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 性别
     */
    private Integer gender;

    /**
     * 部门ID
     */
    private Long deptId;

    /**
     * 租户ID
     */
    private Long tenantId;

    /**
     * 密码（加密后）
     */
    private String password;

    /**
     * 状态（1:启用 其它:禁用）
     */
    private Integer status;

    /**
     * 认证类型（账号密码/短信/微信）
     */
    private String authType;

    /**
     * 认证标识（账号/手机号/微信openid）
     */
    private String identifier;

    /**
     * 密码/短信验证码/令牌
     */
    private String credential;

    /**
     * 验证码
     */
    private String captcha;

    /**
     * 验证码Key
     */
    private String captchaKey;

    /**
     * 角色集合
     */
    private Set<String> roles;

    /**
     * 数据权限列表
     */
    private List<RoleDataScope> dataScopes;

    /**
     * 是否可切换租户
     */
    private Boolean canSwitchTenant;

}
