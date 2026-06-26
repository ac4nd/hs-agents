-- ============================================================
-- 验证脚本：检查 brand_spec 是否全部迁移到 v2.0
-- 适用：执行 migrate_brand_spec_v1_to_v2.sql 之后
--
-- 期望：remaining_v1_main / remaining_v1_template 均为 0
--       null_count 列仅为信息（NULL 行不在迁移范围内，由 Java 端兜底）
-- ============================================================

-- 1. 残留 v1（或 NULL）行数
SELECT
    (SELECT count(*) FROM sys_design_system_config
     WHERE brand_spec IS NOT NULL
       AND (brand_spec->>'version' IS NULL OR brand_spec->>'version' <> '2.0')
    ) AS remaining_v1_main,
    (SELECT count(*) FROM sys_design_system_config_template
     WHERE brand_spec IS NOT NULL
       AND (brand_spec->>'version' IS NULL OR brand_spec->>'version' <> '2.0')
    ) AS remaining_v1_template,
    (SELECT count(*) FROM sys_design_system_config
     WHERE brand_spec IS NULL
    ) AS null_main,
    (SELECT count(*) FROM sys_design_system_config_template
     WHERE brand_spec IS NULL
    ) AS null_template;

-- 2. v2.0 行数总览
SELECT 'sys_design_system_config' AS table_name,
       count(*) AS total_rows,
       count(*) FILTER (WHERE brand_spec->>'version' = '2.0') AS v2_count
FROM sys_design_system_config
UNION ALL
SELECT 'sys_design_system_config_template',
       count(*),
       count(*) FILTER (WHERE brand_spec->>'version' = '2.0')
FROM sys_design_system_config_template;

-- 3. 抽样检查结构完整性（identity / fonts / radius 关键字段非空）
SELECT 'sys_design_system_config' AS table_name,
       count(*) AS broken_rows
FROM sys_design_system_config
WHERE brand_spec IS NOT NULL
  AND brand_spec->>'version' = '2.0'
  AND (   brand_spec#>>'{colors,identity,primary}' IS NULL
       OR brand_spec#>>'{colors,identity,accent}'  IS NULL
       OR brand_spec#>>'{colors,identity,neutral}' IS NULL
       OR brand_spec#>>'{fonts,body}'              IS NULL
       OR brand_spec#>>'{fonts,display}'           IS NULL
       OR brand_spec#>>'{radius,md}'               IS NULL)
UNION ALL
SELECT 'sys_design_system_config_template',
       count(*)
FROM sys_design_system_config_template
WHERE brand_spec IS NOT NULL
  AND brand_spec->>'version' = '2.0'
  AND (   brand_spec#>>'{colors,identity,primary}' IS NULL
       OR brand_spec#>>'{colors,identity,accent}'  IS NULL
       OR brand_spec#>>'{colors,identity,neutral}' IS NULL
       OR brand_spec#>>'{fonts,body}'              IS NULL
       OR brand_spec#>>'{fonts,display}'           IS NULL
       OR brand_spec#>>'{radius,md}'               IS NULL);
