package com.hypersense.boot.system.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hypersense.boot.common.constant.RedisConstants;
import com.hypersense.boot.framework.tenant.TenantContextHolder;
import com.hypersense.boot.system.mapper.TenantMapper;
import com.hypersense.boot.system.mapper.RoleMenuMapper;
import com.hypersense.boot.system.model.dto.RolePermsDTO;
import com.hypersense.boot.system.model.entity.Tenant;
import com.hypersense.boot.system.model.entity.RoleMenu;
import com.hypersense.boot.system.service.RoleMenuService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 角色菜单服务实现类（多租户优化版）
 *
 * @author Ray.Hao
 * @since 2.5.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RoleMenuServiceImpl extends ServiceImpl<RoleMenuMapper, RoleMenu> implements RoleMenuService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final TenantMapper tenantMapper;

    /**
     * 构建租户权限缓存key
     *
     * @param tenantId 租户ID
     * @return 缓存key
     *         - system:role:perms:{tenantId}
     */
    private String buildRolePermsCacheKey(Long tenantId) {
        return RedisConstants.System.ROLE_PERMS + ":" + tenantId;
    }

    /**
     * 启动时初始化权限缓存
     */
    @PostConstruct
    public void initRolePermsCache() {
        log.info("开始初始化权限缓存...");

        // 强制多租户：启动阶段无租户上下文，按租户逐个初始化缓存
        List<Tenant> tenants = tenantMapper.selectList(null);
        if (CollectionUtil.isEmpty(tenants)) {
            log.warn("租户数据为空，跳过权限缓存初始化");
            return;
        }

        int total = 0;
        for (Tenant tenant : tenants) {
            if (tenant == null || tenant.getId() == null) {
                continue;
            }
            Long tenantId = tenant.getId();
            TenantContextHolder.setTenantId(tenantId);

            String cacheKey = buildRolePermsCacheKey(tenantId);
            redisTemplate.delete(cacheKey);

            List<RolePermsDTO> list = this.baseMapper.getRolePermsList(null);
            if (CollectionUtil.isEmpty(list)) {
                continue;
            }
            list.forEach(item -> {
                String roleCode = item.getRoleCode();
                Set<String> perms = item.getPerms();
                if (CollectionUtil.isNotEmpty(perms)) {
                    redisTemplate.opsForHash().put(cacheKey, roleCode, perms);
                }
            });
            total += list.size();
        }
        TenantContextHolder.clear();
        log.info("权限缓存初始化完成（强制多租户），共{}条数据", total);
    }

    /**
     * 刷新当前租户权限缓存
     */
    @Override
    public void refreshRolePermsCache() {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            log.warn("TenantId is null when refreshing role perms cache. Skip.");
            return;
        }
        String cacheKey = buildRolePermsCacheKey(tenantId);

        redisTemplate.delete(cacheKey);

        List<RolePermsDTO> list = this.baseMapper.getRolePermsList(null);
        if (CollectionUtil.isNotEmpty(list)) {
            list.forEach(item -> {
                String roleCode = item.getRoleCode();
                Set<String> perms = item.getPerms();
                if (CollectionUtil.isNotEmpty(perms)) {
                    redisTemplate.opsForHash().put(cacheKey, roleCode, perms);
                }
            });
        }

        log.info("租户[{}]权限缓存刷新完成", tenantId);
    }

    /**
     * 刷新单个角色权限缓存
     */
    @Override
    public void refreshRolePermsCache(String roleCode) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            log.warn("TenantId is null when refreshing role perms cache for role {}. Skip.", roleCode);
            return;
        }
        String cacheKey = buildRolePermsCacheKey(tenantId);

        redisTemplate.opsForHash().delete(cacheKey, roleCode);

        List<RolePermsDTO> list = this.baseMapper.getRolePermsList(roleCode);
        if (CollectionUtil.isNotEmpty(list)) {
            RolePermsDTO rolePerms = list.get(0);
            if (rolePerms != null) {
                Set<String> perms = rolePerms.getPerms();
                if (CollectionUtil.isNotEmpty(perms)) {
                    redisTemplate.opsForHash().put(cacheKey, roleCode, perms);
                }
            }
        }

        log.info("租户[{}]角色[{}]权限缓存刷新完成", tenantId, roleCode);
    }

    /**
     * 刷新权限缓存（角色编码变更时调用）
     */
    @Override
    public void refreshRolePermsCache(String oldRoleCode, String newRoleCode) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            log.warn("TenantId is null when refreshing role perms cache for role change {} -> {}. Skip.", oldRoleCode, newRoleCode);
            return;
        }
        String cacheKey = buildRolePermsCacheKey(tenantId);

        // 清理旧角色和新角色权限缓存
        redisTemplate.opsForHash().delete(cacheKey, oldRoleCode);
        redisTemplate.opsForHash().delete(cacheKey, newRoleCode);

        // 回源 DB 并更新新角色编码缓存
        List<RolePermsDTO> list = this.baseMapper.getRolePermsList(newRoleCode);
        if (CollectionUtil.isNotEmpty(list)) {
            RolePermsDTO rolePerms = list.get(0);
            if (rolePerms != null) {
                Set<String> perms = rolePerms.getPerms();
                if (CollectionUtil.isNotEmpty(perms)) {
                    redisTemplate.opsForHash().put(cacheKey, newRoleCode, perms);
                }
            }
        }

        log.info("租户[{}]角色编码变更: {} -> {}，相关权限缓存刷新完成", tenantId, oldRoleCode, newRoleCode);
    }

    /**
     * 获取角色权限集合
     *
     * @param roleCodes 角色编码集合
     * @return 权限集合
     */
    @Override
    public Set<String> getRolePermsByRoleCodes(Set<String> roleCodes) {
        if (CollectionUtil.isEmpty(roleCodes)) {
            return Collections.emptySet();
        }

        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            return Collections.emptySet();
        }

        String cacheKey = buildRolePermsCacheKey(tenantId);
        Set<String> perms = new HashSet<>();
        List<String> roleCodeList = new ArrayList<>(roleCodes);

        // 1. 尝试从缓存批量获取
        List<Object> cachedPermsList = redisTemplate.opsForHash().multiGet(cacheKey, new ArrayList<>(roleCodeList));

        List<String> missingRoles = new ArrayList<>();
        for (int i = 0; i < roleCodeList.size(); i++) {
            Object cachedPerms = cachedPermsList.get(i);
            String roleCode = roleCodeList.get(i);

            if (cachedPerms == null) {
                missingRoles.add(roleCode);
                continue;
            }

            // Redis JSON 序列化后，Set 会以 Collection 形式反序列化
            if (cachedPerms instanceof Collection<?> collection) {
                collection.stream()
                        .filter(Objects::nonNull)
                        .map(Object::toString)
                        .forEach(perms::add);
            } else {
                perms.add(cachedPerms.toString());
            }
        }

        // 2. 回源 DB 并同步到缓存
        if (!missingRoles.isEmpty()) {
            for (String roleCode : missingRoles) {
                Set<String> dbPerms = this.baseMapper.listRolePerms(Collections.singleton(roleCode));
                if (dbPerms == null) {
                    dbPerms = Collections.emptySet();
                }
                // 空集也写入，防止缓存穿透
                redisTemplate.opsForHash().put(cacheKey, roleCode, dbPerms);
                perms.addAll(dbPerms);
            }
        }

        return perms;
    }

    /**
     * 获取角色拥有的菜单ID集合
     *
     * @param roleId 角色ID
     * @return 菜单ID集合
     */
    @Override
    public List<Long> listMenuIdsByRoleId(Long roleId) {
        return this.baseMapper.listMenuIdsByRoleId(roleId);
    }

}
