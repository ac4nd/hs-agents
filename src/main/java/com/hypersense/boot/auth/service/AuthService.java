package com.hypersense.boot.auth.service;

import com.hypersense.boot.framework.captcha.model.CaptchaInfo;
import com.hypersense.boot.framework.security.model.AuthenticationToken;

/**
 * 认证服务接口
 *
 * @author Ray.Hao
 * @since 2.4.0
 */
public interface AuthService {

    /**
     * 登录（租户ID可选）。
     *
     * @param username 用户名
     * @param password 密码
     * @return 登录结果
     */
    default AuthenticationToken login(String username, String password) {
        return login(username, password, null);
    }

    /**
     * 登录
     *
     * @param username 用户名
     * @param password 密码
     * @param tenantId 租户ID（可选，多租户模式下用于指定租户）
     * @return 登录结果
     */
    AuthenticationToken login(String username, String password, Long tenantId);

    /**
     * 登出
     */
    void logout();

    /**
     * 获取验证码
     *
     * @return 验证码
     */
    CaptchaInfo getCaptcha();

    /**
     * 刷新令牌
     *
     * @param refreshToken 刷新令牌
     * @return 登录结果
     */
    AuthenticationToken refreshToken(String refreshToken);

    /**
     * 发送短信验证码
     *
     * @param mobile 手机号
     */
    void sendSmsLoginCode(String mobile);

    /**
     * 短信验证码登录
     *
     * @param mobile 手机号
     * @param code   验证码
     * @return 登录结果
     */
    AuthenticationToken loginBySms(String mobile, String code);
}
