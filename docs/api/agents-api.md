# Agents 业务接口文档

> 版本：v1.0.0
> 更新时间：2026-06-16
> 基础路径：`/api/v1`

## 概述

本文档描述 hs-agents 项目的业务接口，涵盖项目管理、设计系统管理、Agent 模板管理和会话管理。

**通用响应格式：**
- 成功：`{ "code": "000000", "msg": "操作成功", "data": {...} }`
- 失败：`{ "code": "错误码", "msg": "错误信息", "data": null }`

**分页响应格式：**
```json
{
  "code": "000000",
  "msg": "操作成功",
  "data": {
    "list": [...],
    "total": 100
  }
}
```

---

## 1. 项目管理 (`/api/v1/projects`)

### 1.1 项目分页列表

**地址：** `GET /api/v1/projects`

**Query 参数：**
| 参数 | 类型 | 必填 | 说明 | 示例 |
|------|------|------|------|------|
| pageNum | Integer | 否 | 页码，默认1 | 1 |
| pageSize | Integer | 否 | 每页记录数，默认10 | 10 |
| keywords | String | 否 | 关键字（项目名称） | 我的AI项目 |
| sandboxType | String | 否 | 沙箱类型 | local-sandbox |
| status | Integer | 否 | 状态（1正常/0禁用） | 1 |
| sortBy | String | 否 | 排序字段 | create_time |
| order | String | 否 | 排序方式（ASC/DESC） | DESC |

**返回示例：**
```json
{
  "code": "000000",
  "msg": "操作成功",
  "data": {
    "list": [
      {
        "id": 1,
        "ownerUserId": 100,
        "ownerUserName": "张三",
        "name": "AI客服项目",
        "description": "基于Agent的智能客服系统",
        "sandboxType": "local-sandbox",
        "status": 1,
        "createTime": "2026-06-16 10:00:00",
        "updateTime": "2026-06-16 15:30:00"
      }
    ],
    "total": 1
  }
}
```

**错误码：**
- 无特殊错误码

**所需权限：** 登录用户（数据权限：仅本人数据）

---

### 1.2 新增项目

**地址：** `POST /api/v1/projects`

**Body 参数：**
```json
{
  "name": "AI客服项目",
  "description": "基于Agent的智能客服系统",
  "sandboxType": "local-sandbox",
  "status": 1
}
```

| 参数 | 类型 | 必填 | 说明 | 验证规则 |
|------|------|------|------|----------|
| name | String | 是 | 项目名称 | 不能为空 |
| description | String | 否 | 项目描述 | - |
| sandboxType | String | 是 | 沙箱类型 | local-sandbox/remote-cloud/third-party-api |
| status | Integer | 是 | 状态 | 0或1 |

**返回示例：**
```json
{
  "code": "000000",
  "msg": "操作成功"
}
```

**错误码：**
- 参数校验失败

**所需权限：** 登录用户

---

### 1.3 获取项目详情

**地址：** `GET /api/v1/projects/{id}`

**Path 参数：**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | 是 | 项目ID |

**返回示例：** 同 1.1 列表项

**错误码：**
- 404：项目不存在

**所需权限：** 登录用户（数据权限：仅本人数据）

---

### 1.4 获取项目表单数据

**地址：** `GET /api/v1/projects/{id}/form`

**用途：** 编辑项目时获取表单初始数据

**返回示例：**
```json
{
  "code": "000000",
  "msg": "操作成功",
  "data": {
    "id": 1,
    "name": "AI客服项目",
    "description": "基于Agent的智能客服系统",
    "sandboxType": "local-sandbox",
    "status": 1
  }
}
```

**所需权限：** 登录用户（数据权限：仅本人数据）

---

### 1.5 修改项目

**地址：** `PUT /api/v1/projects/{id}`

**Path 参数：**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | 是 | 项目ID |

**Body 参数：** 同 1.2 新增项目

**返回示例：** 同 1.2

**错误码：**
- 400：项目不存在

**所需权限：** 登录用户（数据权限：仅本人数据）

---

### 1.6 删除项目

**地址：** `DELETE /api/v1/projects/{ids}`

**Path 参数：**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| ids | String | 是 | 项目ID，多个以逗号分隔（如"1,2,3"） |

