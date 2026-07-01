# Plan B / C 遗留问题清单

> 生成时间：2026-06-29
> 范围：Plan B（design-profile）+ Plan C（code-profile）落地后的功能性 / 测试 / 设计遗留

---

## P0 — 阻塞生产可用

### #1 ToolNode 未调用 ExecuteNode.onToolExecuted hook（Plan C 任务 11）✅ 已修复 2026-06-29

- **现状**：已在 `ToolNode` 注入 nullable `TddPhaseManager` + `SymbolRegistry`，
  并在两条执行路径（LLM 决策 / 旧遍历）`handleFileWriteSideEffect` 之后追加 `advanceTddPhase`。
  `NodeFactory.toolNode()` 已转发至 7 参 `create()` 重载
- **实现差异**：未直接调用 `ExecuteNode.onToolExecuted`（避免循环依赖），
  改为在 ToolNode 内镜像同样的逻辑（file_write 推 READ→TEST→TEST_HITL→IMPL→EXEC + 抽 import；
  sandbox_exec 推 EXEC→LINT）。重复约 30 行代码，已标注对应 ExecuteNode 私有方法
- **验证**：`mvn test -Dtest='TddPhaseManagerTest,SymbolRegistryTest,CodeProfileTest,AgentProfileServiceCodeBranchTest'` 全绿

### #2 Lint 规则从未在主链路被调用（**比 #1 更严重，新发现**）✅ 已修复 2026-06-29（首阶段）

- **现状**：在 ToolNode 注入 nullable `LintStatsManager`，
  file_write/file_render/sandbox_exec 后调 `runProfileLint(state, toolName, result, todo)`，
  扫描 active profile 的 `lintRules()`，命中即累加 session 级计数器并发 `LINT_VIOLATION` 事件。
  超 `HitlPolicy.maxLintRetriesBeforeInterrupt` 时写 `NEED_CONFIRMATION`/`INTERRUPT_REASON` 到返回 Map，
  并发 `INTERRUPT` 事件，code-profile 还会调 `tddPhaseManager.failLint(sessionId)`
- **新增组件**：
  - `LintStatsManager`（@Component 单例，session 级 ConcurrentHashMap 计数器，per-rule + total + snapshot + reset）
  - `AgentEventType.LINT_VIOLATION`（新事件类型，data: ruleId/description/message/snippet/attempt/willInterrupt）
  - `LintStatsManagerTest`（5 用例：会话隔离/累加/快照/reset/null 兜底）
  - `ToolNodeLintIntegrationTest`（2 用例：违规累计触发 INTERRUPT；profile 缺失时不触发 lint）
- **NodeFactory** 转发至 8 参 `create()` 重载
- **已覆盖范围**：违规检测 + 事件输出 + 累计 + HITL 触发
- **仍待补（次阶段）**：违规反馈注入下一轮 LLM prompt（让 LLM 看到违规描述并重新生成）；
  DesignProfile 的 retry file_render 联动（需 graph 层改造）；`reset(sessionId)` 在 HITL 审批通过时调用

### #3 CodeProfile 默认参数硬编码（Plan C 任务 8）✅ 已修复 2026-06-29

- **现状**：`AgentProfileService.loadProfile(profileId, sessionId, hints)` 3 参新签名，
  case "code" 通过 `hintStr(hints, "language"|"sourceFile"|"testFile", default)` 从 hints 读取，
  缺失回退 `python` / `src/main.py` / `test/test_main.py`
- **调用链**：`IntentClassifierNode` 写入 state.PROFILE_HINTS →
  `PlanNode/ExecuteNode/ToolNode` 从 state 读 hints + sessionId →
  `CapabilityProfileRegistry.get(profileId, sessionId, hints)` →
  `AgentProfileService.loadProfile(profileId, sessionId, hints)`

### #4 CapabilityProfileRegistry 缓存键不含 sessionId（**结构性问题**）✅ 已修复 2026-06-29

- **现状**：缓存键 = `profileId [+ "::" + sessionId] [+ "#" + hints.hashCode()]`，
  CodeProfile 在不同 session / 不同 hints 下产生独立 cache entry，互不污染
- **invalidate(profileId)** 改为前缀匹配清除所有 session 变体
  （原 `cache.invalidate(profileId)` 只清精确 key，遗留 `profileId::xxx` 全部失效不掉）
