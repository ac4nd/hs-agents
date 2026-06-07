package com.hypersense.boot.common.result;

import cn.hutool.extra.servlet.JakartaServletUtil;
import cn.hutool.json.JSONUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;

/**
 * Web响应写入器
 * <p>
 * 用于在过滤器、Security处理器等无法使用 @RestControllerAdvice 的场景中统一写入HTTP响应。
 */
@Slf4j
public final class ResponseWriter {

    private ResponseWriter() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    public static void writeSuccess(HttpServletResponse response, Object data) {
        writeResult(response, Result.success(data), HttpStatus.OK.value());
    }

    public static void writeSuccess(HttpServletResponse response) {
        writeSuccess(response, null);
    }

    public static void writeError(HttpServletResponse response, ResultCode resultCode) {
        writeError(response, resultCode, null);
    }

    public static void writeError(HttpServletResponse response, ResultCode resultCode, String message) {
        Result<?> result = message == null
                ? Result.failed(resultCode)
                : Result.failed(resultCode, message);

        int httpStatus = mapHttpStatus(resultCode);
        writeResult(response, result, httpStatus);
    }

    private static void writeResult(HttpServletResponse response, Result<?> result, int httpStatus) {
        try {
            response.setStatus(httpStatus);
            response.setCharacterEncoding(StandardCharsets.UTF_8.toString());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            JakartaServletUtil.write(response,
                    JSONUtil.toJsonStr(result),
                    MediaType.APPLICATION_JSON_VALUE
            );

        } catch (Exception e) {
            log.error("写入响应时发生未知异常: httpStatus={}, result={}", httpStatus, result, e);
        }
    }

    private static int mapHttpStatus(ResultCode resultCode) {
        return switch (resultCode) {
            case ACCESS_UNAUTHORIZED,
                 ACCESS_TOKEN_INVALID,
                 REFRESH_TOKEN_INVALID -> HttpStatus.UNAUTHORIZED.value();
            case ACCESS_PERMISSION_EXCEPTION -> HttpStatus.FORBIDDEN.value();
            default -> HttpStatus.BAD_REQUEST.value();
        };
    }
}
