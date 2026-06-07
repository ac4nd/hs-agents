package com.hypersense.boot.framework.security.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 认证令牌
 *
 * @author Ray
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthenticationToken {

    /**
     * 访问令牌
     */
    private String accessToken;

    /**
     * 刷新令牌
     */
    private String refreshToken;

    /**
     * 令牌类型
     */
    private String tokenType;

    /**
     * 过期时间(秒)
     */
    private Integer expiresIn;

}