- **新增测试**：`shouldCachePerSessionId` / `shouldEvictAllSessionVariantsOnInvalidate`
  + `AgentProfileServiceCodeBranchTest.shouldAcceptHintsForCodeProfile`
- **缓存容量**：64 → 256（per-session 缓存后条目数会随 session 线性增长）

---

## P1 — 待补能力

### #5 TddPhaseManager 第 3 次失败语义（Plan C 任务 6 自审 concern）✅ 已修复 2026-06-29

- **现状**：`failLint` 条件从 `n <= MAX_LINT_RETRIES` 改为 `n < MAX_LINT_RETRIES`，
  第 3 次失败时不再把 phase 切回 IMPL，保持 LINT 等待 HITL
- **新增测试**：`shouldHaltAtLintOnThirdFailure` 验证第 3 次失败后 current()==LINT
  且 shouldInterruptForHITL()==true

### #6 DB seed 未应用

- **文件**：
  - `sql/postgresql/capability/sys_agent_profile_design_b.sql`（Plan B）
  - `sql/postgresql/capability/sys_agent_profile_code_c.sql`（Plan C）
- **状态**：用户已认领手动执行
- **应用后影响**：
  - `DesignProfileEndToEndTest.shouldRenderWorldCupPptAndPassLint` 转绿
  - `CodeProfileEndToEndTest.shouldLoadCodeProfileWithFullConfig` 转绿
  - `ProfileGraphIntegrationTest` 需重新跑回归

---

## P2 — 设计观察（非阻塞）✅ 已处理 2026-06-29

### #7 LocalSandboxExecutor shlex 简化 ✅ 已修复

- **现状**：`LocalSandboxExecutor.shlex()` 由 `split("\\s+")` 升级为完整 POSIX 风格解析器
  （支持单引号 / 双引号嵌套、引号外 `\` 转义、双引号内 `\"`/`\\` 转义、未闭合引号宽容处理）
- **新增**：`LocalSandboxExecutorTest`（8 用例：plain / 双引号 / 单引号 / 双引号内转义 /
  引号外转义 / 多空白合并 / 空串 / 未闭合）
- **调用方影响**：CompilePassRule / TestPassRule 原命令模板无空格参数，行为不变；
  未来 LLM 自定义命令带 `"arg with spaces"` 可正常解析

### #8 brand_color_drift 仅在有 brand 色时启用（Plan B 任务 6）✅ 设计决策已记录

- **现状**：行为保持不变（`brandColor == null` 时 `check()` 直接返回 null，静默放行）
- **设计意图**（已固化为 `BrandColorDriftRule` 类 javadoc）：
  - 「无品牌色」≠「禁止彩色」—— 强行限制会误伤 rainbow dashboard / illustration-heavy deck
  - 主色偏移检测只在「品牌主色存在」前提下成立；无品牌色时 slop 拦截由
    `NoPurpleGradientRule` / `NoEmojiIconRule` 等其他规则覆盖
  - 如未来需要无品牌色兜底，应新增独立规则（如 `no_extreme_saturation`），单一职责

### #9 FileRenderTool 输出结构偏离原 spec ✅ 已记录

- **现状**：`CLAUDE.md` 中 FileRenderTool 描述已反映实际行为（"逐页 `slide_<n>.html` +
  `index.html` 聚合页"），不再保留"`deck.html`"的旧 spec
- **行为合理性**：按页 HTML 便于单页预览 / 截图，`index.html` 聚合 3D 概览墙 + 翻页，
  比 `deck.html` 单体更灵活
- **遗留**：若存在更早期 plan-b 文档残留 `deck.html`，统一以本 README + CLAUDE.md 为准

---

## P3 — 预先存在（非 Plan B/C 引入）✅ 已修复 2026-06-29

### #10 `chatModelRegistry is null` — 13 失败 + 45 错误 ✅ 已修复

- **根因**：`AgentServiceImpl.createSession/getGraphOrThrow` 调用 `chatModelRegistry.getOrDefault`，
  但 `AgentServiceImplTest` / `HitlTest` / `SkillIntegrationTest` 的 `setUp()` 把构造函数
  第 8 位 `chatModelRegistry` 传 `null`
- **修复**：三个测试均注入 `mock(ChatModelRegistry.class)` + `when(getOrDefault(any())).thenReturn(null)`
  （让代码回退兜底单例），并把 `deepAgentGraph.build(...)` mock 升级到三参签名
  `build(ChatModel, StreamingChatModel, HitlBuildConfig)`（当前实现入口）
