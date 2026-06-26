-- 场景模板配置表新增 ui_category 字段（UI 显示分类）
-- 说明：原 category 用于业务区分（official/community），不适合做前端分类筛选；
--       新字段 ui_category 从 SKILL.md frontmatter 的 od.scenario 派生（marketing / engineering / ...），
--       本次回填按 slug 前缀做一次性映射（数据库中已无 frontmatter 信息）。

-- 1. 新增字段
ALTER TABLE sys_scene_template_config
    ADD COLUMN IF NOT EXISTS ui_category VARCHAR(50);

COMMENT ON COLUMN sys_scene_template_config.ui_category IS 'UI显示分类（从 SKILL.md od.scenario 派生）';

-- 2. 建索引（便于前端筛选）
CREATE INDEX IF NOT EXISTS idx_scene_tpl_config_ui_category
    ON sys_scene_template_config (ui_category);

-- 3. 数据迁移：按 slug 前缀派生（仅回填 NULL 行）
UPDATE sys_scene_template_config SET ui_category = CASE
    WHEN slug LIKE 'html-ppt-%' OR slug LIKE 'guizang-ppt%' THEN 'presentation'
    WHEN slug LIKE '%dashboard%' OR slug LIKE 'flowai-live%' THEN 'dashboard'
    WHEN slug LIKE '%blog%' OR slug LIKE '%email%' OR slug LIKE '%newsletter%' THEN 'content'
    WHEN slug LIKE '%landing%' OR slug LIKE '%pricing%' OR slug LIKE '%marketing%' OR slug LIKE '%sales%' THEN 'marketing'
    WHEN slug LIKE '%invoice%' OR slug LIKE '%finance%' OR slug LIKE '%dcf%' OR slug LIKE '%valuation%' THEN 'finance'
    WHEN slug LIKE '%hr%' OR slug LIKE '%onboarding%' THEN 'hr'
    WHEN slug LIKE '%clinical%' OR slug LIKE '%health%' THEN 'healthcare'
    WHEN slug LIKE '%course%' OR slug LIKE '%edu%' THEN 'education'
    WHEN slug LIKE '%runbook%' OR slug LIKE '%docs%' OR slug LIKE '%eng-%' THEN 'engineering'
    WHEN slug LIKE '%gamified%' OR slug LIKE '%dating%' THEN 'personal'
    WHEN slug LIKE '%contact%' OR slug LIKE '%widget%' OR slug LIKE '%critique%' THEN 'design'
    ELSE 'other'
END WHERE ui_category IS NULL;

-- 4. 验证（手动执行）
-- SELECT ui_category, count(*) FROM sys_scene_template_config GROUP BY ui_category ORDER BY 2 DESC;
