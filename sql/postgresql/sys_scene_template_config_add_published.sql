-- 为场景模板配置表新增发布状态字段
-- Copyright (c) 2026-present, HyperSense

ALTER TABLE sys_scene_template_config
    ADD COLUMN IF NOT EXISTS is_published SMALLINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN sys_scene_template_config.is_published IS '是否发布(1-已发布 0-未发布)；发布后对所有用户可见';

-- 已存在的官方模板默认发布
UPDATE sys_scene_template_config SET is_published = 1 WHERE is_official = 1;

-- 添加发布状态索引（用于查询性能）
CREATE INDEX IF NOT EXISTS idx_scene_tpl_config_is_published ON sys_scene_template_config (is_published);
