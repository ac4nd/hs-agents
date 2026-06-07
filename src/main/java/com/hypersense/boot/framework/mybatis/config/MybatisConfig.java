package com.hypersense.boot.framework.mybatis.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.hypersense.boot.framework.mybatis.handler.MyMetaObjectHandler;
import com.hypersense.boot.framework.mybatis.interceptor.MyDataPermissionHandler;
import com.hypersense.boot.framework.mybatis.interceptor.MyTenantLineHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * mybatis-plus 配置类
 *
 * @author Ray.Hao
 * @since 2022/7/2
 */
@Configuration
@EnableTransactionManagement
public class MybatisConfig {

    @Autowired(required = false)
    private MyTenantLineHandler myTenantLineHandler;

    /**
     * 分页插件和数据权限插件
     * <p>
     * 如果启用了多租户，则添加多租户插件（必须在最前面）
     * </p>
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 多租户插件（强制启用，必须在最前面）
        if (myTenantLineHandler != null) {
            interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(myTenantLineHandler));
        }

        // 数据权限
        interceptor.addInnerInterceptor(new DataPermissionInterceptor(new MyDataPermissionHandler()));

        // 分页插件
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));

        return interceptor;
    }

    /**
     * 自动填充数据库创建人、创建时间、更新人、更新时间
     */
    @Bean
    public GlobalConfig globalConfig() {
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setMetaObjectHandler(new MyMetaObjectHandler());
        return globalConfig;
    }

}