- **过时断言同步**：
  - `testCreateSession_graphBuilt` / HitlTest `verify build(...)` 改为三参签名
  - `testExecute_success` / `testExecute_graphExecutionFails` 删除 `verify(sandboxManager).destroy`
    （沙箱生命周期跟随会话，单轮完成/失败不销毁，仅 deleteSession 时清理）
  - `testExecute_graphNotFound` 文案断言扩展为 `图重建失败|图实例不存在`
  - `testGetSession_invalidType` 文案 `数据异常` → `类型异常`（实际文案 `数据类型异常` 不含连续子串 `数据异常`）

### #11 SubAgentEventStreamingTest TODO 校验失败 ✅ 已修复

- **根因**：测试 mock LLM 响应 `"TODO: 回答用户问题"` 不引用任何工具，
  撞上 Plan A「废除 direct 策略」校验（`PlanNode.validateWithRetry` 强制 TODO 引用
  `file_write / file_read / internet_search / sandbox / reply_text / delegate` 之一）
- **修复**：mock 响应改为 `"TODO: 使用 reply_text 工具回答用户问题"`，通过校验
- **验证**：23/23 全绿（含 `testStreamEventSequence` 的 `FINAL_RESPONSE` 序列断言）

### 回归验证

`mvn test -Dtest='*' -DexcludedGroups=integration` 全套 157 测试 0 失败 0 错误

---

## 修复优先级建议

| 顺序 | 项 | 工作量 | 备注 |
|---|---|---|---|
| 1 | #1 ToolNode hook 接入 | 2 小时 | 隔离改动，立即可做 |
| 2 | #2 LintNode 新增 | 8-12 小时 | 最大缺口，TDD/design 真正生效的前提 |
| 3 | #4 缓存键 + #3 PROFILE_HINTS | 4 小时 | 跟 #2 一并设计，避免返工 |
| 4 | #5 TddPhase 第 3 次失败 | 30 分钟 | 等 #2 接入后才有意义 |
| 5 | #6 DB seed 应用 | 用户手动 | 5 分钟 |

**关键路径**：#1 → #2 → #3+#4。不补 #2，整个 lint 体系是空架子。

---

## 现网问题修复（2026-06-30 新增）

### #12 DesignProfile LLM 调用不存在的 `file_write` 工具 ✅ 已修复 2026-06-30（两阶段）

- **现象**：用户问「设计世界杯赛程展示页」时，LLM 调用了 `file_write` 工具，
  但该工具不在 design profile 的 `allowedTools` 白名单（白名单只含
  `design_asset_fetch / design_direction_explore / file_render / file_write_chunk / reply_text`），
  导致 `[no_tool_matched]` 错误，HTML 文件未落盘
- **根因（两层）**：
  1. **systemPrompt 缺工具说明**：`sys_agent_profile_design_b.sql` 完全没提 `file_write_chunk`
     的存在和用法，PlanNode 也没生成正确的 TODO
  2. **ToolNode.shouldInvoke 缺 case**：switch 只覆盖 `reply_text/internet_search/read_file/
     sandbox/file_write` 五个工具，design-profile 的四个工具全部落入 default → 返回 false，
     LLM 路径**永远没有候选工具可选**，LLM 只能凭直觉自由决策调 `file_write`。
     这是真正阻塞的根因——即便 systemPrompt 完美，候选列表为空 LLM 还是会乱调
- **修复**：
  - **阶段 1（systemPrompt）**：新增 §3.1 工具选择决策树（PPT→file_render，自由 HTML→file_write_chunk）；
    明确三阶段调用流程；显式禁止 `file_write` 变体名
  - **阶段 2（代码）**：`ToolNode.shouldInvoke` 补齐 4 个 case：
    - `file_write_chunk`：匹配 `file_write_chunk / 分块 / chunk / 写入 / 保存 / 生成 / .html` 等
    - `file_render`：匹配 `file_render / 渲染 / slides / ppt / 幻灯片 / 演示文稿`
    - `design_asset_fetch`：匹配 `取 logo / 抓取 / 官方 logo / wikimedia / unsplash / simpleicons`
    - `design_direction_explore`：匹配 `outline / 探索方向 / 风格方向 / 3 份 / 三份 / variation`
