-- ============================================================
-- 场景模板配置菜单 + 按钮权限 DML（幂等）
-- 数据库：godlikeagents / schema：public
-- 主菜单 ID：2910  |  按钮 ID：2911 ~ 2917
-- 执行前 MAX(id) = 2905，本段范围 2910-2917 未被使用
-- 授权角色：role_id=1 (ROOT 超级管理员) + role_id=2 (ADMIN 系统管理员)
-- ============================================================

BEGIN;

-- ---------- 1. 幂等清理 ----------
DELETE FROM sys_role_menu WHERE menu_id BETWEEN 2910 AND 2917;
DELETE FROM sys_menu WHERE id BETWEEN 2910 AND 2917;

-- ---------- 2. 主菜单（系统管理子菜单，sort=51，紧跟 notice sort=9 之后） ----------
INSERT INTO sys_menu
  (id, parent_id, tree_path, name, type, route_name, route_path, component,
   perm, always_show, keep_alive, visible, sort, icon, scope, create_time, update_time)
VALUES
  (2910, 2, '0,2', '场景模板配置', 'M', 'SceneTemplateConfig', 'scene-template-config',
   'system/scene-template-config/index', NULL, 0, 1, 1, 51, 'Palette', 2,
   NOW(), NOW());

-- ---------- 3. 按钮权限（type='B'） ----------
INSERT INTO sys_menu
  (id, parent_id, tree_path, name, type, route_name, route_path, component,
   perm, always_show, keep_alive, visible, sort, icon, scope, create_time, update_time)
VALUES
  (2911, 2910, '0,2,2910', '查询',     'B', NULL, '', NULL, 'sys:scene-template-config:list',      NULL, NULL, 1, 1, '', 2, NOW(), NOW()),
  (2912, 2910, '0,2,2910', '详情',     'B', NULL, '', NULL, 'sys:scene-template-config:detail',    NULL, NULL, 1, 2, '', 2, NOW(), NOW()),
  (2913, 2910, '0,2,2910', '新增',     'B', NULL, '', NULL, 'sys:scene-template-config:create',    NULL, NULL, 1, 3, '', 2, NOW(), NOW()),
  (2914, 2910, '0,2,2910', '修改',     'B', NULL, '', NULL, 'sys:scene-template-config:update',    NULL, NULL, 1, 4, '', 2, NOW(), NOW()),
  (2915, 2910, '0,2,2910', '删除',     'B', NULL, '', NULL, 'sys:scene-template-config:delete',    NULL, NULL, 1, 5, '', 2, NOW(), NOW()),
  (2916, 2910, '0,2,2910', '发布',     'B', NULL, '', NULL, 'sys:scene-template-config:publish',   NULL, NULL, 1, 6, '', 2, NOW(), NOW()),
  (2917, 2910, '0,2,2910', '取消发布', 'B', NULL, '', NULL, 'sys:scene-template-config:unpublish', NULL, NULL, 1, 7, '', 2, NOW(), NOW());

-- ---------- 4. 角色授权（ROOT + ADMIN） ----------
INSERT INTO sys_role_menu (role_id, menu_id, tenant_id) VALUES
  (1, 2910, NULL), (1, 2911, NULL), (1, 2912, NULL), (1, 2913, NULL),
  (1, 2914, NULL), (1, 2915, NULL), (1, 2916, NULL), (1, 2917, NULL),
  (2, 2910, NULL), (2, 2911, NULL), (2, 2912, NULL), (2, 2913, NULL),
  (2, 2914, NULL), (2, 2915, NULL), (2, 2916, NULL), (2, 2917, NULL);

COMMIT;

-- ---------- 5. 验证（手动执行） ----------
-- SELECT id, name, type, perm, sort FROM sys_menu WHERE id BETWEEN 2910 AND 2917 ORDER BY id;
-- SELECT role_id, menu_id FROM sys_role_menu WHERE menu_id BETWEEN 2910 AND 2917 ORDER BY role_id, menu_id;
