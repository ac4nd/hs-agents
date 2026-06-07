package com.hypersense.boot.common.constant;

/**
 * 系统常量
 *
 * @author Ray.Hao
 * @since 1.0.0
 */
public interface SystemConstants {

    /**
     * 根节点ID
     */
    Long ROOT_NODE_ID = 0L;

    Long DEFAULT_TENANT_ID = 0L;

    /**
     * 平台租户ID（与默认租户一致）
     */
    Long PLATFORM_TENANT_ID = DEFAULT_TENANT_ID;

    Long PLATFORM_MENU_ID = 1L;

    String PLATFORM_ADMIN_USERNAME = "admin";

    String PLATFORM_ROOT_USERNAME = "root";

    String PLATFORM_ADMIN_ROLE_CODE = "ADMIN";

    /**
     * 租户切换权限标识
     */
    String TENANT_SWITCH_PERMISSION = "sys:tenant:switch";

    /**
     * 系统默认密码
     */
    String DEFAULT_PASSWORD = "123456";

    /**
     * 超级管理员角色编码
     */
    String ROOT_ROLE_CODE = "ROOT";


    /**
     * 系统配置 IP的QPS限流的KEY
     */
    String SYSTEM_CONFIG_IP_QPS_LIMIT_KEY = "IP_QPS_THRESHOLD_LIMIT";

}