- **新增测试**：`ToolNodeShouldInvokeTest`（7 用例，覆盖 4 个新工具的关键字/语义/扩展名匹配 + unknown 工具兜底）
- **应用方法**：
  1. 重新执行 `sql/postgresql/capability/sys_agent_profile_design_b.sql` 到 DB（阶段 1）
  2. **重启应用**让 ToolNode 代码改动生效（阶段 2，无需重跑 SQL 也能工作）
  3. 调用 `CapabilityProfileRegistry.invalidate("design")` 清缓存或重启应用

### #12b file_write_chunk 单次调用导致文件未落盘 ✅ 已修复 2026-06-30（阶段 3）

- **现象**：阶段 2 修完后 LLM 正确选 `file_write_chunk`，但调用一次后 HTML 仍未出现在工作目录
- **根因**：原设计强制三阶段 `start → append → end`，但 LLM 经常一次性把完整 HTML 放在
  `append` 里就停了，没有调 `end`，缓冲被遗弃在内存里 → 文件从未落盘。
  再加 `SessionChunkBuffer.append/end` 在未 start 时抛 `IllegalStateException`，
  LLM 看到错误更混乱
- **修复**（三处协同）：
  1. **新增 `mode=write` 单次写入模式**（默认）：LLM 一次性传完整 `chunk` 即直接落盘，
     无需 start/append/end；大文件场景才走三阶段分块
  2. **`SessionChunkBuffer.append` 容错自动 start**：LLM 漏调 start 时不丢内容
  3. **`SessionChunkBuffer.end` 容错返回 null**：未 start 直接 end 不再抛异常，
     由调用方返回友好错误
- **修改文件**：
  - `FileWriteChunkTool.java`：description / specification / switch 新增 `write` case，
    默认 mode 从 `append` 改为 `write`
  - `SessionChunkBuffer.java`：`append` 自动 start、`end` 返回 null（不再抛 IllegalStateException）
- **新增测试**：`FileWriteChunkToolTest`（5 用例：write 空_chunk 拒绝 / 默认走 write /
  unknown mode / filename 缺失 / end 未 start 降级）+ `SessionChunkBufferTest` 同步更新

### #12c file_write_chunk 漏传 filename 导致整轮产物丢失 ✅ 已修复 2026-07-01（阶段 4）

- **现象**：LLM 流式输出大 HTML 时偶尔漏传 `filename` 参数（即便 schema 标记 required），
  工具硬拒绝 → 文件未落盘 → reply_text 误报"已生成完毕" → 用户看到无产物的失败链路
- **根因**：required 校验在 LLM function-call 路径上是弱约束（不同 LLM 厂商遵守程度不一），
  而工具自身没有兜底，丢失整轮成本太高（HTML 已生成在 chunk 里，只因没 filename 就丢弃）
- **修复**：filename 缺失时按优先级推导
  1. 从 chunk 的 `<title>...</title>` 抽取 → slugify（小写 + 非字母数字转 `-` + 保留 CJK）→ `.html`
  2. 失败回退默认 `output.html`
  3. 永不硬拒绝，配合 WARN 日志便于排查
- **新增辅助方法**：`deriveFilenameFromContent(String chunk)` 在 `FileWriteChunkTool`
- **新增测试**：`shouldDeriveFilenameFromTitleWhenMissing` / `shouldFallbackToOutputHtmlWhenNoTitleAndNoFilename`
  覆盖 title 提取 + 默认兜底

### #12d LLM 只调 mode=start 就停，缓冲被遗弃 ✅ 已修复 2026-07-01（阶段 5，根因）

- **现象**：日志显示 `file_write_chunk start` 后 LLM 立即跳到下一个 TODO（reply_text），
  从未调 append/end，HTML 全部丢失在工作内存里
- **根因**：
  1. systemPrompt 把三阶段分块作为**主推**路径，但 LLM 实际不会在单 TODO 内连续三次
     回调同一工具（流式响应一次结束就进入下一 TODO）
  2. `mode=start` 携带 chunk 时无任何兜底，内容被丢弃
- **修复**（systemPrompt + 代码双管齐下）：
  1. **systemPrompt 重写**（`sql/postgresql/capability/sys_agent_profile_design_b.sql`）：
     - **默认推荐 `mode=write` 单次写入**（覆盖 95% 场景），明确"一个 TODO 只调一次"
     - 三阶段分块降级为"HTML 真的超过 8K tokens 才用"，并标注"必须在同一 TODO 内连续调完"
  2. **`FileWriteChunkTool.start` 防御性自动落盘**：当 start 携带非空 chunk 时
     直接走 persist 路径（不再初始化空缓冲），覆盖 LLM 把完整 HTML 塞进 start 又停止的情况