**返回示例：**
```json
{
  "code": "000000",
  "msg": "操作成功"
}
```

**错误码：**
- 400：ID不能为空

**所需权限：** 登录用户（数据权限：仅本人数据）

---

## 2. 设计系统管理 (`/api/v1/design-systems`)

### 2.1 设计系统分页列表

**地址：** `GET /api/v1/design-systems`

**Query 参数：**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| pageNum | Integer | 否 | 页码，默认1 |
| pageSize | Integer | 否 | 每页记录数，默认10 |
| keywords | String | 否 | 关键字（名称） |
| category | String | 否 | 分类（personal/official） |
| type | String | 否 | 类型（web/app） |
| publishStatus | Integer | 否 | 发布状态（1已发布/0草稿） |

**返回示例：**
```json
{
  "code": "000000",
  "msg": "操作成功",
  "data": {
    "list": [
      {
        "id": 1,
        "ownerUserId": 100,
        "ownerUserName": "张三",
        "name": "Material Design",
        "category": "official",
        "categoryLabel": "官方",
        "type": "web",
        "typeLabel": "网页",
        "brandSpec": "{\"colors\": {...}}",
        "codeSpec": "{\"naming\": \"camelCase\"}",
        "assets": "{\"icons\": [...]}",
        "publishStatus": 1,
        "publishStatusLabel": "已发布",
        "createTime": "2026-06-16 10:00:00",
        "updateTime": "2026-06-16 15:30:00"
      }
    ],
    "total": 1
  }
}
```

**所需权限：** 登录用户（数据权限：仅本人数据）

---

### 2.2 设计系统模板列表

**地址：** `GET /api/v1/design-systems/templates`

**用途：** 获取已发布的官方设计系统，用于下拉选择

**返回示例：**
```json
{
  "code": "000000",
  "msg": "操作成功",
  "data": [
    {
      "id": 1,
      "name": "Material Design",
      "type": "web",
      "categoryLabel": "官方"
    }
  ]
}
```

**所需权限：** 登录用户

---

### 2.3 新增设计系统

**地址：** `POST /api/v1/design-systems`

