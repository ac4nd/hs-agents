-- ============================================================
-- 一次性数据迁移：brand_spec v1 → v2.0
-- 表：sys_design_system_config, sys_design_system_config_template
--
-- 背景：
--   sys_design_system_config.brand_spec / sys_design_system_config_template.brand_spec
--   均为 JSONB。Java 端 BrandSpecMigrator 已实现读时迁移，本脚本做一次性批量升级，
--   让存量数据全部转为 v2.0，避免每次读取都走迁移路径。
--
-- 迁移规则（必须与 com.hypersense.boot.system.util.BrandSpecMigrator#parseAndMigrate 一致）：
--   colors.primary   → colors.identity.primary   缺失用 #1A56DB
--   colors.text      → colors.identity.neutral   缺失用 #1F2937
--   colors.accent    → colors.identity.accent    缺失用 #F97316
--   colors.secondary / colors.background          丢弃（v2 由 identity 派生）
--   font（字符串）→ fonts.body / fonts.display   追加 ", system-ui, sans-serif" fallback
--                                                 （仅当原值不包含 system-ui 时追加；
--                                                  Java 端只判断 system-ui，不判断 sans-serif）
--   colors.semantic                                 固定 {success,warning,danger,info}
--   fonts.mono / baseSize / scaleRatio / weight / lineHeight / tracking  固定默认值
--   radius                                          固定 {sm,md,lg,xl,2xl}
--   logo                                            保留原值
--   version                                         固定 "2.0"
--
-- 幂等：已是 v2.0 的行（brand_spec->>'version' = '2.0'）不动；可重复执行。
-- 安全：整脚本包裹在单个事务中，失败自动回滚；不修改表结构，不 ALTER TABLE。
--
-- 执行前请备份：
--   pg_dump -t sys_design_system_config -t sys_design_system_config_template > backup_brand_spec.sql
-- ============================================================

BEGIN;

-- ------------------------------------------------------------
-- 0. font fallback 表达式（v1 font 字符串 → v2 fonts.display / fonts.body）
-- ------------------------------------------------------------
-- 与 Java 端 BrandSpecMigrator.parseAndMigrate 一致：
--   - 缺失/空：用 v2 默认 'Geist, Inter, system-ui, sans-serif'
--   - 原值已含 system-ui（大小写不敏感）：原样使用
--   - 否则：末尾追加 ", system-ui, sans-serif"
-- 注意：Java 端 fallback 判断仅检查 system-ui，不检查 sans-serif，
--       本脚本保持一致，避免迁移结果与读时迁移产生差异。

-- ============================================================
-- 1. 主表：sys_design_system_config
-- ============================================================
UPDATE sys_design_system_config
SET brand_spec = jsonb_build_object(
    'version', '2.0',
    'colors', jsonb_build_object(
        'identity', jsonb_build_object(
            'primary', COALESCE(NULLIF(brand_spec->'colors'->>'primary', ''), '#1A56DB'),
            'accent',  COALESCE(NULLIF(brand_spec->'colors'->>'accent',  ''), '#F97316'),
            'neutral', COALESCE(NULLIF(brand_spec->'colors'->>'text',     ''), '#1F2937')
        ),
        'semantic', jsonb_build_object(
            'success', '#16A34A',
            'warning', '#F59E0B',
            'danger',  '#DC2626',
            'info',    '#0EA5E9'
        )
    ),
    'fonts', jsonb_build_object(
        'display', CASE
            WHEN brand_spec->>'font' IS NULL OR btrim(brand_spec->>'font') = '' THEN
                'Geist, Inter, system-ui, sans-serif'
            WHEN position(lower('system-ui') IN lower(brand_spec->>'font')) > 0 THEN
                brand_spec->>'font'
            ELSE
                brand_spec->>'font' || ', system-ui, sans-serif'
        END,
        'body', CASE
            WHEN brand_spec->>'font' IS NULL OR btrim(brand_spec->>'font') = '' THEN
                'Geist, Inter, system-ui, sans-serif'
            WHEN position(lower('system-ui') IN lower(brand_spec->>'font')) > 0 THEN
                brand_spec->>'font'
            ELSE
                brand_spec->>'font' || ', system-ui, sans-serif'
        END,
        'mono',        'JetBrains Mono, ui-monospace, monospace',
        'baseSize',    16,
        'scaleRatio',  1.25,
        'weight',      jsonb_build_object('regular', 400, 'medium', 500, 'semibold', 600, 'bold', 700),
        'lineHeight',  jsonb_build_object('tight', 1.25, 'normal', 1.5, 'relaxed', 1.625),
        'tracking',    jsonb_build_object('tight', '-0.025em', 'normal', '0', 'wide', '0.025em')
    ),
    'radius', jsonb_build_object(
        'sm',  4,
        'md',  6,
        'lg',  8,
        'xl',  12,
        '2xl', 16
    ),
    'logo', COALESCE(brand_spec->>'logo', '')
)
WHERE brand_spec IS NOT NULL
  AND jsonb_typeof(brand_spec) = 'object'
  AND (brand_spec->>'version' IS NULL OR brand_spec->>'version' <> '2.0');

