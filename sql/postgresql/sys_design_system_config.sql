-- hs-agents 用户设计体系配置表 - PostgreSQL 16+
-- Copyright (c) 2026-present, HyperSense
--
-- 说明：用户/团队的设计体系配置（Design System Config），包含品牌规范、代码 Token 与素材库
--       官方模板表 sys_design_system_config_template 跨租户共享（已加入 tenant.ignore-tables）
--
-- brandSpec v2.0 结构（JSONB 字段，不改 DDL，仅扩展内部结构）：
--   {
--     "version": "2.0",
--     "colors": {
--       "identity": { "primary": "#...", "accent": "#...", "neutral": "#..." },
--       "semantic": { "success": "#...", "warning": "#...", "danger": "#...", "info": "#..." }
--     },
--     "fonts": {
--       "display": "...", "body": "...", "mono": "...",
--       "baseSize": 16, "scaleRatio": 1.25,
--       "weight": { "regular": 400, "medium": 500, "semibold": 600, "bold": 700 },
--       "lineHeight": { "tight": 1.25, "normal": 1.5, "relaxed": 1.625 },
--       "tracking": { "tight": "-0.025em", "normal": "0", "wide": "0.025em" }
--     },
--     "radius": { "sm": 4, "md": 6, "lg": 8, "xl": 12, "2xl": 16 },
--     "logo": ""
--   }
--   历史版本（v1）brandSpec 由 Java 端 BrandSpecMigrator 自动迁移到 v2.0（读取时兜底）。
--
-- 本文件包含两部分：
--   1. 全量建表（用于全新部署）
--   2. 增量改名脚本（用于已有 sys_design_system 表的环境做迁移）

-- ============================================================
-- 第一部分：全量建表（用于全新部署）
-- ============================================================

