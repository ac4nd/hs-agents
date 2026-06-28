# hs-agents 模块指南

本文件为 `hs-agents/` 子模块提供 Claude Code 协作指南。父项目总指南见上级目录 `CLAUDE.md`。

## Capability Profile 架构（Plan A 已落地）

智能体在 PlanNode 前通过 IntentClassifierNode 自动识别用户意图，路由到 5 个能力档位之一：
`design` / `code` / `think` / `docs` / `learning`。每个档位的提示词、工具白名单、Plan 策略
由 `sys_agent_profile` 表配置，`CapabilityProfileRegistry` 用 Caffeine 缓存（30 分钟 TTL）。

### 关键位置

| 资产 | 路径 |
|---|---|
| DB 建表 + 种子 | `sql/postgresql/sys_agent_profile.sql` |
| 接口契约 | `src/main/java/com/hypersense/boot/framework/agents/profile/CapabilityProfile.java` |
| 抽象基类（含模板渲染） | `src/main/java/com/hypersense/boot/framework/agents/profile/AbstractCapabilityProfile.java` |
| 注册表（Caffeine 缓存） | `src/main/java/com/hypersense/boot/framework/agents/profile/CapabilityProfileRegistry.java` |
| 意图分类节点 | `src/main/java/com/hypersense/boot/framework/agents/engine/node/IntentClassifierNode.java` |
| Profile 业务服务 | `src/main/java/com/hypersense/boot/framework/agents/service/AgentProfileService.java` |
| 状态字段 | `src/main/java/com/hypersense/boot/framework/agents/model/DeepAgentState.java`（ACTIVE_PROFILE / INTENT_CONFIDENCE / SECONDARY_PROFILES / PROFILE_HINTS / CURRENT_PHASE / PROFILE_HANDOFF_FROM / PROFILE_HANDOFF_CONTEXT 共 7 个） |
| HANDOFF 事件类型 | `AgentEventType.PROFILE_HANDOFF` |

### Plan A 落地范围

- 框架骨架 + 2 个 stub profile（StubDesignProfile / StubCodeProfile）+ GenericProfile 兜底
- 5 个默认 profile 种子（design/code/think/docs/learning）
- IntentClassifierNode 单次 LLM 调用，含 retry + legacy research→think 映射 + fallback 到 code
- PlanNode / ExecuteNode / ToolNode 接入 profile（构造函数注入 + 失败兜底）
- AgentServiceImpl.streamExecute 在 buildInitialState 前调用 classifier
- 静态串联：完成时若有 secondary profiles，发送 PROFILE_HANDOFF 事件（前端发起新请求）

### Plan B/C 预告

- **Plan B**（已落地，见下方专节）：替换 StubDesignProfile 为真实 DesignProfile（反 slop lint + 资产协议 + file_render Velocity 模板 + design_asset_fetch / design_direction_explore / file_write_chunk 工具）
- **Plan C**：替换 StubCodeProfile 为真实 CodeProfile（TDD 状态机 + SymbolRegistry + package_lookup + 4 条 lint 规则）

### design-profile（Plan B 已落地）

设计模式完整实现，参考 huashu-design 哲学。关键位置：

| 资产 | 路径 |
|---|---|
| 实现 | `framework/agents/profile/impl/DesignProfile.java` |
| 反 slop lint（5 条规则） | `framework/agents/profile/lint/`（NoPurpleGradient / NoEmojiIcon / NoPlaceholder / NoSvgHuman / BrandColorDrift） |
| Slides schema | `framework/agents/profile/SlidesSchema.java` |
| 资产抓取工具 | `tool/impl/DesignAssetFetchTool.java`（svgl → simpleicons → Wikimedia 三级兜底） |
| 方向探索工具 | `tool/impl/DesignDirectionExploreTool.java`（roulette / reference / designer 三套互补逻辑 + 兜底） |
| 渲染工具 | `tool/impl/FileRenderTool.java`（slides JSON → 逐页 `slide_<n>.html` + `index.html` 聚合页） |
| 分块写盘工具 | `tool/impl/FileWriteChunkTool.java` + `SessionChunkBuffer.java`（start/append/end 三态） |
| Velocity 引擎封装 | `framework/agents/render/SlideTemplateEngine.java` |
| PPT 模板 | `resources/templates/slides/`（`_slide_base.vm` + `ppt_weekly_update.vm` + `ppt_keynote.vm` + `ppt_report.vm` + `deck_index.vm` 3D 概览墙） |

