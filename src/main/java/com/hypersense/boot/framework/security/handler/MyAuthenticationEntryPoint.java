package com.hypersense.boot.framework.security.handler;

import com.hypersense.boot.common.result.ResultCode;
import com.hypersense.boot.common.result.ResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 认证失败处理器
 *
 * @author Ray
 */
@Slf4j
@Component
public class MyAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException {
        log.warn("认证失败: {} - {}", authException.getMessage(), request.getRequestURI());
        ResponseWriter.writeError(response, ResultCode.ACCESS_UNAUTHORIZED);
    }
}
