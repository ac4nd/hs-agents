-- hs-agents 业务表 - PostgreSQL 16+
-- Copyright (c) 2026-present, HyperSense
--
-- 说明：Agent 相关业务表（DesignSystem、AgentTemplate、AgentSession）
-- 注：Project 模块已移除（2026-06-25），相关表/字段清理见 project_drop.sql / agent_session_remove_project.sql

-- ----------------------------
-- Table structure for sys_design_system
-- ----------------------------
DROP TABLE IF EXISTS sys_design_system;
CREATE TABLE sys_design_system (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT DEFAULT 0,
    owner_user_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    category VARCHAR(50) NOT NULL,
    type VARCHAR(50) NOT NULL,
    brand_spec TEXT,
    code_spec TEXT,
    assets TEXT,
    publish_status SMALLINT DEFAULT 0,
    create_by BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_deleted SMALLINT DEFAULT 0,
    CONSTRAINT uk_design_system_name UNIQUE (tenant_id, name, is_deleted)
);

CREATE INDEX idx_design_system_tenant_id ON sys_design_system (tenant_id);
CREATE INDEX idx_design_system_owner_user_id ON sys_design_system (owner_user_id);
CREATE INDEX idx_design_system_category ON sys_design_system (category);
CREATE INDEX idx_design_system_type ON sys_design_system (type);
CREATE INDEX idx_design_system_publish_status ON sys_design_system (publish_status);

COMMENT ON TABLE sys_design_system IS '设计系统表';
COMMENT ON COLUMN sys_design_system.id IS '主键';
COMMENT ON COLUMN sys_design_system.tenant_id IS '租户ID';
COMMENT ON COLUMN sys_design_system.owner_user_id IS '所属用户ID';
COMMENT ON COLUMN sys_design_system.name IS '名称';
COMMENT ON COLUMN sys_design_system.category IS '分类(personal/official)';
COMMENT ON COLUMN sys_design_system.type IS '类型(web/app)';
COMMENT ON COLUMN sys_design_system.brand_spec IS '品牌规范(JSON)';
COMMENT ON COLUMN sys_design_system.code_spec IS '代码规范(JSON)';
COMMENT ON COLUMN sys_design_system.assets IS '资产(JSON)';
COMMENT ON COLUMN sys_design_system.publish_status IS '发布状态(1-已发布 0-草稿)';
COMMENT ON COLUMN sys_design_system.create_by IS '创建人ID';
COMMENT ON COLUMN sys_design_system.create_time IS '创建时间';
COMMENT ON COLUMN sys_design_system.update_by IS '更新人ID';
COMMENT ON COLUMN sys_design_system.update_time IS '更新时间';
COMMENT ON COLUMN sys_design_system.is_deleted IS '逻辑删除标识(1-已删除 0-未删除)';

-- ----------------------------
-- Table structure for sys_agent_template
-- ----------------------------
DROP TABLE IF EXISTS sys_agent_template;
CREATE TABLE sys_agent_template (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT DEFAULT 0,
    owner_user_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    instructions TEXT NOT NULL,
    sub_agents TEXT,
    enabled_tools TEXT,
    hitl_enabled BOOLEAN DEFAULT FALSE,
    sandbox_config TEXT,
    create_by BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_deleted SMALLINT DEFAULT 0,
    CONSTRAINT uk_agent_template_name UNIQUE (tenant_id, name, is_deleted)
);

CREATE INDEX idx_agent_template_tenant_id ON sys_agent_template (tenant_id);
CREATE INDEX idx_agent_template_owner_user_id ON sys_agent_template (owner_user_id);
CREATE INDEX idx_agent_template_hitl_enabled ON sys_agent_template (hitl_enabled);

COMMENT ON TABLE sys_agent_template IS 'Agent模板表';
COMMENT ON COLUMN sys_agent_template.id IS '主键';
COMMENT ON COLUMN sys_agent_template.tenant_id IS '租户ID';
COMMENT ON COLUMN sys_agent_template.owner_user_id IS '所属用户ID';
COMMENT ON COLUMN sys_agent_template.name IS '名称';
COMMENT ON COLUMN sys_agent_template.instructions IS '指令文本';
COMMENT ON COLUMN sys_agent_template.sub_agents IS '子Agent配置(JSON)';
COMMENT ON COLUMN sys_agent_template.enabled_tools IS '启用的工具(JSON)';
COMMENT ON COLUMN sys_agent_template.hitl_enabled IS '是否启用HITL审批';
COMMENT ON COLUMN sys_agent_template.sandbox_config IS '沙箱配置(JSON)';
COMMENT ON COLUMN sys_agent_template.create_by IS '创建人ID';
COMMENT ON COLUMN sys_agent_template.create_time IS '创建时间';
COMMENT ON COLUMN sys_agent_template.update_by IS '更新人ID';
COMMENT ON COLUMN sys_agent_template.update_time IS '更新时间';
COMMENT ON COLUMN sys_agent_template.is_deleted IS '逻辑删除标识(1-已删除 0-未删除)';

-- ----------------------------
-- Table structure for agent_session
-- ----------------------------
DROP TABLE IF EXISTS agent_session;
CREATE TABLE agent_session (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    tenant_id BIGINT DEFAULT 0,
    user_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    todos TEXT,
    files TEXT,
    final_response TEXT,
    enabled_tools TEXT,
    hitl_enabled BOOLEAN DEFAULT FALSE,
    hitl_interrupt_nodes TEXT,
    interrupted_node VARCHAR(128),
    interrupt_context TEXT,
    pending_approval TEXT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_deleted SMALLINT DEFAULT 0,
    CONSTRAINT uk_agent_session_id UNIQUE (session_id, is_deleted)
);

CREATE INDEX idx_agent_session_tenant_id ON agent_session (tenant_id);
CREATE INDEX idx_agent_session_user_id ON agent_session (user_id);
CREATE INDEX idx_agent_session_status ON agent_session (status);
CREATE INDEX idx_agent_session_create_time ON agent_session (create_time);

COMMENT ON TABLE agent_session IS 'Agent会话表';
COMMENT ON COLUMN agent_session.id IS '主键';
COMMENT ON COLUMN agent_session.session_id IS '会话ID';
COMMENT ON COLUMN agent_session.tenant_id IS '租户ID';
COMMENT ON COLUMN agent_session.user_id IS '所属用户ID';
COMMENT ON COLUMN agent_session.status IS '会话状态';
COMMENT ON COLUMN agent_session.todos IS 'TODO列表(JSON)';
COMMENT ON COLUMN agent_session.files IS '产物文件(JSON)';
COMMENT ON COLUMN agent_session.final_response IS '最终响应';
COMMENT ON COLUMN agent_session.enabled_tools IS '启用的工具(JSON)';
COMMENT ON COLUMN agent_session.hitl_enabled IS '是否启用HITL审批';
COMMENT ON COLUMN agent_session.hitl_interrupt_nodes IS 'HITL中断节点列表(JSON)';
COMMENT ON COLUMN agent_session.interrupted_node IS '触发中断的节点名';
COMMENT ON COLUMN agent_session.interrupt_context IS '中断上下文(JSON)';
COMMENT ON COLUMN agent_session.pending_approval IS '待处理的审批请求(JSON)';
COMMENT ON COLUMN agent_session.create_time IS '创建时间';
COMMENT ON COLUMN agent_session.update_time IS '更新时间';
COMMENT ON COLUMN agent_session.is_deleted IS '逻辑删除标识(1-已删除 0-未删除)';
