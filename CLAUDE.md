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
4. **按产物类型分流**：
   - **PPT / 幻灯片 deck** → LLM 输出 slides JSON（2-5K tokens）→ `file_render` 逐页渲染
   - **Landing / 信息图 / 任意自由 HTML** → LLM 直接产 HTML → `file_write_chunk` 三阶段（start/append/end）落盘
5. `file_render` 路径：逐页渲染 + index.html 聚合页（3D 概览墙 + 键盘翻页）
6. 反 slop lint 自检，不通过则重渲染/重写（≤3 次）→ HITL

> ⚠️ **工具选择铁律**：白名单**只有** `[design_asset_fetch, design_direction_explore, file_render, file_write_chunk, reply_text]`。
> 严禁调用 `file_write`（不存在）——任何"保存 HTML 文件"需求走 `file_write_chunk`（自由 HTML）或 `file_render`（PPT 模板）。
> systemPrompt §3.1 已写入决策树，LLM 选错时通常是因为 SQL 未应用最新版本（清缓存：`CapabilityProfileRegistry.invalidate("design")`）。

#### 设计模式 lint 规则

| 规则 id | 检测 |
|---|---|
| `no_purple_gradient` | CSS 渐变中的紫/靛色（AI slop 标志） |
| `no_emoji_icon` | emoji 充当图标（li/i/span/button 紧邻） |
| `no_placeholder` | Lorem Ipsum / TODO / 单独 `...` |
| `no_svg_human` | SVG 内人脸 path（结构或 eyes/mouth 关键词） |
| `brand_color_drift` | 非 brand 主色的显著彩色偏移（仅当 `withBrandColor` 提供 brand 时启用，15% 容差） |

#### 修改设计配置

更新 `sql/postgresql/capability/sys_agent_profile_design_b.sql` 后：
1. 应用 SQL 到目标 DB
2. 调用 `CapabilityProfileRegistry.invalidate("design")` 清缓存（或重启应用）

### code-profile（Plan C 已落地）

代码模式完整实现，遵循 SOLID/KISS/DRY/YAGNI/TDD。关键位置：

| 资产 | 路径 |
|---|---|
| 实现 | `framework/agents/profile/impl/CodeProfile.java` |
| Schema + systemPrompt 模板 | `framework/agents/profile/CodeSchema.java` |
| TDD 状态机 | `framework/agents/profile/impl/TddPhaseManager.java` + `TddPhase.java` |
| 编译 lint | `framework/agents/profile/lint/CompilePassRule.java`（py_compile/javac/go build） |
| 测试 lint | `framework/agents/profile/lint/TestPassRule.java`（pytest/mvn/npm/go test） |
| 反幻觉 API lint | `framework/agents/profile/lint/NoPhantomApiRule.java`（未注册的 module.method 或 imported symbol 拦截） |
| 注释语言 lint | `framework/agents/profile/lint/CommentLanguageMatchRule.java` |
| API 白名单 | `framework/agents/profile/lint/SymbolRegistry.java`（session 级 + `__shared__` 内置） |
| package_lookup 工具 | `framework/agents/tool/impl/PackageLookupTool.java`（PyPI/NPM/Maven，成功后自动注册符号） |
| sandbox 执行器 | `framework/agents/tool/impl/LocalSandboxExecutor.java`（ProcessBuilder，30s 超时） |
| SandboxExecutor 接口 | `framework/agents/tool/SandboxExecutor.java`（CompilePassRule/TestPassRule 后端） |

#### TDD 状态机

```
READ → TEST → TEST_HITL（强制中断）→ IMPL → EXEC → LINT → DONE
                                ↑                ↓
                                └── 失败 ≤3 次 ──┘
                                  >3 次 → HITL
```

#### code-profile 工作流

1. IntentClassifier 识别为 code → 加载 CodeProfile
2. `file_read` 相关代码（Phase READ）
3. `file_write` 失败测试（Phase TEST）→ TEST_HITL 强制中断
4. 用户审批后 `file_write` 实现（Phase IMPL）
5. `sandbox_exec` 跑测试（Phase EXEC）
6. 4 条 lint（Phase LINT）→ 失败回 IMPL（≤3 次）→ 触发 HITL
7. 完成（Phase DONE）

#### ExecuteNode 接入

`ExecuteNode` 注入了 `TddPhaseManager` + `SymbolRegistry`：
- `apply(state)` 末尾调用 `applyTddPhase(state, result)` 把当前 phase 写入 `DeepAgentState.CURRENT_PHASE`
- `buildStrategyHint` 的 `case TDD` 追加当前 phase 描述到拆分策略
- public hook `onToolExecuted(state, toolName, toolResult)`：由 `ToolNode` 在工具执行后调用，按 `file_write` / `sandbox_exec` 推进 phase，并抽取源码 import 注册到 SymbolRegistry（**当前 ToolNode 未接入此 hook，待后续任务**）

#### 第三方 API 防幻觉流程

1. LLM 写代码前调 `package_lookup(language, pkg, symbol, sessionId)`
2. PyPI/NPM/Maven 返回真实版本号 + summary
3. 成功后 `PackageLookupTool` 自动 `registry.register(sessionId, symbol)` + 简短形式（如 `np.array` 同时注册 `array`）
4. 后续 `file_write` 源码经 `NoPhantomApiRule` 校验：未注册的 `module.method` 或 `imported symbol` 被拦截

#### 修改 code 配置

更新 `sql/postgresql/capability/sys_agent_profile_code_c.sql` 后：
1. 应用 SQL 到目标 DB
2. 调用 `CapabilityProfileRegistry.invalidate("code")` 清缓存（或重启应用）

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
| `SymbolRegistryTest` / `TddPhaseManagerTest` | API 白名单 + TDD 状态机（合法迁移/HITL 门控/retry 预算） |
| `CompilePassRuleTest` / `TestPassRuleTest` | sandbox 执行验证（命令模板 + Result 解析） |
| `CommentLanguageMatchRuleTest` | 注释语言一致性（CJK 混用拦截） |
| `NoPhantomApiRuleTest` + `PhantomApiLintComplianceTest` | 5 份样本（py/js）拦截合规回归 |
| `PackageLookupToolTest` | PyPI/NPM 解析 + 符号注册（MockWebServer 隔离） |
| `CodeProfileTest` | id/strategy/systemPrompt 渲染/4 lint 规则/outputFormat |
| `AgentProfileServiceCodeBranchTest` | AgentProfileService case "code" 路由 CodeProfile |
| `CodeProfileEndToEndTest` (`@Tag("integration")`) | 真实 DB + 快排 TDD 状态机端到端 |

跑 Plan A + B + C 全部单测（排除 integration）：
```bash
mvn test -Dtest='com.hypersense.boot.framework.agents.profile.*Test,FileRenderToolTest,SessionChunkBufferTest,DesignAssetFetchToolTest,DesignDirectionExploreToolTest,AgentProfileServiceDesignBranchTest,AgentProfileServiceCodeBranchTest,IntentClassifierNodeTest' -DexcludedGroups=integration -q
```
