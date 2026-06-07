package com.hypersense.boot.framework.security.token;

import com.hypersense.boot.framework.security.model.AuthenticationToken;
import org.springframework.security.core.Authentication;

/**
 * Token 管理器接口
 *
 * @author Ray
 */
public interface TokenManager {

    /**
     * 生成令牌
     */
    AuthenticationToken generateToken(Authentication authentication);

    /**
     * 解析令牌
     */
    Authentication parseToken(String token);

    /**
     * 校验令牌
     */
    boolean validateToken(String token);

    /**
     * 校验刷新令牌
     */
    boolean validateRefreshToken(String refreshToken);

    /**
     * 使令牌失效
     */
    void invalidateToken(String token);

    /**
     * 使指定用户的所有会话失效
     */
    void invalidateUserSessions(Long userId);

    /**
     * 刷新令牌
     */
    AuthenticationToken refreshToken(String refreshToken);

}