- **新增测试**：`shouldAutoPersistWhenStartCarriesContent` / `shouldStartBufferWhenChunkEmpty`
  覆盖 start+content 自动落盘 + 正常 start 流程
- **应用方法**：
  1. 应用最新 SQL 到 DB（让 systemPrompt 推送 write 模式）
  2. 重启应用
  3. `CapabilityProfileRegistry.invalidate("design")` 清缓存

### #12e LLM 反复退回 file_write（强 prompt 也压不住）✅ 已修复 2026-07-01（阶段 6，硬根因）

- **现象**：即便 systemPrompt 明令禁止 + 推荐 file_write_chunk，LLM 在长 HTML 场景
  仍反复退回 `file_write`（训练语料里最强先验），profile 白名单拦截 → 整轮失败
- **根因**：LLM 训练语料里 `file_write` 是文件写入的「最大公约数」名，
  prompt 层压不住——必须**代码层别名兜底**
- **修复**：`ToolNode.normalizeToolAlias(reqName, state)`
  - design profile 下：`file_write` / `file_save` / `save_file` → 自动重写为 `file_write_chunk`
  - 在两条 LLM 决策路径（流式 + 同步）`req.name()` 读取后立即规范化，
    早于 `rejectIfNotInProfile` 白名单校验
  - WARN 日志保留可观测性
- **设计哲学**：prompt 是「软约束」（LLM 可能不听），代码别名是「硬兜底」（必然生效）。
  生产稳定性的最后一道防线不寄希望于 LLM 听话
- **应用方法**：重启应用即生效（不需要重跑 SQL，但建议跑最新 SQL 让 prompt 也对齐）

### #12f HTML 输出在 `<body>` 处被截断（整页黑屏）✅ 已修复 2026-07-01（阶段 7）

- **现象**：文件成功落盘但浏览器渲染一片漆黑——查看 HTML 内容只到 `<body>` 标签就结束，
  主体内容全部丢失
- **根因**：`ToolNode` 4 处硬编码 `maxOutputTokens(8192)`：
  - LLM 走 function calling 把完整 HTML 塞进 `file_write_chunk.chunk` 参数时，
    8K tokens 上限在 HTML 主体未生成前就被砍掉
  - 纯文本回退路径（`callLlmStreamingForPlainText` / `callLlmSyncForPlainText`）同样 8K，
    fallback 也救不回来
  - `ChatModelFactory` javadoc 早已指出 design profile 需 16K，但实际代码从未对齐
- **修复**：抽常量 `LLM_MAX_OUTPUT_TOKENS = 32768`，4 处统一引用
  - 32K 覆盖 95% 单文件 HTML 场景（用户实际产出 ~14K tokens 的 FIFA 页面游刃有余）
  - 现代主流 LLM（GPT-4 Turbo / DeepSeek / Claude / Qwen-Max）均支持 64K+ 输出，
    32K 留有充足安全边界
- **修改位置**：
  - `decideByLlmStreaming` / `decideByLlmSync`（function calling 主路径）
  - `callLlmStreamingForPlainText` / `callLlmSyncForPlainText`（纯文本回退路径）
- **应用方法**：重启应用即生效

### #12g HTML 仍被截断在 `</head>` 处（CSS 过长 + JSON 嵌 HTML 引号转义）✅ 已修复 2026-07-01（阶段 8）

- **现象**：即便 maxOutputTokens 提到 32K，LLM 输出仍在 `</head>` 处干净收尾，body 完全缺失，
  浏览器一片黑。截断**不是 token 砍断**（那种会切在中间），是 LLM 在 function calling JSON
  参数里把 HTML 当字符串塞入时，CSS 里大量双引号（如 `font-family:"NVIDIA-EMEA"`）
  导致 JSON 字符串过早关闭，langchain4j 解析得到的就是被切的 HTML
- **根因（多因）**：
  1. CSS 过长（1500+ 行 design token 系统）烧光预算，body 还没开始就被截
  2. JSON 嵌 HTML 引号转义在长字符串上易出错
  3. **校验漏洞**：原 `validateToolArgs` 只校验 `file_write`，不校验 `file_write_chunk`——
     design profile 主路径完全绕过 HTML 结构校验，残缺 HTML 直接落盘
