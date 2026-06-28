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

- **Plan B**：替换 StubDesignProfile 为真实 DesignProfile（反 slop lint + 资产协议 + file_render Velocity 模板 + design_asset_fetch / design_direction_explore / file_write_chunk 工具）
- **Plan C**：替换 StubCodeProfile 为真实 CodeProfile（TDD 状态机 + SymbolRegistry + package_lookup + 4 条 lint 规则）

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

跑 Plan A 全部测试：
```bash
mvn test -Dtest='com.hypersense.boot.framework.agents.profile.*Test,ProfileGraphIntegrationTest,IntentClassifierNodeTest' -q
```

排除集成测试（无 DB 环境）：
```bash
mvn test -Dtest='com.hypersense.boot.framework.agents.profile.*Test,IntentClassifierNodeTest' -DexcludedGroups=integration -q
```
