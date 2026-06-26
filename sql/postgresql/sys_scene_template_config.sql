-- hs-agents 场景模板配置表 - PostgreSQL 16+
-- Copyright (c) 2026-present, HyperSense
--
-- 说明：场景模板（HTML 设计模板）配置元数据表，用于 Community 模板市场

-- ----------------------------
-- Table structure for sys_scene_template_config
-- ----------------------------
DROP TABLE IF EXISTS sys_scene_template_config;
CREATE TABLE sys_scene_template_config (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT DEFAULT 0,
    owner_user_id BIGINT NOT NULL,
    slug VARCHAR(128) NOT NULL,
    name VARCHAR(255) NOT NULL,
    tagline VARCHAR(512),
    category VARCHAR(64) NOT NULL DEFAULT 'official',
    ui_category VARCHAR(50),
    mood JSONB,
    palette JSONB,
    typography JSONB,
    slide_count INT DEFAULT 1,
    source_url VARCHAR(1024),
    html_url VARCHAR(1024),
    thumbnail_url VARCHAR(1024),
    is_official SMALLINT DEFAULT 1,
    is_published SMALLINT NOT NULL DEFAULT 0,
    sort INT DEFAULT 0,
    create_by BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_deleted SMALLINT DEFAULT 0,
    CONSTRAINT uk_scene_tpl_config_slug UNIQUE (tenant_id, slug, is_deleted)
);

CREATE INDEX idx_scene_tpl_config_tenant_id ON sys_scene_template_config (tenant_id);
CREATE INDEX idx_scene_tpl_config_category ON sys_scene_template_config (category);
CREATE INDEX idx_scene_tpl_config_ui_category ON sys_scene_template_config (ui_category);
CREATE INDEX idx_scene_tpl_config_is_official ON sys_scene_template_config (is_official);
CREATE INDEX idx_scene_tpl_config_is_published ON sys_scene_template_config (is_published);
CREATE INDEX idx_scene_tpl_config_owner_user_id ON sys_scene_template_config (owner_user_id);

COMMENT ON TABLE sys_scene_template_config IS '场景模板配置表';
COMMENT ON COLUMN sys_scene_template_config.id IS '主键';
COMMENT ON COLUMN sys_scene_template_config.tenant_id IS '租户ID（官方模板为 0）';
COMMENT ON COLUMN sys_scene_template_config.owner_user_id IS '所属用户ID（官方模板为 0）';
COMMENT ON COLUMN sys_scene_template_config.slug IS 'URL slug（目录名）';
COMMENT ON COLUMN sys_scene_template_config.name IS '模板名称';
COMMENT ON COLUMN sys_scene_template_config.tagline IS '一句话描述';
COMMENT ON COLUMN sys_scene_template_config.category IS '分类(official/community)';
COMMENT ON COLUMN sys_scene_template_config.ui_category IS 'UI显示分类（从 SKILL.md od.scenario 派生）';
COMMENT ON COLUMN sys_scene_template_config.mood IS '情绪风格 JSON';
COMMENT ON COLUMN sys_scene_template_config.palette IS '调色板 JSON';
COMMENT ON COLUMN sys_scene_template_config.typography IS '排版规范 JSON';
COMMENT ON COLUMN sys_scene_template_config.slide_count IS '幻灯片页数';
COMMENT ON COLUMN sys_scene_template_config.source_url IS '源文件路径或仓库地址';
COMMENT ON COLUMN sys_scene_template_config.html_url IS 'HTML 模板的 MinIO 访问 URL';
COMMENT ON COLUMN sys_scene_template_config.thumbnail_url IS '缩略图 URL';
COMMENT ON COLUMN sys_scene_template_config.is_official IS '是否官方(1-是 0-否)';
COMMENT ON COLUMN sys_scene_template_config.is_published IS '是否发布(1-已发布 0-未发布)；发布后对所有用户可见';
COMMENT ON COLUMN sys_scene_template_config.sort IS '排序值（升序）';
COMMENT ON COLUMN sys_scene_template_config.create_by IS '创建人ID';
COMMENT ON COLUMN sys_scene_template_config.create_time IS '创建时间';
COMMENT ON COLUMN sys_scene_template_config.update_by IS '更新人ID';
COMMENT ON COLUMN sys_scene_template_config.update_time IS '更新时间';
COMMENT ON COLUMN sys_scene_template_config.is_deleted IS '逻辑删除标识(1-已删除 0-未删除)';
