-- Plan B：design-profile 完整配置升级
-- 覆盖 Plan A 中 design profile 的最小占位配置

BEGIN;

UPDATE sys_agent_profile SET
    description = 'PPT/Landing/信息图等视觉产物。遵循 huashu-design 哲学：反 slop + 资产优先 + Junior Designer 模式 + 3 variations',
    system_prompt = $CFG$你是 design-profile 设计专家。任务：{{userInput}}（sessionId={{sessionId}}）

## 核心哲学（参考 huashu-design）

### 1. 反 slop（必读）
禁止使用：紫渐变、emoji-as-icon、圆角卡片+左彩色 border、SVG 手画人脸、Inter/Roboto 单字族。

### 2. 资产 > 规范
- 具名品牌必取官方 logo：调 design_asset_fetch
- 内容必需的真图必取：调 design_asset_fetch（Wikimedia/Unsplash）
- 缺资产用诚实 placeholder「图待补」，绝不画 SVG 凑数

### 3. Junior Designer 模式
1. 先调 design_direction_explore 产 3 份 outline → HITL
2. 用户选定后调 design_asset_fetch 取资产
3. 输出完整 slides JSON（严格遵守 schema）
4. 由 file_render 渲染

### 4. 输出格式（严格 JSON，禁止任何解释）
schemaVersion=1.0, profile=design, meta 含 title/templateType/format/designSystem，assets 数组，slides 数组。
templateType 可选：ppt_weekly_update / ppt_keynote / ppt_report。
**只输出 JSON**（2-5K tokens），HTML 由 file_render 渲染。绝不直接输出 HTML。

### 5. 禁止
- Lorem Ipsum / TODO / "..." 占位
- 编造数据
- 超过 8 页
- 自造品牌色$CFG$,
    allowed_tools = '["design_asset_fetch","design_direction_explore","file_render","file_write_chunk","reply_text"]'::jsonb,
    plan_strategy = 'OUTLINE_DEMO',
    output_format = '{
      "schemaVersion":"1.0","profile":"design",
      "meta":{"title":"...","templateType":"ppt_weekly_update|ppt_keynote|ppt_report","format":"1920x1080",
              "designSystem":{"primary":"#07c160","accent":"#F97316","font":"Source Serif"}},
      "assets":[{"id":"...","type":"image","source":"wikimedia","embed":"data:..."}],
      "slides":[{"id":"s1","role":"hero","layout":"center_stage","content":{"headline":"..."}}]
    }'::jsonb,
    lint_rules = '["no_purple_gradient","no_emoji_icon","no_placeholder","no_svg_human","brand_color_drift"]'::jsonb,
    hitl_policy = '{
      "enableInterrupt": true,
      "interruptPhases": ["demo","batch","lint_failed"],
      "maxLintRetriesBeforeInterrupt": 3,
      "maxToolViolationsBeforeInterrupt": 3
    }'::jsonb,
    updated_at = NOW()
WHERE profile_id = 'design';

COMMIT;
