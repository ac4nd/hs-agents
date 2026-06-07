package com.hypersense.boot.system.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hypersense.boot.framework.tenant.TenantContextHolder;
import com.hypersense.boot.system.mapper.RoleDeptMapper;
import com.hypersense.boot.system.model.entity.RoleDept;
import com.hypersense.boot.system.service.RoleDeptService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

/**
 * 角色部门关联服务实现
 *
 * @author Ray.Hao
 * @since 3.0.0
 */
@Service
@RequiredArgsConstructor
public class RoleDeptServiceImpl extends ServiceImpl<RoleDeptMapper, RoleDept> implements RoleDeptService {

    @Override
    public List<Long> getDeptIdsByRoleId(Long roleId) {
        if (roleId == null) {
            return Collections.emptyList();
        }
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            return Collections.emptyList();
        }
        return this.baseMapper.getDeptIdsByRoleId(tenantId, roleId);
    }

    @Override
    public List<Long> getDeptIdsByRoleCodes(List<String> roleCodes) {
        if (CollectionUtil.isEmpty(roleCodes)) {
            return Collections.emptyList();
        }
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            return Collections.emptyList();
        }
        return this.baseMapper.getDeptIdsByRoleCodes(tenantId, roleCodes);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveRoleDepts(Long roleId, List<Long> deptIds) {
        if (roleId == null || CollectionUtil.isEmpty(deptIds)) {
            return;
        }
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            return;
        }

        this.remove(new LambdaQueryWrapper<RoleDept>()
                .eq(RoleDept::getTenantId, tenantId)
                .eq(RoleDept::getRoleId, roleId));

        List<RoleDept> roleDepts = deptIds.stream()
                .map(deptId -> new RoleDept(tenantId, roleId, deptId))
                .toList();
        this.saveBatch(roleDepts);
    }

    @Override
    public void deleteByRoleId(Long roleId) {
        if (roleId == null) {
            return;
        }
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            return;
        }
        this.remove(new LambdaQueryWrapper<RoleDept>()
                .eq(RoleDept::getTenantId, tenantId)
                .eq(RoleDept::getRoleId, roleId));
    }

}
