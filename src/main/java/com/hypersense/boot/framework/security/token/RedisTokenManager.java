package com.hypersense.boot.framework.security.token;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.hypersense.boot.common.constant.RedisConstants;
import com.hypersense.boot.common.exception.BusinessException;
import com.hypersense.boot.common.result.ResultCode;
import com.hypersense.boot.framework.security.config.SecurityProperties;
import com.hypersense.boot.framework.security.model.AuthenticationToken;
import com.hypersense.boot.framework.security.model.OnlineUser;
import com.hypersense.boot.framework.security.model.SysUserDetails;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Redis Token 管理器
 *
 * @author Ray.Hao
 * @since 2024/11/15
 */
@ConditionalOnProperty(value = "security.session.type", havingValue = "redis-token")
@Service
public class RedisTokenManager implements TokenManager {

    private final SecurityProperties securityProperties;
    private final RedisTemplate<String, Object> redisTemplate;

    public RedisTokenManager(SecurityProperties securityProperties, RedisTemplate<String, Object> redisTemplate) {
        this.securityProperties = securityProperties;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public AuthenticationToken generateToken(Authentication authentication) {
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        String accessToken = IdUtil.fastSimpleUUID();
        String refreshToken = IdUtil.fastSimpleUUID();

        OnlineUser onlineUser = new OnlineUser(
                user.getUserId(),
                user.getUsername(),
                user.getDeptId(),
                user.getDataScopes(),
                user.getTenantId(),
                user.getCanSwitchTenant(),
                user.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toSet())
        );

        storeTokensInRedis(accessToken, refreshToken, onlineUser);
        handleSingleDeviceLogin(user.getUserId(), accessToken);

        return AuthenticationToken.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(securityProperties.getSession().getAccessTokenTimeToLive())
                .build();
    }

    @Override
    public Authentication parseToken(String token) {
        OnlineUser onlineUser = (OnlineUser) redisTemplate.opsForValue().get(formatTokenKey(token));
        if (onlineUser == null) return null;

        Set<SimpleGrantedAuthority> authorities = null;
        Set<String> roles = onlineUser.getRoles();
        if (CollectionUtil.isNotEmpty(roles)) {
            authorities = roles.stream()
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toSet());
        }

