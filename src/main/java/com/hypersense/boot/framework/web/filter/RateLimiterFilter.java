package com.hypersense.boot.framework.web.filter;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.StrUtil;
import com.hypersense.boot.common.constant.RedisConstants;
import com.hypersense.boot.common.constant.SystemConstants;
import com.hypersense.boot.common.result.ResultCode;
import com.hypersense.boot.common.util.IPUtils;
import com.hypersense.boot.common.result.ResponseWriter;
import com.hypersense.boot.system.service.ConfigService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * IP 限流过滤器
 *
 * @author Theo
 * @since 2024/08/10 14:38
 */
@Slf4j
public class RateLimiterFilter extends OncePerRequestFilter {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ConfigService configService;

    private static final long DEFAULT_IP_LIMIT = 10L;

    public RateLimiterFilter(RedisTemplate<String, Object> redisTemplate, ConfigService configService) {
        this.redisTemplate = redisTemplate;
        this.configService = configService;
    }

    public boolean rateLimit(String ip) {
        String key = StrUtil.format(RedisConstants.RateLimiter.IP, ip);

        Long count = redisTemplate.opsForValue().increment(key);
        if (count == null || count == 1) {
            redisTemplate.expire(key, 1, TimeUnit.SECONDS);
        }

        Object systemConfig = configService.getSystemConfig(SystemConstants.SYSTEM_CONFIG_IP_QPS_LIMIT_KEY);
        if (systemConfig == null) {
            log.warn("系统未配置限流阈值，跳过限流");
            return false;
        }

        long limit = Convert.toLong(systemConfig, DEFAULT_IP_LIMIT);
        return count != null && count > limit;
    }

    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response,
                                    @NotNull FilterChain filterChain) throws ServletException, IOException {
        String ip = IPUtils.getIpAddr(request);

        if (rateLimit(ip)) {
            ResponseWriter.writeError(response, ResultCode.REQUEST_CONCURRENCY_LIMIT_EXCEEDED);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
