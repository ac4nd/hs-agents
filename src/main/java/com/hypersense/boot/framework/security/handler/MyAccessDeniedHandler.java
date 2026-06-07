package com.hypersense.boot.framework.security.handler;

import com.hypersense.boot.common.result.ResultCode;
import com.hypersense.boot.common.result.ResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 访问拒绝处理器
 *
 * @author Ray
 */
@Slf4j
@Component
public class MyAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException {
        log.warn("访问拒绝: {} - {}", accessDeniedException.getMessage(), request.getRequestURI());
        ResponseWriter.writeError(response, ResultCode.ACCESS_PERMISSION_EXCEPTION);
    }
}
