package com.hypersense.boot.framework.agents.profile;

/**
 * design-profile 的输出 JSON schema（spec §4.2）。
 * 作为 LLM 输出约束 + file_render 输入。
 */
public final class SlidesSchema {

    public static final String SCHEMA_JSON = """
            {
              "$schema": "http://json-schema.org/draft-07/schema#",
              "type": "object",
              "required": ["schemaVersion", "profile", "meta", "slides"],
              "properties": {
                "schemaVersion": {"type": "string", "const": "1.0"},
                "profile": {"type": "string", "const": "design"},
                "meta": {
                  "type": "object",
                  "required": ["title", "templateType", "format"],
                  "properties": {
                    "title": {"type": "string"},
                    "audience": {"type": "string"},
                    "distance": {"type": "string"},
                    "temperature": {"type": "string"},
                    "designSystem": {
                      "type": "object",
                      "properties": {
                        "primary": {"type": "string"},
                        "accent": {"type": "string"},
                        "font": {"type": "string"}
                      }
                    },
                    "templateType": {"type": "string"},
                    "format": {"type": "string"}
                  }
                },
                "assets": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "id": {"type": "string"},
                      "type": {"type": "string"},
                      "source": {"type": "string"},
                      "embed": {"type": "string"}
                    }
                  }
                },
                "slides": {
                  "type": "array",
                  "minItems": 3,
                  "items": {
                    "type": "object",
                    "required": ["id", "role", "layout"],
                    "properties": {
                      "id": {"type": "string"},
                      "role": {"type": "string"},
                      "layout": {"type": "string"},
                      "content": {"type": "object"}
                    }
                  }
                }
              }
            }
            """;

    /** DesignProfile 系统提示词模板（参考 huashu-design 哲学） */
    public static final String SYSTEM_PROMPT_TEMPLATE = """
            你是 design-profile 设计专家。任务：{{userInput}}（sessionId={{sessionId}}）

            ## 核心哲学（参考 huashu-design）

            ### 1. 反 slop（必读）
            以下元素是 AI 训练语料最泛滥的「通用公式」，缺乏品牌识别度，**禁止使用**：
            - 紫渐变 / 靛蓝渐变（紫渐变是 SaaS 落地页万能公式）
            - emoji 作图标（请用 SVG 图标库如 Heroicons/Lucide 或文字标签）
            - 圆角卡片 + 左彩色 border accent
            - SVG 手画人脸 / 人像（用真实人像图或抽象图形）
            - Inter/Roboto 单字族（用衬线 display + system body）

            ### 2. 资产 > 规范
            - 具名产品/品牌（Apple/FIFA 等）必取官方 logo：调 design_asset_fetch 抓取
            - 内容必需的真图（足球、奖杯、地图）必取：调 design_asset_fetch 从 Wikimedia/Unsplash
            - 缺资产时用诚实 placeholder：「图待补」，不要画 SVG 凑数

            ### 3. Junior Designer 模式
            - 第一步调 design_direction_explore 产 3 份 outline（轮盘/参照/设计师三套逻辑）
            - 等待用户 HITL 审批选定方向
            - 然后调 design_asset_fetch 取资产
            - 然后输出完整 slides JSON（严格遵守下面 schema）

            ### 4. 输出格式（严格 JSON，禁止任何解释文本）

            schemaVersion=1.0, profile=design, meta 含 title/templateType/format/designSystem，
            assets 数组（含 id/source/embed data URL），slides 数组（每页含 id/role/layout/content）。

            templateType 可选：ppt_weekly_update / ppt_keynote / ppt_report（由用户需求决定）。

            关键：**只输出 JSON**（2-5K tokens），HTML 渲染由 file_render 工具完成。绝不直接输出 HTML。

            ### 5. 禁止
            - 禁止 Lorem Ipsum / TODO / "..." 占位
            - 禁止编造数据
            - 禁止超过 8 页（每页 1 个核心信息）
            - 禁止自造品牌色（必须用 designSystem 中颜色或其衍生色）
            """;

    /** 模板类型与对应 Velocity 文件名映射 */
    public static final String TEMPLATE_WEEKLY = "ppt_weekly_update";
    public static final String TEMPLATE_KEYNOTE = "ppt_keynote";
    public static final String TEMPLATE_REPORT = "ppt_report";

    private SlidesSchema() {}
}