        SysUserDetails userDetails = buildUserDetails(onlineUser, authorities);
        return new UsernamePasswordAuthenticationToken(userDetails, null, authorities);
    }

    @Override
    public boolean validateToken(String token) {
        return redisTemplate.hasKey(formatTokenKey(token));
    }

    @Override
    public boolean validateRefreshToken(String refreshToken) {
        return redisTemplate.hasKey(formatRefreshTokenKey(refreshToken));
    }

    @Override
    public AuthenticationToken refreshToken(String refreshToken) {
        OnlineUser onlineUser = (OnlineUser) redisTemplate.opsForValue()
                .get(StrUtil.format(RedisConstants.Auth.REFRESH_TOKEN_USER, refreshToken));
        if (onlineUser == null) {
            throw new BusinessException(ResultCode.REFRESH_TOKEN_INVALID);
        }
        Object oldAccessTokenValue = redisTemplate.opsForValue().get(StrUtil.format(RedisConstants.Auth.USER_ACCESS_TOKEN, onlineUser.getUserId()));
        Optional.of(oldAccessTokenValue)
                .map(String.class::cast)
                .ifPresent(oldAccessToken -> redisTemplate.delete(formatTokenKey(oldAccessToken)));

        String newAccessToken = IdUtil.fastSimpleUUID();
        storeAccessToken(newAccessToken, onlineUser);

        int accessTtl = securityProperties.getSession().getAccessTokenTimeToLive();
        return AuthenticationToken.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken)
                .expiresIn(accessTtl)
                .build();
    }

    @Override
    public void invalidateToken(String token) {
        // Only delete the current token, not all user sessions
        // This ensures single-device logout doesn't affect other devices when allowMultiLogin=true
        redisTemplate.delete(formatTokenKey(token));
    }

    @Override
    public void invalidateUserSessions(Long userId) {
        if (userId == null) {
            return;
        }
        String userAccessKey = StrUtil.format(RedisConstants.Auth.USER_ACCESS_TOKEN, userId);
        Object accessTokenValue = redisTemplate.opsForValue().get(userAccessKey);
        if (accessTokenValue instanceof String accessToken) {
            redisTemplate.delete(formatTokenKey(accessToken));
        }
        redisTemplate.delete(userAccessKey);

        String userRefreshKey = StrUtil.format(RedisConstants.Auth.USER_REFRESH_TOKEN, userId);
        Object refreshTokenValue = redisTemplate.opsForValue().get(userRefreshKey);
        if (refreshTokenValue instanceof String refreshToken) {
            redisTemplate.delete(StrUtil.format(RedisConstants.Auth.REFRESH_TOKEN_USER, refreshToken));
        }
        redisTemplate.delete(userRefreshKey);
    }

    private void storeTokensInRedis(String accessToken, String refreshToken, OnlineUser onlineUser) {
        setRedisValue(formatTokenKey(accessToken), onlineUser, securityProperties.getSession().getAccessTokenTimeToLive());
        String refreshTokenKey = StrUtil.format(RedisConstants.Auth.REFRESH_TOKEN_USER, refreshToken);
        setRedisValue(refreshTokenKey, onlineUser, securityProperties.getSession().getRefreshTokenTimeToLive());
        setRedisValue(StrUtil.format(RedisConstants.Auth.USER_REFRESH_TOKEN, onlineUser.getUserId()),
                refreshToken,
                securityProperties.getSession().getRefreshTokenTimeToLive());
    }

    private void handleSingleDeviceLogin(Long userId, String accessToken) {
        Boolean allowMultiLogin = securityProperties.getSession().getRedisToken().getAllowMultiLogin();
        String userAccessKey = StrUtil.format(RedisConstants.Auth.USER_ACCESS_TOKEN, userId);
        if (!allowMultiLogin) {
            Object oldAccessTokenValue = redisTemplate.opsForValue().get(userAccessKey);
            if (oldAccessTokenValue instanceof String oldAccessToken) {
                redisTemplate.delete(formatTokenKey(oldAccessToken));
            }
        }
        setRedisValue(userAccessKey, accessToken, securityProperties.getSession().getAccessTokenTimeToLive());
    }

    private void storeAccessToken(String newAccessToken, OnlineUser onlineUser) {
        setRedisValue(StrUtil.format(RedisConstants.Auth.ACCESS_TOKEN_USER, newAccessToken), onlineUser, securityProperties.getSession().getAccessTokenTimeToLive());
        String userAccessKey = StrUtil.format(RedisConstants.Auth.USER_ACCESS_TOKEN, onlineUser.getUserId());
        setRedisValue(userAccessKey, newAccessToken, securityProperties.getSession().getAccessTokenTimeToLive());
    }

    private SysUserDetails buildUserDetails(OnlineUser onlineUser, Set<SimpleGrantedAuthority> authorities) {
        SysUserDetails userDetails = new SysUserDetails();
        userDetails.setUserId(onlineUser.getUserId());
        userDetails.setUsername(onlineUser.getUsername());
        userDetails.setDeptId(onlineUser.getDeptId());
        userDetails.setDataScopes(onlineUser.getDataScopes());
        userDetails.setTenantId(onlineUser.getTenantId());
        userDetails.setCanSwitchTenant(onlineUser.getCanSwitchTenant());
        if (authorities != null) {
            userDetails.setRoleCodes(authorities.stream()
                    .map(SimpleGrantedAuthority::getAuthority)
                    .collect(java.util.stream.Collectors.toSet()));
        }
        return userDetails;
    }

    private String formatTokenKey(String token) {
        return StrUtil.format(RedisConstants.Auth.ACCESS_TOKEN_USER, token);
    }

    private String formatRefreshTokenKey(String refreshToken) {
        return StrUtil.format(RedisConstants.Auth.REFRESH_TOKEN_USER, refreshToken);
    }

    private void setRedisValue(String key, Object value, int ttl) {
        if (ttl != -1) {
            redisTemplate.opsForValue().set(key, value, ttl, TimeUnit.SECONDS);
        } else {
            redisTemplate.opsForValue().set(key, value);
        }
    }
}
