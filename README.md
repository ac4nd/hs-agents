# GodlikeAgents

<p align="center">
  <strong>企业级 AI Agent 多租户权限管理平台</strong>
</p>

<p align="center">
  <a href="./LICENSE"><img src="https://img.shields.io/badge/License-MIT-blue.svg" alt="License: MIT"></a>
  <img src="https://img.shields.io/badge/JDK-17-green.svg" alt="JDK 17">
  <img src="https://img.shields.io/badge/Spring%20Boot-4.0.5-brightgreen.svg" alt="Spring Boot 4.0.5">
  <img src="https://img.shields.io/badge/PostgreSQL-16-blue.svg" alt="PostgreSQL 16">
</p>

---

## 项目简介

GodlikeAgents 是一个基于 **Java 17 + Spring Boot 4 + Spring Security 6** 构建的企业级 AI Agent 平台，集成了多租户权限管理和先进的 AI Agent 能力。系统采用 **LangGraph4j + LangChain4j** 实现图编排式多 Agent 协作，支持工具调用、沙箱执行、长期记忆、人机协作（HITL）等核心 AI 特性。

## 核心特性

### AI Agent 框架

- **图编排引擎** — 基于 LangGraph4j StateGraph 的任务规划-执行-路由流程
- **多级 Agent 协作** — 主 Agent 可委派子 Agent 独立执行，支持递归嵌套
- **工具系统** — 内置网络搜索（SearXNG）、文件读写、代码沙箱执行，支持自定义扩展
- **技能系统** — 从文件系统动态加载 SKILL.md 技能定义，运行时按需注入
- **长期记忆** — 基于智谱 Embedding-3 + PostgreSQL pgvector 的语义检索记忆
- **沙箱执行** — 支持本地进程、Docker 容器、Podman、远程沙箱四种隔离模式
- **人机协作（HITL）** — Agent 执行关键操作前可中断等待人工审批
- **中间件管线** — 日志记录、消息压缩、大输出卸载等可插拔中间件
- **SSE 流式输出** — 实时推送 Agent 思考过程和执行结果

### 系统管理

- **多租户架构** — 数据库行级隔离（tenant_id），基于 ThreadLocal 的上下文传播
- **RBAC 权限** — 用户-角色-菜单-部门四级权限体系
- **数据权限** — 五种数据范围（全部/本部门及子部门/本部门/仅本人/自定义）
- **认证体系** — JWT 无状态 / Redis 有状态双会话模式，支持密码/短信/微信小程序登录
- **文件管理** — 策略模式实现 MinIO / 阿里云 OSS / 本地存储
- **代码生成** — Velocity 模板引擎，一键生成前后端 CRUD 代码
- **实时消息** — 基于 SSE 的在线通知和字典变更推送

## 技术栈

| 分类 | 技术 | 版本 |
|------|------|------|
| 核心框架 | Spring Boot | 4.0.5 |
| 安全框架 | Spring Security | 6.x |
| ORM | MyBatis-Plus | 3.5.15 |
| 数据库 | PostgreSQL + pgvector | 16 |
| 缓存 | Redis + Caffeine | 7.2.3 / 2.9.3 |
| AI 编排 | LangGraph4j | 1.8.16 |
| LLM 集成 | LangChain4j | 1.0.0 |
| 向量模型 | 智谱 Embedding-3 | 0.3.3 |
| 对象映射 | MapStruct | 1.6.3 |
| 分布式锁 | Redisson | 4.1.0 |
| 连接池 | Druid | 1.2.24 |
| API 文档 | Knife4j + SpringDoc | 4.5.0 |
| 对象存储 | MinIO / 阿里云 OSS | 8.5.10 |
| 任务调度 | XXL-Job | 3.2.0 |
| Excel | FastExcel | 1.3.0 |
| 工具库 | Hutool | 5.8.41 |
| 容器沙箱 | Docker Java | 3.7.1 |

## 项目结构

```
hs-agents/
├── docker/                          # 基础设施
│   ├── docker-compose.yml           # PostgreSQL, Redis, MinIO, XXL-Job, SearXNG
│   ├── sandbox/                     # 多语言沙箱镜像 (Python/Node.js/Bash)
│   └── searxng/                     # SearXNG 搜索引擎配置
├── sql/
│   └── postgresql/                  # 数据库初始化脚本
├── src/main/java/com/hypersense/boot/
│   ├── agents/                      # Agent 业务层 (Controller/Service)
│   ├── auth/                        # 认证模块 (JWT/短信/微信登录)
│   ├── codegen/                     # 代码生成器
│   ├── common/                      # 公共组件 (基类/注解/异常/工具)
│   ├── file/                        # 文件管理 (MinIO/OSS/本地)
│   ├── framework/                   # 基础设施层
│   │   ├── agents/                  # ★ AI Agent 核心
│   │   │   ├── engine/              # 图编排引擎 (Plan/Execute/Delegate/Tool/Finalize)
│   │   │   ├── tool/                # 内置工具 (搜索/文件/沙箱)
│   │   │   ├── sandbox/             # 沙箱执行器 (Local/Docker/Podman/Remote)
│   │   │   ├── skill/               # 技能系统 (动态加载/注册)
│   │   │   ├── memory/              # 长期记忆 (Embedding/pgvector)
│   │   │   ├── checkpoint/          # 检查点持久化 (PostgreSQL)
│   │   │   ├── middleware/          # 中间件管线
│   │   │   └── config/              # Agent 配置
│   │   ├── security/                # Security 配置 & 过滤器链
│   │   ├── tenant/                  # 多租户拦截
│   │   ├── mybatis/                 # MyBatis 增强 (数据权限/分页)
│   │   └── web/                     # Web 基础设施 (限流/异常/CORS)
│   ├── message/                     # SSE 实时消息
│   └── system/                      # 系统管理 (用户/角色/菜单/部门/租户/字典/日志)
└── src/main/resources/
    ├── application.yml              # 主配置
    ├── application-dev.yml          # 开发环境
    ├── application-prod.yml         # 生产环境
    ├── codegen.yml                  # 代码生成器配置
    ├── mapper/                      # MyBatis XML
    └── templates/                   # Velocity 代码生成模板
```

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- Docker & Docker Compose（用于基础设施）

