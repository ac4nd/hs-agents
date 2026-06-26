-- ============================================================
-- 设计体系配置 菜单 + 按钮权限 DML
-- 适用：hs-agents 数据库（PostgreSQL 16.4）
-- 父菜单：系统管理 (id=2, tree_path='0,2')
-- 菜单 ID 段：2900（页面）+ 2901~2905（5 个按钮权限）
-- 创建时间：2026-06-24
-- 字段顺序（参考 sys_menu 表定义）：
--   id, parent_id, tree_path, name, type, route_name, route_path,
--   component, perm, always_show, keep_alive, visible, sort, icon,
--   redirect, create_time, update_time, params
-- ============================================================

BEGIN;

-- 清理已存在的同 ID 记录（幂等，便于重复执行）
DELETE FROM sys_role_menu WHERE menu_id IN (2900, 2901, 2902, 2903, 2904, 2905);
DELETE FROM sys_menu WHERE id IN (2900, 2901, 2902, 2903, 2904, 2905);

-- 主菜单（页面）
INSERT INTO sys_menu VALUES (
    2900, 2, '0,2', '设计体系配置', 'M',
    'DesignSystemConfig', 'design-system-config', 'system/design-system-config/index',
    NULL, NULL, 0, 1, 50, 'Palette', NULL,
    now(), now(), NULL
);

-- 按钮权限
INSERT INTO sys_menu VALUES (
    2901, 2900, '0,2,2900', '设计体系配置查询', 'B',
    NULL, '', NULL, 'sys:design-system-config:list',
    NULL, NULL, 1, 1, '', NULL,
    now(), now(), NULL
);

INSERT INTO sys_menu VALUES (
    2902, 2900, '0,2,2900', '设计体系配置新增', 'B',
    NULL, '', NULL, 'sys:design-system-config:create',
    NULL, NULL, 1, 2, '', NULL,
    now(), now(), NULL
);

INSERT INTO sys_menu VALUES (
    2903, 2900, '0,2,2900', '设计体系配置修改', 'B',
    NULL, '', NULL, 'sys:design-system-config:update',
    NULL, NULL, 1, 3, '', NULL,
    now(), now(), NULL
);

INSERT INTO sys_menu VALUES (
    2904, 2900, '0,2,2900', '设计体系配置删除', 'B',
    NULL, '', NULL, 'sys:design-system-config:delete',
    NULL, NULL, 1, 4, '', NULL,
    now(), now(), NULL
);

INSERT INTO sys_menu VALUES (
    2905, 2900, '0,2,2900', '设计体系配置发布', 'B',
    NULL, '', NULL, 'sys:design-system-config:publish',
    NULL, NULL, 1, 5, '', NULL,
    now(), now(), NULL
);

-- ============================================================
-- 角色关联：sys_role_menu(role_id, menu_id, tenant_id)
-- 超级管理员 role_id=1（ROOT，平台租户 0）
-- 系统管理员 role_id=2（ADMIN，平台租户 0）
-- ============================================================
INSERT INTO sys_role_menu VALUES
    (1, 2900, 0), (1, 2901, 0), (1, 2902, 0), (1, 2903, 0), (1, 2904, 0), (1, 2905, 0),
    (2, 2900, 0), (2, 2901, 0), (2, 2902, 0), (2, 2903, 0), (2, 2904, 0), (2, 2905, 0);

COMMIT;

-- ============================================================
-- 验证（可选执行）
-- ============================================================
-- SELECT id, name, type, perm FROM sys_menu WHERE id BETWEEN 2900 AND 2905 ORDER BY id;
-- SELECT role_id, menu_id FROM sys_role_menu WHERE menu_id BETWEEN 2900 AND 2905 ORDER BY role_id, menu_id;
