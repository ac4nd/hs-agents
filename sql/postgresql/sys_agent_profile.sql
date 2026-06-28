-- hs-agents 能力档位配置表 - PostgreSQL 16+
-- Copyright (c) 2026-present, HyperSense
--
-- 说明：定义 design/code/think/docs/learning 五大能力档位的提示词、工具白名单、Plan 策略等
--       CapabilityProfileRegistry 启动时从此表加载并 Caffeine 缓存
--

CREATE TABLE IF NOT EXISTS sys_agent_profile (
    id BIGSERIAL PRIMARY KEY,
    profile_id VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(64) NOT NULL,
    description VARCHAR(512),
    system_prompt TEXT NOT NULL,
    allowed_tools JSONB NOT NULL DEFAULT '[]',
    plan_strategy VARCHAR(32) NOT NULL,
    output_format JSONB,
    lint_rules JSONB DEFAULT '[]',
    hitl_policy JSONB DEFAULT '{}',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_sys_agent_profile_enabled ON sys_agent_profile(enabled, sort_order);

COMMENT ON TABLE sys_agent_profile IS '能力档位配置表';
COMMENT ON COLUMN sys_agent_profile.profile_id IS '档位 ID: design/code/think/docs/learning';
COMMENT ON COLUMN sys_agent_profile.allowed_tools IS '允许调用的工具白名单 JSON 数组';
COMMENT ON COLUMN sys_agent_profile.plan_strategy IS 'Plan 策略: OUTLINE_DEMO/TDD/DIVERGE_THEN_STRUCTURE/OUTLINE_THEN_FILL/LAYERED_LEARNING/GENERIC';

-- 初始化 5 个默认 profile（GENERIC 策略兜底，Plan B/C 再细化提示词与工具）
-- 注意：system_prompt 末尾必须保留 {{userInput}} 占位符，AbstractCapabilityProfile.systemPrompt(ctx) 会替换。
INSERT INTO sys_agent_profile (profile_id, name, description, system_prompt, allowed_tools, plan_strategy, sort_order) VALUES
('design', '设计模式', 'PPT/Landing/信息图等视觉产物，参考 huashu-design 哲学',
 '你是设计专家。遵循反 slop 原则（无紫渐变/无 emoji icon/无 SVG 手画人脸）。资产优先：具名品牌必取官方 logo，内容必需真图必取。Junior Designer 模式：先 demo 后批量。'||chr(10)||chr(10)||'用户需求：{{userInput}}',
 '["design_asset_fetch","design_direction_explore","file_render","file_write","reply_text"]'::jsonb,
 'OUTLINE_DEMO', 10),
('code', '代码模式', '实现/重构/修复代码，遵循 SOLID/KISS/DRY/YAGNI 与 TDD',
 '你是资深工程师。读后写、evidence-based。先 file_read 相关代码，再写失败测试，HITL 后实现，最后跑测试。禁止改测试以通过。'||chr(10)||chr(10)||'用户需求：{{userInput}}',
 '["file_read","file_write","file_write_chunk","sandbox_exec","package_lookup","reply_text"]'::jsonb,
 'TDD', 20),
('think', '深度思考模式', '调研 + 计划合一：发散调研→收敛结论→结构化计划',
 '你是深度思考专家。先发散后收敛。每条结论必须有 source URL。计划任务必须 SMART 化，列依赖与风险。调研完成前不下结论。'||chr(10)||chr(10)||'用户需求：{{userInput}}',
 '["internet_search","web_reader","graphify","file_read","file_write","reply_text"]'::jsonb,
 'DIVERGE_THEN_STRUCTURE', 30),
('docs', '文档模式', '撰写技术文档/规范/教程',
 '你是技术文档专家。受众导向、信息架构清晰、示例可运行。'||chr(10)||chr(10)||'用户需求：{{userInput}}',
 '["file_read","file_write","reply_text"]'::jsonb,
 'OUTLINE_THEN_FILL', 40),
('learning', '学习模式', '苏格拉底式分层教学',
 '你是教学专家。先评估用户水平，制定学习路径，由浅入深配示例与测验。'||chr(10)||chr(10)||'用户需求：{{userInput}}',
 '["internet_search","file_read","file_write","reply_text"]'::jsonb,
 'LAYERED_LEARNING', 50)
ON CONFLICT (profile_id) DO NOTHING;