-- ----------------------------
-- Table structure for sys_design_system_config
-- ----------------------------
DROP TABLE IF EXISTS sys_design_system_config;
CREATE TABLE sys_design_system_config (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT DEFAULT 0,
    owner_user_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    category VARCHAR(20) NOT NULL DEFAULT 'personal',  -- personal / official
    type VARCHAR(20) NOT NULL DEFAULT 'web',            -- web / app
    brand_spec JSONB,                                   -- 品牌规范 JSON（颜色/字体/Logo）
    code_spec JSONB,                                    -- 代码规范 JSON（design-tokens）
    assets JSONB,                                       -- 素材库 JSON
    publish_status SMALLINT NOT NULL DEFAULT 0,         -- 0 草稿 / 1 已发布
    create_by BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_dsc_tenant ON sys_design_system_config (tenant_id);
CREATE INDEX idx_dsc_owner ON sys_design_system_config (owner_user_id);
CREATE INDEX idx_dsc_category ON sys_design_system_config (category);
CREATE INDEX idx_dsc_publish ON sys_design_system_config (publish_status);

COMMENT ON TABLE sys_design_system_config IS '用户设计体系配置表';
COMMENT ON COLUMN sys_design_system_config.id IS '主键';
COMMENT ON COLUMN sys_design_system_config.tenant_id IS '租户ID（多租户自动注入）';
COMMENT ON COLUMN sys_design_system_config.owner_user_id IS '所有者用户ID';
COMMENT ON COLUMN sys_design_system_config.name IS '设计体系名称';
COMMENT ON COLUMN sys_design_system_config.category IS '分类(personal-个人体系/official-官方预设)';
COMMENT ON COLUMN sys_design_system_config.type IS '类型(web-网页/app-应用)';
COMMENT ON COLUMN sys_design_system_config.brand_spec IS '品牌规范 JSON（颜色/字体/Logo）';
COMMENT ON COLUMN sys_design_system_config.code_spec IS '代码规范 JSON（design-tokens）';
COMMENT ON COLUMN sys_design_system_config.assets IS '素材库 JSON';
COMMENT ON COLUMN sys_design_system_config.publish_status IS '发布状态(0-草稿 1-已发布)';
COMMENT ON COLUMN sys_design_system_config.create_by IS '创建人ID';
COMMENT ON COLUMN sys_design_system_config.create_time IS '创建时间';
COMMENT ON COLUMN sys_design_system_config.update_by IS '更新人ID';
COMMENT ON COLUMN sys_design_system_config.update_time IS '更新时间';
COMMENT ON COLUMN sys_design_system_config.is_deleted IS '逻辑删除标识(1-已删除 0-未删除)';

-- ----------------------------
-- Table structure for sys_design_system_config_template
-- ----------------------------
DROP TABLE IF EXISTS sys_design_system_config_template;
CREATE TABLE sys_design_system_config_template (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT DEFAULT 0,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(20) NOT NULL DEFAULT 'web',
    category_label VARCHAR(64),                         -- 显示用分类标签（如 "AI & LLM"）
    brand_spec JSONB,
    code_spec JSONB,
    assets JSONB,
    thumbnail_url VARCHAR(1024),
    sort_order INT DEFAULT 0,
    is_active SMALLINT NOT NULL DEFAULT 1,              -- 1 启用 / 0 停用
    create_by BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_dsct_tenant ON sys_design_system_config_template (tenant_id);
CREATE INDEX idx_dsct_active ON sys_design_system_config_template (is_active);

COMMENT ON TABLE sys_design_system_config_template IS '官方设计体系配置模板表（跨租户共享）';
COMMENT ON COLUMN sys_design_system_config_template.id IS '主键';
COMMENT ON COLUMN sys_design_system_config_template.tenant_id IS '租户ID（模板默认 0）';
COMMENT ON COLUMN sys_design_system_config_template.name IS '模板名称';
COMMENT ON COLUMN sys_design_system_config_template.type IS '类型(web-网页/app-应用)';
COMMENT ON COLUMN sys_design_system_config_template.category_label IS '显示用分类标签（如 "AI & LLM"）';
COMMENT ON COLUMN sys_design_system_config_template.brand_spec IS '品牌规范 JSON';
COMMENT ON COLUMN sys_design_system_config_template.code_spec IS '代码规范 JSON';
COMMENT ON COLUMN sys_design_system_config_template.assets IS '素材库 JSON';
COMMENT ON COLUMN sys_design_system_config_template.thumbnail_url IS '缩略图 URL';
COMMENT ON COLUMN sys_design_system_config_template.sort_order IS '排序值（升序）';
COMMENT ON COLUMN sys_design_system_config_template.is_active IS '是否启用(1-是 0-否)';
COMMENT ON COLUMN sys_design_system_config_template.create_by IS '创建人ID';
COMMENT ON COLUMN sys_design_system_config_template.create_time IS '创建时间';
COMMENT ON COLUMN sys_design_system_config_template.update_by IS '更新人ID';
COMMENT ON COLUMN sys_design_system_config_template.update_time IS '更新时间';
COMMENT ON COLUMN sys_design_system_config_template.is_deleted IS '逻辑删除标识(1-已删除 0-未删除)';

-- ----------------------------
-- 种子数据已迁移到独立文件：sys_design_system_config_template_seed.sql
-- 共 150 个官方设计系统模板（来源 open-design/design-systems），brandSpec v2.0
-- 旧的手写 3 条种子（OpenAI / Vercel / Linear）已由批量种子取代
-- ----------------------------


-- ============================================================
-- 第二部分：增量改名脚本（用于已有 sys_design_system 表的环境做迁移）
-- 说明：全新部署无需执行本段；已在生产/测试环境跑过旧 SQL 的环境执行本段做迁移
-- ============================================================

-- 表改名
ALTER TABLE IF EXISTS sys_design_system RENAME TO sys_design_system_config;
ALTER TABLE IF EXISTS sys_design_system_template RENAME TO sys_design_system_config_template;

-- 索引改名（PostgreSQL 表改名后索引名保留旧名，建议同步重命名以保持一致）
ALTER INDEX IF EXISTS idx_ds_tenant RENAME TO idx_dsc_tenant;
ALTER INDEX IF EXISTS idx_ds_owner RENAME TO idx_dsc_owner;
ALTER INDEX IF EXISTS idx_ds_category RENAME TO idx_dsc_category;
ALTER INDEX IF EXISTS idx_ds_publish RENAME TO idx_dsc_publish;
ALTER INDEX IF EXISTS idx_dst_tenant RENAME TO idx_dsct_tenant;
ALTER INDEX IF EXISTS idx_dst_active RENAME TO idx_dsct_active;

-- 注意：若旧表中存在 uk_scene_tpl_slug 等唯一约束，请按实际约束名同步执行 RENAME CONSTRAINT，例如：
-- ALTER TABLE sys_design_system_config_template RENAME CONSTRAINT uk_scene_tpl_slug TO uk_dsct_scene_tpl_slug;