### 1. 启动基础设施

```bash
docker-compose -f docker/docker-compose.yml -p godlikeagents up -d
```

启动后包含以下服务：

| 服务 | 端口 | 说明 |
|------|------|------|
| PostgreSQL (pgvector) | 5432 | 数据库，账号 `postgres/123456` |
| Redis | 6379 | 缓存，密码 `123456` |
| MinIO | 9000 / 9001 | 对象存储，控制台 9001 |
| XXL-Job Admin | 8080 | 任务调度中心 |
| SearXNG | 8888 | Agent 网络搜索引擎 |

### 2. 配置 LLM

编辑 `application-dev.yml`，配置大模型 API：

```yaml
agent:
  llm:
    openai:
      endpoint: https://open.bigmodel.cn/api/coding/paas/v4
      api-key: ${YOUR_API_KEY}
      model-name: glm-4.7
```

支持所有 OpenAI 兼容 API（OpenAI / DeepSeek / 通义千问 / 智谱等）。

### 3. 构建运行

```bash
# 构建
mvn clean package -DskipTests

# 运行
java -jar target/godlikeagents.jar

# 或使用 Maven 直接运行
mvn spring-boot:run
```

应用启动后访问：
- API 文档：`http://localhost:8000/doc.html`
- 应用端口：**8000**（dev）/ **8989**（prod）

## Agent 使用示例

### Builder API

```java
String result = GodlikeAgent.builder()
    .apiKey("your-api-key")
    .endpoint("https://open.bigmodel.cn/api/coding/paas/v4")
    .modelName("glm-4.7")
    .addTool(new InternetSearchTool(searchEndpoint))
    .addTool(new FileReadTool())
    .enableMessageCompression()
    .enableHitl()
    .skills("/path/to/skills")
    .build()
    .run("搜索今天的科技新闻并生成摘要");
```

### REST API

```bash
# 创建会话
curl -X POST http://localhost:8000/api/v1/agent/sessions \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{"prompt": "分析这段代码的性能问题", "tools": ["search", "sandbox"]}'

# SSE 流式执行
curl -N http://localhost:8000/api/v1/agent/sessions/{id}/stream \
  -H "Authorization: Bearer {token}"

# 人机协作审批
curl -X POST http://localhost:8000/api/v1/agent/sessions/{id}/approve \
  -H "Authorization: Bearer {token}" \
  -d '{"approved": true}'
```

## Agent 架构

```
用户请求
   │
   ▼
┌──────────┐     ┌──────────┐     ┌──────────────┐
│ PlanNode │────▶│ ExecuteNode │───▶│ DelegateNode │ (子Agent委派)
└──────────┘     └─────┬────┘     └──────────────┘
                       │
                  ┌────▼────┐
                  │ ToolNode │ (工具调用 + 重试)
                  └────┬────┘
                       │
                ┌──────▼──────┐
                │ FinalizeNode │ (结果综合)
                └──────────────┘
                       │
                       ▼
                  用户响应 / SSE流
```

- **PlanNode** — 分析任务，拆解为 TODO 列表
- **ExecuteNode** — 逐步执行 TODO，协调工具和子 Agent
- **DelegateNode** — 创建独立子 Agent 上下文，支持递归委派
- **ToolNode** — 执行工具调用，支持指数退避重试
- **FinalizeNode** — 综合所有执行结果，生成最终响应
- **路由节点** — 条件路由：完成 → 终止，未完成 → 继续执行

## 安全架构

```
请求 → RateLimiterFilter → CaptchaFilter → TokenAuthenticationFilter → 业务处理
```

- **双会话模式**：JWT（无状态）或 Redis Token，通过 `security.session.type` 切换
- **认证提供者**：密码认证 / 短信认证
- **白名单**：`security.ignore-urls`（跳过鉴权）/ `security.unsecured-urls`（完全绕过 Security）
- **数据权限**：`@DataPermission` 注解 + AOP 切面动态改写 SQL

## 自定义注解

| 注解 | 作用 |
|------|------|
| `@Log` | 操作审计日志 |
| `@DataPermission` | 行级数据权限控制 |
| `@IgnoreTenant` | 跳过多租户过滤 |
| `@RepeatSubmit` | 防重复提交 |
| `@ValidField` | 自定义字段校验 |

## 开发

```bash
# 运行全部测试
mvn test

# 运行单个测试类
mvn test -Dtest=ClassName

# 运行单个测试方法
mvn test -Dtest=ClassName#methodName
```

## 许可证

[MIT License](./LICENSE) Copyright (c) 2026 HyperSense
