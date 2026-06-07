package com.hypersense.boot.framework.mybatis.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.hypersense.boot.framework.tenant.TenantContextHolder;
import com.hypersense.boot.framework.tenant.TenantProperties;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * mybatis-plus 字段自动填充
 */
@Component
@RequiredArgsConstructor
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Autowired(required = false)
    private TenantProperties tenantProperties;

    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createTime", LocalDateTime::now, LocalDateTime.class);
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime::now, LocalDateTime.class);

        if (tenantProperties != null) {
            Long tenantId = TenantContextHolder.getTenantId();
            if (tenantId != null) {
                Long finalTenantId = tenantId;
                this.strictInsertFill(metaObject, "tenantId", () -> finalTenantId, Long.class);
            }
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime::now, LocalDateTime.class);
    }

}
