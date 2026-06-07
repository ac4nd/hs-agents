package com.hypersense.boot.auth.controller;

import com.hypersense.boot.framework.captcha.model.CaptchaInfo;
import com.hypersense.boot.auth.model.LoginReq;
import com.hypersense.boot.common.enums.ActionTypeEnum;
import com.hypersense.boot.common.enums.LogModuleEnum;
import com.hypersense.boot.common.result.Result;
import com.hypersense.boot.auth.service.AuthService;
import com.hypersense.boot.common.annotation.Log;
import com.hypersense.boot.common.result.ResultCode;
import com.hypersense.boot.framework.security.model.SysUserDetails;
import com.hypersense.boot.framework.security.model.AuthenticationToken;
import com.hypersense.boot.framework.security.token.TokenManager;
import com.hypersense.boot.system.model.entity.User;
import com.hypersense.boot.system.service.TenantService;
import com.hypersense.boot.system.service.UserService;
import com.hypersense.boot.framework.security.model.UserAuthInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.util.Objects;
import java.util.List;

/**
 * 认证控制层
 *
 * @author Ray.Hao
 * @since 2022/10/16
 */
@Tag(name = "01.认证中心")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final TenantService tenantService;
    private final PasswordEncoder passwordEncoder;
    private final TokenManager tokenManager;

    @Operation(summary = "获取验证码")
    @GetMapping("/captcha")
    public Result<CaptchaInfo> getCaptcha() {
        CaptchaInfo captcha = authService.getCaptcha();
        return Result.success(captcha);
    }

    @Operation(summary = "账号密码登录")
    @PostMapping("/login")
    @Log(module = LogModuleEnum.LOGIN, value = ActionTypeEnum.LOGIN)
    public Result<?> login(HttpServletRequest httpServletRequest, @RequestBody @Valid LoginReq request) {
        String username = request.getUsername();
        String password = request.getPassword();
        Long tenantId = request.getTenantId();

        // 多租户模式：如果指定了租户ID，直接验证该租户下的密码
        if (tenantId != null) {
            AuthenticationToken authenticationToken = authService.login(username, password, tenantId);
            return Result.success(authenticationToken);
        }

        // 多租户模式：未指定租户ID，优先从域名解析租户并直接登录（子域名场景）
        String domain = httpServletRequest != null ? httpServletRequest.getServerName() : null;
        if (domain != null) {
            Long tenantIdFromDomain = tenantService.getTenantIdByDomain(domain);
            if (tenantIdFromDomain != null) {
                AuthenticationToken authenticationToken = authService.login(username, password, tenantIdFromDomain);
                return Result.success(authenticationToken);
            }
        }

        // 多租户模式：未指定租户ID且无法从域名解析，查询该用户名在所有租户下的账户
        List<User> users = userService.listUsersByUsernameAcrossAllTenants(username);

        // 为避免账号枚举与租户信息泄露，此处对外统一返回"账号或密码错误"
        if (users.isEmpty()) {
            return Result.failed("账号或密码错误");
        }

        // 过滤出正常状态的用户
        List<User> activeUsers = users.stream()
                .filter(user -> user.getStatus() != null && user.getStatus() == 1)
                .toList();

        if (activeUsers.isEmpty()) {
            return Result.failed("账号或密码错误");
        }

        // 关键：只有当密码校验通过后，才允许进入"选择租户"分支，防止租户列表被探测
        List<User> passwordMatchedUsers = activeUsers.stream()
                .filter(user -> Objects.nonNull(user.getPassword()) && passwordEncoder.matches(password, user.getPassword()))
                .toList();

        if (passwordMatchedUsers.isEmpty()) {
            return Result.failed("账号或密码错误");
        }

        // 优先可切换租户账号登录：具备租户切换权限的账号可登录后显式切换租户
        for (User candidate : passwordMatchedUsers) {
            UserAuthInfo authInfo = userService.getAuthInfoByUsernameInTenant(username, candidate.getTenantId());
            if (authInfo != null && Boolean.TRUE.equals(authInfo.getCanSwitchTenant())) {
                AuthenticationToken authenticationToken = authService.login(username, password, candidate.getTenantId());
                return Result.success(authenticationToken);
            }
        }

        // 无租户切换权限：仅允许唯一租户账号登录
        if (passwordMatchedUsers.size() == 1) {
            User user = passwordMatchedUsers.get(0);
            AuthenticationToken authenticationToken = authService.login(username, password, user.getTenantId());
            return Result.success(authenticationToken);
        }

        return Result.failed("账号归属多个租户，请使用租户域名或指定租户登录");
    }

    @Operation(summary = "切换租户")
    @PostMapping("/switch-tenant")
    public Result<AuthenticationToken> switchTenant(@RequestParam Long tenantId) {
        if (!tenantService.hasTenantSwitchPermission()) {
            return Result.failed("无权限");
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof SysUserDetails details)) {
            return Result.failed(ResultCode.ACCESS_TOKEN_INVALID);
        }

        boolean canAccess = tenantService.canAccessTenant(details.getUserId(), tenantId);
        if (!canAccess) {
            return Result.failed("无权限");
        }

        SysUserDetails newDetails = new SysUserDetails();
        newDetails.setUserId(details.getUserId());
        newDetails.setUsername(details.getUsername());
        newDetails.setDeptId(details.getDeptId());
        newDetails.setDataScopes(details.getDataScopes());
        newDetails.setTenantId(tenantId);
        newDetails.setCanSwitchTenant(details.getCanSwitchTenant());

        Authentication newAuth = new UsernamePasswordAuthenticationToken(newDetails, authentication.getCredentials(), authentication.getAuthorities());
        AuthenticationToken token = tokenManager.generateToken(newAuth);
        return Result.success(token);
    }

    @Operation(summary = "短信验证码登录")
    @PostMapping("/login/sms")
    @Log(module = LogModuleEnum.LOGIN, value = ActionTypeEnum.LOGIN)
    public Result<AuthenticationToken> loginBySms(
            @Parameter(description = "手机号", example = "18888888888") @RequestParam String mobile,
            @Parameter(description = "验证码", example = "1234") @RequestParam String code
    ) {
        AuthenticationToken loginResult = authService.loginBySms(mobile, code);
        return Result.success(loginResult);
    }

    @Operation(summary = "发送登录短信验证码")
    @PostMapping("/sms/code")
    public Result<Void> sendLoginVerifyCode(
            @Parameter(description = "手机号", example = "18888888888") @RequestParam String mobile
    ) {
        authService.sendSmsLoginCode(mobile);
        return Result.success();
    }


    @Operation(summary = "退出登录")
    @DeleteMapping("/logout")
    @Log(module = LogModuleEnum.LOGIN, value = ActionTypeEnum.LOGOUT)
    public Result<?> logout() {
        authService.logout();
        return Result.success();
    }

    @Operation(summary = "刷新令牌")
    @PostMapping("/refresh-token")
    public Result<?> refreshToken(
            @Parameter(description = "刷新令牌", example = "xxx.xxx.xxx") @RequestParam String refreshToken
    ) {
        AuthenticationToken authenticationToken = authService.refreshToken(refreshToken);
        return Result.success(authenticationToken);
    }

}