-- ============================================================
-- 2. 模板表：sys_design_system_config_template（同样逻辑）
-- ============================================================
UPDATE sys_design_system_config_template
SET brand_spec = jsonb_build_object(
    'version', '2.0',
    'colors', jsonb_build_object(
        'identity', jsonb_build_object(
            'primary', COALESCE(NULLIF(brand_spec->'colors'->>'primary', ''), '#1A56DB'),
            'accent',  COALESCE(NULLIF(brand_spec->'colors'->>'accent',  ''), '#F97316'),
            'neutral', COALESCE(NULLIF(brand_spec->'colors'->>'text',     ''), '#1F2937')
        ),
        'semantic', jsonb_build_object(
            'success', '#16A34A',
            'warning', '#F59E0B',
            'danger',  '#DC2626',
            'info',    '#0EA5E9'
        )
    ),
    'fonts', jsonb_build_object(
        'display', CASE
            WHEN brand_spec->>'font' IS NULL OR btrim(brand_spec->>'font') = '' THEN
                'Geist, Inter, system-ui, sans-serif'
            WHEN position(lower('system-ui') IN lower(brand_spec->>'font')) > 0 THEN
                brand_spec->>'font'
            ELSE
                brand_spec->>'font' || ', system-ui, sans-serif'
        END,
        'body', CASE
            WHEN brand_spec->>'font' IS NULL OR btrim(brand_spec->>'font') = '' THEN
                'Geist, Inter, system-ui, sans-serif'
            WHEN position(lower('system-ui') IN lower(brand_spec->>'font')) > 0 THEN
                brand_spec->>'font'
            ELSE
                brand_spec->>'font' || ', system-ui, sans-serif'
        END,
        'mono',        'JetBrains Mono, ui-monospace, monospace',
        'baseSize',    16,
        'scaleRatio',  1.25,
        'weight',      jsonb_build_object('regular', 400, 'medium', 500, 'semibold', 600, 'bold', 700),
        'lineHeight',  jsonb_build_object('tight', 1.25, 'normal', 1.5, 'relaxed', 1.625),
        'tracking',    jsonb_build_object('tight', '-0.025em', 'normal', '0', 'wide', '0.025em')
    ),
    'radius', jsonb_build_object(
        'sm',  4,
        'md',  6,
        'lg',  8,
        'xl',  12,
        '2xl', 16
    ),
    'logo', COALESCE(brand_spec->>'logo', '')
)
WHERE brand_spec IS NOT NULL
  AND jsonb_typeof(brand_spec) = 'object'
  AND (brand_spec->>'version' IS NULL OR brand_spec->>'version' <> '2.0');

-- ============================================================
-- 3. 统计迁移结果（informational，不阻塞事务）
-- ============================================================
SELECT 'sys_design_system_config' AS table_name,
       count(*) FILTER (WHERE brand_spec IS NOT NULL
                          AND brand_spec->>'version' = '2.0')                              AS v2_count,
       count(*) FILTER (WHERE brand_spec IS NOT NULL
                          AND (brand_spec->>'version' IS NULL OR brand_spec->>'version' <> '2.0')) AS remaining_v1,
       count(*) FILTER (WHERE brand_spec IS NULL)                                          AS null_count
FROM sys_design_system_config
UNION ALL
SELECT 'sys_design_system_config_template',
       count(*) FILTER (WHERE brand_spec IS NOT NULL
                          AND brand_spec->>'version' = '2.0'),
       count(*) FILTER (WHERE brand_spec IS NOT NULL
                          AND (brand_spec->>'version' IS NULL OR brand_spec->>'version' <> '2.0')),
       count(*) FILTER (WHERE brand_spec IS NULL)
FROM sys_design_system_config_template;

COMMIT;
