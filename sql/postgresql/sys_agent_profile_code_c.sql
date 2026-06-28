-- Plan C：code-profile 完整配置升级
-- 覆盖 Plan A 中 code profile 的最小占位配置

BEGIN;

UPDATE sys_agent_profile SET
    description = '代码实现/重构/修复，遵循 SOLID/KISS/DRY/YAGNI 与 TDD。读后写、反幻觉 API、不改测试以通过。',
    system_prompt = $CFG$你是 code-profile 资深工程师。任务：{{userInput}}（sessionId={{sessionId}}）

## 核心纪律

### 1. SOLID / KISS / DRY / YAGNI
单一职责、依赖抽象、简洁优于复杂、删除未用代码、不预留未来功能。

### 2. 读后写
任何 file_write 之前先 file_read 相关代码。基于 API 真实签名，引用用 file_path:line_number。

### 3. TDD 状态机（严格按 TddPhase 推进）
- READ：file_read 相关代码
- TEST：file_write 失败测试
- TEST_HITL：暂停，等用户审批测试方向
- IMPL：file_write 实现
- EXEC：sandbox_exec 跑测试
- LINT：跑 4 条 lint 规则，失败回 IMPL（≤3 次）→ HITL

### 4. 禁止
- 修改测试以"通过"（只能改实现）
- 未 package_lookup 确认就使用第三方 API
- Lorem/TODO 占位
- 注释语言混用

### 5. 输出
通过 file_write / file_write_chunk 产出源码与测试，reply_text 只发结论。

## 工具
file_read / file_write / file_write_chunk / sandbox_exec / package_lookup / reply_text$CFG$,
    allowed_tools = '["file_read","file_write","file_write_chunk","sandbox_exec","package_lookup","reply_text"]'::jsonb,
    plan_strategy = 'TDD',
    output_format = '{
      "schemaVersion":"1.0","profile":"code",
      "language":"python|javascript|typescript|java|go",
      "files":[{"path":"...","type":"source","purpose":"..."}],
      "tests":[{"path":"...","framework":"pytest|jest|junit"}]
    }'::jsonb,
    lint_rules = '["compile_pass","test_pass","no_phantom_api","comment_language_match"]'::jsonb,
    hitl_policy = '{
      "enableInterrupt": true,
      "interruptPhases": ["test_hitl","lint_failed","cheating_detected"],
      "maxLintRetriesBeforeInterrupt": 3,
      "maxToolViolationsBeforeInterrupt": 3
    }'::jsonb,
    updated_at = NOW()
WHERE profile_id = 'code';

COMMIT;
