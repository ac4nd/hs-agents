-- agent_session 表改名 + 时间字段改名迁移脚本
-- Copyright (c) 2026-present, HyperSense
--
-- 说明：
--   1. 将 sys_agent_session 表改名为 agent_session（去掉 sys_ 前缀）
--   2. 将 created_at / updated_at 字段统一改名为 create_time / update_time
--   3. 重建对应索引
--
-- 适用环境：已部署 sys_agent_session 旧表结构与字段名的环境
-- 新环境：直接执行 agents_business_tables.sql 即可，无需本脚本
--
-- 执行前建议备份：
--   pg_dump -U postgres -d hypersense -t sys_agent_session > backup_session.sql

BEGIN;

-- 表改名（PostgreSQL 表改名后，索引/约束会自动跟随，但建议同步改名以保持命名一致）
ALTER TABLE IF EXISTS sys_agent_session RENAME TO agent_session;

-- 字段改名（仅当旧字段存在时执行；幂等保护：跳过已迁移的环境）
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'agent_session' AND column_name = 'created_at'
    ) THEN
        ALTER TABLE agent_session RENAME COLUMN created_at TO create_time;
    END IF;
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'agent_session' AND column_name = 'updated_at'
    ) THEN
        ALTER TABLE agent_session RENAME COLUMN updated_at TO update_time;
    END IF;
END $$;

-- 索引重建（旧索引名基于旧表/旧字段，需先 DROP 再 CREATE）
DROP INDEX IF EXISTS idx_agent_session_created_at;
DROP INDEX IF EXISTS idx_agent_session_tenant_id;
DROP INDEX IF EXISTS idx_agent_session_user_id;
DROP INDEX IF EXISTS idx_agent_session_status;
DROP INDEX IF EXISTS idx_agent_session_create_time;

CREATE INDEX IF NOT EXISTS idx_agent_session_tenant_id ON agent_session (tenant_id);
CREATE INDEX IF NOT EXISTS idx_agent_session_user_id ON agent_session (user_id);
CREATE INDEX IF NOT EXISTS idx_agent_session_status ON agent_session (status);
CREATE INDEX IF NOT EXISTS idx_agent_session_create_time ON agent_session (create_time);

-- COMMENT 显式同步（与 agents_business_tables.sql 保持一致）
COMMENT ON TABLE agent_session IS 'Agent会话表';
COMMENT ON COLUMN agent_session.create_time IS '创建时间';
COMMENT ON COLUMN agent_session.update_time IS '更新时间';

COMMIT;
