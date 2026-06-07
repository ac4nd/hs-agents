package com.hypersense.boot.framework.security.util;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.hypersense.boot.common.constant.SecurityConstants;
import com.hypersense.boot.common.constant.SystemConstants;
import com.hypersense.boot.framework.security.model.RoleDataScope;
import com.hypersense.boot.framework.security.model.SysUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Spring Security 工具类
 *
 * @author Ray
 * @since 2021/1/10
 */
public class SecurityUtils {

    public static Optional<SysUserDetails> getUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof SysUserDetails) {
                return Optional.of((SysUserDetails) principal);
            }
        }
        return Optional.empty();
    }

    public static Long getUserId() {
        return getUser().map(SysUserDetails::getUserId).orElse(null);
    }

    public static String getUsername() {
        return getUser().map(SysUserDetails::getUsername).orElse(null);
    }

    public static Long getDeptId() {
        return getUser().map(SysUserDetails::getDeptId).orElse(null);
    }

    public static List<RoleDataScope> getDataScopes() {
        return getUser().map(SysUserDetails::getDataScopes).orElse(Collections.emptyList());
    }

    public static boolean canSwitchTenant() {
        return getUser().map(SysUserDetails::getCanSwitchTenant).orElse(false);
    }

    public static Set<String> getRoles() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .map(Authentication::getAuthorities)
                .filter(CollectionUtil::isNotEmpty)
                .stream()
                .flatMap(Collection::stream)
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith(SecurityConstants.ROLE_PREFIX))
                .map(authority -> StrUtil.removePrefix(authority, SecurityConstants.ROLE_PREFIX))
                .collect(Collectors.toSet());
    }

    public static boolean isRoot() {
        Set<String> roles = getRoles();
        return roles.contains(SystemConstants.ROOT_ROLE_CODE);
    }

    public static String getAccessToken() {
        ServletRequestAttributes servletRequestAttributes = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes());
        if (Objects.isNull(servletRequestAttributes)) {
            return null;
        }
        HttpServletRequest request = servletRequestAttributes.getRequest();
        return request.getHeader(HttpHeaders.AUTHORIZATION);
    }

}