**Body 参数：**
```json
{
  "name": "Material Design",
  "category": "official",
  "type": "web",
  "brandSpec": "{\"colors\": {...}}",
  "codeSpec": "{\"naming\": \"camelCase\"}",
  "assets": "{\"icons\": [...]}",
  "publishStatus": 0
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| name | String | 是 | 名称 |
| category | String | 是 | 分类（personal/official） |
| type | String | 是 | 类型（web/app） |
| brandSpec | String | 否 | 品牌规范（JSON字符串） |
| codeSpec | String | 否 | 代码规范（JSON字符串） |
| assets | String | 否 | 资产（JSON字符串） |
| publishStatus | Integer | 否 | 发布状态（0或1） |

**返回示例：**
```json
{
  "code": "000000",
  "msg": "操作成功"
}
```

**所需权限：** 登录用户

---

### 2.4 获取设计系统详情

**地址：** `GET /api/v1/design-systems/{id}`

**Path 参数：**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | 是 | 设计系统ID |

**返回示例：** 同 2.1 列表项

**所需权限：** 登录用户（数据权限：仅本人数据）

---

### 2.5 获取设计系统表单数据

**地址：** `GET /api/v1/design-systems/{id}/form`

**返回示例：**
```json
{
  "code": "000000",
  "msg": "操作成功",
  "data": {
    "id": 1,
    "name": "Material Design",
    "category": "official",
    "type": "web",
    "brandSpec": "{\"colors\": {...}}",
    "codeSpec": "{\"naming\": \"camelCase\"}",
    "assets": "{\"icons\": [...]}",
    "publishStatus": 0
  }
}
```

**所需权限：** 登录用户（数据权限：仅本人数据）

---

### 2.6 修改设计系统

**地址：** `PUT /api/v1/design-systems/{id}`

**Path 参数：**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | 是 | 设计系统ID |

**Body 参数：** 同 2.3

**返回示例：** 同 2.3

**所需权限：** 登录用户（数据权限：仅本人数据）

---

### 2.7 删除设计系统

**地址：** `DELETE /api/v1/design-systems/{ids}`

**Path 参数：**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| ids | String | 是 | 设计系统ID，多个以逗号分隔 |

**返回示例：**
```json
{
  "code": "000000",
  "msg": "操作成功"
}
```

**所需权限：** 登录用户（数据权限：仅本人数据）

---

### 2.8 发布设计系统

**地址：** `POST /api/v1/design-systems/{id}/publish`

**Path 参数：**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | 是 | 设计系统ID |

**返回示例：**
```json
{
  "code": "000000",
  "msg": "操作成功"
}
```

**所需权限：** 登录用户（数据权限：仅本人数据）

---

## 3. Agent 模板管理 (`/api/v1/agent-templates`)

### 3.1 Agent模板分页列表

**地址：** `GET /api/v1/agent-templates`

**Query 参数：**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| pageNum | Integer | 否 | 页码，默认1 |
| pageSize | Integer | 否 | 每页记录数，默认10 |
| keywords | String | 否 | 关键字（名称） |
| hitlEnabled | Boolean | 否 | 是否启用HITL审批 |

**返回示例：**
```json
{
  "code": "000000",
  "msg": "操作成功",
  "data": {
    "list": [
      {
        "id": 1,
        "ownerUserId": 100,
        "ownerUserName": "张三",
        "name": "代码审查助手",
        "instructions": "你是一个专业的代码审查助手...",
        "subAgents": "[{\"name\":\"frontend\",\"role\":\"前端专家\"}]",
        "enabledTools": "[\"file-read\",\"file-write\"]",
        "hitlEnabled": true,
        "sandboxConfig": "{\"type\":\"local-sandbox\"}",
        "createTime": "2026-06-16 10:00:00",
        "updateTime": "2026-06-16 15:30:00"
      }
    ],
    "total": 1
  }
}
```

**所需权限：** 登录用户（数据权限：仅本人数据）

---

### 3.2 新增Agent模板

**地址：** `POST /api/v1/agent-templates`

**Body 参数：**
```json
{
  "name": "代码审查助手",
  "instructions": "你是一个专业的代码审查助手...",
  "subAgents": "[{\"name\":\"frontend\",\"role\":\"前端专家\"}]",
  "enabledTools": "[\"file-read\",\"file-write\"]",
  "hitlEnabled": true,
  "sandboxConfig": "{\"type\":\"local-sandbox\"}"
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| name | String | 是 | 名称 |
| instructions | String | 是 | 指令文本 |
| subAgents | String | 否 | 子Agent配置（JSON字符串） |
| enabledTools | String | 否 | 启用的工具（JSON字符串） |
| hitlEnabled | Boolean | 否 | 是否启用HITL审批 |
| sandboxConfig | String | 否 | 沙箱配置（JSON字符串） |

**返回示例：**
```json
{
  "code": "000000",
  "msg": "操作成功"
}
```

**所需权限：** 登录用户

---

### 3.3 获取Agent模板详情

**地址：** `GET /api/v1/agent-templates/{id}`

**返回示例：** 同 3.1 列表项

**所需权限：** 登录用户（数据权限：仅本人数据）

---

### 3.4 获取Agent模板表单数据

**地址：** `GET /api/v1/agent-templates/{id}/form`

**返回示例：**
```json
{
  "code": "000000",
  "msg": "操作成功",
  "data": {
    "id": 1,
    "name": "代码审查助手",
    "instructions": "你是一个专业的代码审查助手...",
    "subAgents": "[{\"name\":\"frontend\",\"role\":\"前端专家\"}]",
    "enabledTools": "[\"file-read\",\"file-write\"]",
    "hitlEnabled": true,
    "sandboxConfig": "{\"type\":\"local-sandbox\"}"
  }
}
```

**所需权限：** 登录用户（数据权限：仅本人数据）

---

### 3.5 修改Agent模板

**地址：** `PUT /api/v1/agent-templates/{id}`

**Body 参数：** 同 3.2

**返回示例：** 同 3.2

**所需权限：** 登录用户（数据权限：仅本人数据）

---

### 3.6 删除Agent模板

**地址：** `DELETE /api/v1/agent-templates/{ids}`

**返回示例：**
```json
{
  "code": "000000",
  "msg": "操作成功"
}
```

**所需权限：** 登录用户（数据权限：仅本人数据）

---

## 4. SSE 流式接口

### 4.1 流式执行 Agent

**地址：** `GET /api/v1/agent/sessions/{sessionId}/stream`

**Content-Type：** `text/event-stream`

**Path 参数：**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| sessionId | String | 是 | 会话ID（从创建会话接口获取） |

**Query 参数：**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| input | String | 是 | 用户输入文本 |

**请求头：**
| 参数 | 说明 |
|------|------|
| Authorization | Bearer {token}（需要登录认证） |

**调用流程：**
1. 调用 `POST /api/v1/agent/sessions` 创建会话，获得 `sessionId`
2. 调用 `GET /api/v1/agent/sessions/{sessionId}/stream?input=xxx` 开始流式执行

**SSE 推送格式：**
```
event: agent_event
data: {"type":"...","message":"...","data":{...},"timestamp":...}
```

### 4.2 SSE 事件类型

**15 种事件类型清单：**

| 事件类型 | type 值 | 说明 | data 结构 |
|---------|---------|------|----------|
| 计划创建 | `plan_created` | 计划创建/更新 | `DeepAgentState` |
| TODO 开始 | `todo_started` | TODO 开始执行 | `{ id, description }` |
| TODO 完成 | `todo_completed` | TODO 执行完成 | `TodoItem` |
| 工具调用 | `tool_call` | 工具调用 | `{ tool, input, output }` |
| 子 Agent 委派 | `sub_agent_delegated` | 子 Agent 委派 | `{ parentAgent, subAgent, task }` |
| 子 Agent 开始 | `sub_agent_started` | 子 Agent 开始执行 | `SubAgentContext` |
| 子 Agent 节点执行 | `sub_agent_node_execution` | 子 Agent 内部节点执行 | `SubAgentContext` |
| 子 Agent 完成 | `sub_agent_completed` | 子 Agent 执行完成 | `SubAgentContext` |
| 子 Agent 失败 | `sub_agent_failed` | 子 Agent 执行失败 | `{ message, error }` |
| 节点执行 | `node_execution` | 节点执行 | `DeepAgentState` |
| 最终响应 | `final_response` | 执行完成（流结束） | `null` |
| 错误 | `error` | 执行错误（流结束） | `{ message, stack? }` |
| 中断 | `interrupt` | HITL 中断（等待人工审批） | `InterruptContext` |
| 审批已接收 | `approval_received` | 审批决策已接收 | `{ decision, feedback? }` |
| 等待审批 | `awaiting_approval` | 等待审批中 | `InterruptContext` |

### 4.3 SSE 事件示例

**通用结构：**
```json
{
  "type": "事件类型",
  "message": "事件描述",
  "data": { /* 事件特定数据 */ },
  "timestamp": 1718522400000
}
```

**各事件 data 示例：**

```json
// plan_created
{
  "type": "plan_created",
  "message": "计划已创建",
  "data": {
    "sessionId": "abc123",
    "messages": [...],
    "todos": [...],
    "files": {...}
  },
  "timestamp": 1718522400000
}

// todo_started
{
  "type": "todo_started",
  "message": "开始执行任务",
  "data": {
    "id": "todo-1",
    "description": "创建用户管理模块"
  },
  "timestamp": 1718522400000
}

// todo_completed
{
  "type": "todo_completed",
  "message": "任务完成",
  "data": {
    "id": "todo-1",
    "description": "创建用户管理模块",
    "status": "completed",
    "result": "已创建 UserController、UserService、UserMapper",
    "assignedAgent": null,
    "updatedAt": "2026-06-16T10:30:00"
  },
  "timestamp": 1718522400000
}

// tool_call
{
  "type": "tool_call",
  "message": "调用工具",
  "data": {
    "tool": "file_write",
    "input": {"path": "/src/UserController.java", "content": "..."},
    "output": {"success": true, "message": "文件已写入"}
  },
  "timestamp": 1718522400000
}

// sub_agent_delegated
{
  "type": "sub_agent_delegated",
  "message": "委派给子 Agent",
  "data": {
    "parentAgent": "main",
    "subAgent": "frontend",
    "task": "创建 React 组件"
  },
  "timestamp": 1718522400000
}

// node_execution
{
  "type": "node_execution",
  "message": "节点执行中",
  "data": {
    "sessionId": "abc123",
    "messages": [...],
    "todos": [...],
    "files": {...}
  },
  "timestamp": 1718522400000
}

// final_response (流结束)
{
  "type": "final_response",
  "message": "执行完成",
  "data": null,
  "timestamp": 1718522400000
}

// error (流结束)
{
  "type": "error",
  "message": "执行失败",
  "data": {
    "message": "工具调用失败",
    "stack": "Error: File not found\n    at ..."
  },
  "timestamp": 1718522400000
}

// interrupt (HITL 中断)
{
  "type": "interrupt",
  "message": "执行已暂停，等待人工审批",
  "data": {
    "nodeName": "tool",
    "sessionId": "abc123",
    "summary": "图执行在节点 [tool] 前暂停"
  },
  "timestamp": 1718522400000
}

// approval_received
{
  "type": "approval_received",
  "message": "审批决策: APPROVED",
  "data": {
    "decision": "APPROVED",
    "feedback": "继续执行"
  },
  "timestamp": 1718522400000
}

// awaiting_approval
{
  "type": "awaiting_approval",
  "message": "等待审批",
  "data": {
    "nodeName": "tool",
    "sessionId": "abc123",
    "summary": "等待人工审批"
  },
  "timestamp": 1718522400000
}
```

### 4.4 流结束事件

**正常完成：**
- 事件类型：`final_response`
- data：`null`
- 前端应关闭 SSE 连接

**错误结束：**
- 事件类型：`error`
- data：包含错误信息
- 前端应显示错误并关闭 SSE 连接

### 4.5 TypeScript 类型定义

```typescript
type AgentEventType = 
  'plan_created' | 'todo_started' | 'todo_completed' | 'tool_call' |
  'sub_agent_delegated' | 'sub_agent_started' | 'sub_agent_node_execution' |
  'sub_agent_completed' | 'sub_agent_failed' | 'node_execution' |
  'final_response' | 'error' | 'interrupt' | 'approval_received' |
  'awaiting_approval';

interface AgentEvent {
  type: AgentEventType;
  message: string;
  data: any;
  timestamp: number;
}

interface TodoItem {
  id: string;
  description: string;
  status: 'pending' | 'in_progress' | 'completed' | 'failed';
  result?: string;
  assignedAgent?: string;
  updatedAt: string;
}

interface InterruptContext {
  nodeName: string;
  sessionId: string;
  summary: string;
}
```

---

## 5. 会话管理扩展 (`/api/v1/agent`）

### 5.1 当前用户会话列表

**地址：** `GET /api/v1/agent/sessions`

**返回示例：**
```json
{
  "code": "000000",
  "msg": "操作成功",
  "data": [
    {
      "sessionId": "abc123def456",
      "userId": 100,
      "status": "COMPLETED",
      "todos": [...],
      "files": {...},
      "finalResponse": "任务已完成",
      "enabledTools": [...],
      "hitlEnabled": false,
      "createdAt": "2026-06-16 10:00:00",
      "updatedAt": "2026-06-16 15:30:00"
    }
  ]
}
```

**所需权限：** 登录用户（仅返回本人会话）

---

### 5.2 删除会话

**地址：** `DELETE /api/v1/agent/sessions/{sessionId}`

**Path 参数：**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| sessionId | String | 是 | 会话ID |

**返回示例：**
```json
{
  "code": "000000",
  "msg": "操作成功"
}
```

**所需权限：** 登录用户（仅能删除本人会话）

---

## 附录

### A. 状态码说明

| code | msg | 说明 |
|------|-----|------|
| 000000 | 操作成功 | 请求成功 |
| 999999 | 系统错误 | 服务器内部错误 |
| 400001 | 参数校验失败 | 请求参数不符合要求 |

### B. 数据权限说明

- 所有列表查询接口均受数据权限控制
- 普通用户只能查看本人创建的数据（`ownerUserId = 当前用户ID`）
- 管理员可以查看所有数据

### C. 多租户说明

- 所有接口自动隔离租户数据（通过 `tenant_id` 字段）
- 跨租户操作需要管理员权限
