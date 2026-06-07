package com.hypersense.boot.framework.security.model;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ObjectUtil;
import com.hypersense.boot.common.constant.SecurityConstants;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 用户详情
 *
 * @author Ray
 */
@Data
@NoArgsConstructor
public class SysUserDetails implements UserDetails {

    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 部门ID
     */
    private Long deptId;

    /**
     * 租户ID
     */
    private Long tenantId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;

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
     * 状态
     */
    private Integer status;

    /**
     * 租户能否切换
     */
    private Boolean canSwitchTenant;

    /**
     * 角色编码集合
     */
    private Set<String> roleCodes;

    /**
     * 数据权限列表
     */
    private List<RoleDataScope> dataScopes;

    /**
     * 构造函数：根据用户认证信息初始化用户详情对象
     *
     * @param user 用户认证信息对象
     */
    public SysUserDetails(UserAuthInfo user) {
        this.userId = user.getUserId();
        this.username = user.getUsername();
        this.password = user.getPassword();
        this.deptId = user.getDeptId();
        this.tenantId = user.getTenantId();
        this.nickname = user.getNickname();
        this.avatar = user.getAvatar();
        this.mobile = user.getMobile();
        this.email = user.getEmail();
        this.gender = user.getGender();
        this.status = user.getStatus();
        this.canSwitchTenant = user.getCanSwitchTenant();
        this.dataScopes = user.getDataScopes();
        this.roleCodes = user.getRoles();

        this.authorities = CollectionUtil.isNotEmpty(user.getRoles())
                ? user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(SecurityConstants.ROLE_PREFIX + role))
                .collect(Collectors.toSet())
                : Collections.emptySet();
    }

    /**
     * 角色权限集合
     */
    private Collection<SimpleGrantedAuthority> authorities;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (authorities != null) {
            return authorities;
        }
        if (roleCodes == null || roleCodes.isEmpty()) {
            return Collections.emptySet();
        }
        return roleCodes.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return this.status != null && this.status == 1;
    }
}
