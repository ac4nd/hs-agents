package com.hypersense.boot.common.aspect;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.hypersense.boot.common.annotation.RepeatSubmit;
import com.hypersense.boot.common.constant.RedisConstants;
import com.hypersense.boot.common.constant.SecurityConstants;
import com.hypersense.boot.common.exception.BusinessException;
import com.hypersense.boot.common.result.ResultCode;
import com.hypersense.boot.common.util.IPUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.concurrent.TimeUnit;

/**
 * 防重复提交切面
 *
 * @author Ray.Hao
 * @since 2.3.0
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class RepeatSubmitAspect {

    private final RedissonClient redissonClient;

    @Pointcut("@annotation(repeatSubmit)")
    public void repeatSubmitPointCut(RepeatSubmit repeatSubmit) {
    }

    @Around(value = "repeatSubmitPointCut(repeatSubmit)", argNames = "pjp,repeatSubmit")
    public Object handleRepeatSubmit(ProceedingJoinPoint pjp, RepeatSubmit repeatSubmit) throws Throwable {
        String lockKey = buildLockKey();

        int expire = repeatSubmit.expire();
        RLock lock = redissonClient.getLock(lockKey);

        boolean locked = lock.tryLock(0, expire, TimeUnit.SECONDS);
        if (!locked) {
            throw new BusinessException(ResultCode.DUPLICATE_SUBMISSION);
        }
        return pjp.proceed();
    }

    private String buildLockKey() {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        String userIdentifier = getUserIdentifier(request);
        String requestIdentifier = StrUtil.join(":", request.getMethod(), request.getRequestURI());
        return StrUtil.format(RedisConstants.Lock.RESUBMIT, userIdentifier, requestIdentifier);
    }

    private String getUserIdentifier(HttpServletRequest request) {
        String userIdentifier;
        String tokenHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StrUtil.isNotBlank(tokenHeader) && tokenHeader.startsWith(SecurityConstants.BEARER_TOKEN_PREFIX)) {
            String rawToken = tokenHeader.substring(SecurityConstants.BEARER_TOKEN_PREFIX.length());
            userIdentifier = DigestUtil.sha256Hex(rawToken);
        } else {
            userIdentifier = IPUtils.getIpAddr(request);
        }
        return userIdentifier;
    }


}