- **修复**（三管齐下）：
  1. **systemPrompt 加铁律**（`sys_agent_profile_design_b.sql` §4.2）：
     - HTML 总体 ≤ 6K tokens / 24KB；CSS ≤ 200 行，body ≤ 300 行
     - 禁止冗长 design token 系统（直接写 `color:#76b900`，不写 `var(--nv-primary)`）
     - 响应式只 1 个断点，不写 4 层
     - HTML 必须完整收尾到 `</html>`，**绝不停在 `</head>`**
     - 内容真的多就砍 section 数量（保留 3-4 个核心），不要砍 body
  2. **`validateToolArgs` 扩展覆盖 file_write_chunk**：
     - `mode=write` / `mode=start+chunk` 单次写入路径走和 `file_write` 一致的 HTML 校验
     - `<html>` / `</html>` / `<body>` / `</body>` 任一缺失即拒绝，错误信息直接告诉 LLM
       「CSS 过长被截断，砍 design token 系统 + 砍 section 数量」
  3. **纯文本回退扩展到 file_write_chunk**：
     - `tryPlainTextFallbackForFileWrite` 新增 file_write_chunk 分支
     - function calling 残缺时，自动切换纯文本直出模式（绕开 JSON 引号转义）
     - `isFileWriteContentMissing` 匹配模式放宽，覆盖 chunk 字段错误
- **应用方法**：
  1. 应用最新 SQL 到 DB（让 LLM 看到 HTML 体量铁律）
  2. 重启应用
  3. `CapabilityProfileRegistry.invalidate("design")` 清缓存

### #12h 改用 open-design `<artifact>` 标签纯文本模式（绕开 JSON 转义）✅ 已修复 2026-07-01（阶段 9）

- **现象**：#12g 后 LLM 仍偶发把 HTML 截在 `</head>`——即便 maxOutputTokens=32K 且校验拦截。
  根本性原因：function calling 用 JSON 字符串传 HTML，CSS 里的双引号触发 JSON 转义边界，
  国产模型在长字符串上不稳——这是**架构性**问题，加 token / 加校验都治标不治本
- **方案**：借鉴 open-design 项目，让 design profile 自由 HTML 走纯文本 `<artifact>` 标签模式
  - LLM 不调任何 function call 工具，直接在文本响应里输出：
    ```
    <artifact path="xxx.html">
    <!DOCTYPE html>...</html>
    </artifact>
    ```
  - 框架从文本抽取 artifact 内容落盘，绕开 JSON 转义 + 函数调用 token 上限
- **改动**：
  1. **`ToolNode#rescueContentFromText`**：新增 artifact 优先解析路径
     - 正则 `<artifact (?:path|name|identifier)="...">...</artifact>` 优先匹配
     - 同时抽取 path 属性作为 filename（basename），不覆盖已存在的 filename
     - 兼容 `file_write` / `file_write_chunk` / `reply_text` 三个工具
  2. **`ToolNode#buildPlainTextFallbackSystemPrompt`**：改写为 artifact 模式 prompt
     - 强约束 LLM 只输出一个 `<artifact>` 块，内部 `<!DOCTYPE html>` 开头 `</html>` 结尾
  3. **`ToolNode#buildPlainTextFallbackUserPrompt`**：提示用 artifact 标签
  4. **`generateContentByPlainText` 第 3 步**：传递真实工具名（file_write_chunk 不再被当作 file_write），
     同步 artifact 解析出的 filename 回 originalArgs
  5. **SQL `sys_agent_profile_design_b.sql` §3.1 / §4.2**：自由 HTML 推荐改用 artifact 纯文本模式，
    file_write_chunk 降级为「LLM 文本模式反复失败时」的 fallback；file_write 列入禁止
- **测试**：新增 `ToolNodeArtifactParseTest`（8 用例）覆盖：
  - 基本 artifact 解析（path/name/identifier 三种属性）
  - path basename 提取（剥离目录）
  - 不覆盖已存在 filename
  - 容忍 LLM 前言后语
  - 无 artifact 时回退到 ```html 代码块 / 裸 DOCTYPE / reply_text 兜底
- **应用方法**：
  1. 应用最新 SQL 到 DB（让 LLM 改用 artifact 输出）
  2. 重启应用
  3. `CapabilityProfileRegistry.invalidate("design")` 清缓存
