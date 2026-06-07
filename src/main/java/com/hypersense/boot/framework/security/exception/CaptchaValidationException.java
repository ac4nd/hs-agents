package com.hypersense.boot.framework.security.exception;

/**
 * 验证码校验异常
 *
 * @author Ray
 */
public class CaptchaValidationException extends RuntimeException {

    public CaptchaValidationException(String message) {
        super(message);
    }
}
