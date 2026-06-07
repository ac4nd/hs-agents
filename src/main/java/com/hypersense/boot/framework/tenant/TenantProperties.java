package com.hypersense.boot.framework.tenant;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 多租户配置属性
 *
 * @author Ray.Hao
 * @since 3.0.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "tenant")
public class TenantProperties {

    /**
     * 租户字段名
     * 默认：tenant_id
     */
    private String column = "tenant_id";

    /**
     * 忽略多租户过滤的表名列表
     * 系统表、租户表等不需要租户隔离的表
     */
    private List<String> ignoreTables = new ArrayList<>();

}