#### 设计模式工作流

1. IntentClassifier 识别为 design → 加载 DesignProfile
2. `design_direction_explore` 产 3 份 outline → HITL 审批
3. `design_asset_fetch` 取 logo + 真图
4. LLM 输出 slides JSON（2-5K tokens，受 `maxOutputTokens` 限制）
5. `file_render` 逐页渲染 + index.html 聚合页（3D 概览墙 + 键盘翻页）
6. 反 slop lint 自检，不通过则重渲染（≤3 次）→ HITL

#### 设计模式 lint 规则

| 规则 id | 检测 |
|---|---|
| `no_purple_gradient` | CSS 渐变中的紫/靛色（AI slop 标志） |
| `no_emoji_icon` | emoji 充当图标（li/i/span/button 紧邻） |
| `no_placeholder` | Lorem Ipsum / TODO / 单独 `...` |
| `no_svg_human` | SVG 内人脸 path（结构或 eyes/mouth 关键词） |
| `brand_color_drift` | 非 brand 主色的显著彩色偏移（仅当 `withBrandColor` 提供 brand 时启用，15% 容差） |

#### 修改设计配置

更新 `sql/postgresql/sys_agent_profile_design_b.sql` 后：
1. 应用 SQL 到目标 DB
2. 调用 `CapabilityProfileRegistry.invalidate("design")` 清缓存（或重启应用）

### 修改 profile 配置

DB 表 `sys_agent_profile` 修改后需调用 `CapabilityProfileRegistry.invalidate(profileId)` 清缓存（或重启应用）。

### 跨租户配置

`sys_agent_profile` 是全局跨租户配置表：
- 已加入 `application-dev.yml` 的 `tenant.ignore-tables`
- `AgentProfileService.loadProfile` 标注了 `@IgnoreTenant`

**注意**：`application-dev.yml` 设了 `skip-worktree` 标志（保存本地 DB 凭证）。如检出
新环境，需手动确保 `tenant.ignore-tables` 包含 `sys_agent_profile`。

### 测试

| 测试 | 范围 |
|---|---|
| `PlanStrategyTest` | 枚举值 + fromString |
| `CapabilityProfileRegistryTest` | Caffeine 缓存委托 + invalidate |
| `StubDesignProfileTest` / `StubCodeProfileTest` | id/name/systemPrompt 渲染/allowedTools/planStrategy/lintRules |
| `IntentClassifierNodeTest` | 解析 / LLM 异常 / JSON 异常 / retry / legacy 映射 |
| `ProfileGraphIntegrationTest` (`@Tag("integration")`) | 从真实 DB 加载 + 缓存一致 + systemPrompt 渲染 |
| `DesignProfileTest` / `AgentProfileServiceDesignBranchTest` | DesignProfile 字段 + AgentProfileService case "design" 路由 |
| `AntiSlopLintComplianceTest` | 10 份 slop 样本 HTML 拦截 / 第 10 份 clean 放过 |
| `FileRenderToolTest` / `SessionChunkBufferTest` / `DesignAssetFetchToolTest` / `DesignDirectionExploreToolTest` | 4 个新工具单测 |
| `DesignProfileEndToEndTest` (`@Tag("integration")`) | 真实 DB + 世界杯 PPT 端到端 |

跑 Plan A + B 全部单测（排除 integration）：
```bash
mvn test -Dtest='com.hypersense.boot.framework.agents.profile.*Test,FileRenderToolTest,SessionChunkBufferTest,DesignAssetFetchToolTest,DesignDirectionExploreToolTest,AgentProfileServiceDesignBranchTest,IntentClassifierNodeTest' -DexcludedGroups=integration -q
```
