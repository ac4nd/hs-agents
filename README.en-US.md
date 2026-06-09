# GodlikeAgents

<p align="center">
  <strong>Enterprise AI Agent Platform with Multi-Tenant RBAC</strong>
</p>

<p align="center">
  <a href="./LICENSE"><img src="https://img.shields.io/badge/License-MIT-blue.svg" alt="License: MIT"></a>
  <img src="https://img.shields.io/badge/JDK-17-green.svg" alt="JDK 17">
  <img src="https://img.shields.io/badge/Spring%20Boot-4.0.5-brightgreen.svg" alt="Spring Boot 4.0.5">
  <img src="https://img.shields.io/badge/PostgreSQL-16-blue.svg" alt="PostgreSQL 16">
</p>

---

## Overview

GodlikeAgents is an enterprise-grade AI Agent platform built on **Java 17 + Spring Boot 4 + Spring Security 6**, integrating multi-tenant permission management with advanced AI Agent capabilities. The system uses **LangGraph4j + LangChain4j** for graph-orchestrated multi-agent collaboration, supporting tool invocation, sandbox execution, long-term memory, and Human-in-the-Loop (HITL) workflows.

## Key Features

### AI Agent Framework

- **Graph Orchestration Engine** — LangGraph4j StateGraph-based plan-execute-route workflow
- **Hierarchical Agent Collaboration** — Master agent delegates to sub-agents with independent contexts, supports recursive nesting
- **Tool System** — Built-in web search (SearXNG), file I/O, code sandbox; extensible via custom tools
- **Skill System** — Dynamic SKILL.md loading from filesystem, runtime injection into agent context
- **Long-Term Memory** — Semantic retrieval via Zhipu Embedding-3 + PostgreSQL pgvector
- **Sandbox Execution** — Four isolation modes: local process, Docker, Podman, remote sandbox
- **Human-in-the-Loop (HITL)** — Interrupt workflow for human approval on critical operations
- **Middleware Pipeline** — Pluggable middleware: logging, message compression, large output offloading
- **SSE Streaming** — Real-time agent thought process and execution results

### System Management

- **Multi-Tenancy** — Row-level database isolation (tenant_id) with ThreadLocal context propagation
- **RBAC** — Four-level permission hierarchy: User-Role-Menu-Department
- **Data Permissions** — Five scope levels (All / Dept & Sub-depts / Dept / Self / Custom)
- **Authentication** — Dual session modes: stateless JWT / stateful Redis; supports password, SMS, and WeChat Mini Program login
- **File Management** — Strategy pattern with MinIO / Aliyun OSS / Local storage backends
- **Code Generation** — Velocity template engine for one-click CRUD scaffolding (frontend + backend)
- **Real-Time Messaging** — SSE-based online notifications and dictionary change broadcasts

## Tech Stack

| Category | Technology | Version |
|-----------|-----------|---------|
| Core Framework | Spring Boot | 4.0.5 |
| Security | Spring Security | 6.x |
| ORM | MyBatis-Plus | 3.5.15 |
| Database | PostgreSQL + pgvector | 16 |
| Cache | Redis + Caffeine | 7.2.3 / 2.9.3 |
| AI Orchestration | LangGraph4j | 1.8.16 |
| LLM Integration | LangChain4j | 1.0.0 |
| Embedding Model | Zhipu Embedding-3 | 0.3.3 |
| Object Mapping | MapStruct | 1.6.3 |
| Distributed Lock | Redisson | 4.1.0 |
| Connection Pool | Druid | 1.2.24 |
| API Documentation | Knife4j + SpringDoc | 4.5.0 |
| Object Storage | MinIO / Aliyun OSS | 8.5.10 |
| Job Scheduling | XXL-Job | 3.2.0 |
| Excel | FastExcel | 1.3.0 |
| Utilities | Hutool | 5.8.41 |
| Container Sandbox | Docker Java | 3.7.1 |

## Project Structure

```
hs-agents/
├── docker/                          # Infrastructure
│   ├── docker-compose.yml           # PostgreSQL, Redis, MinIO, XXL-Job, SearXNG
│   ├── sandbox/                     # Multi-language sandbox image (Python/Node.js/Bash)
│   └── searxng/                     # SearXNG search engine config
├── sql/
│   └── postgresql/                  # Database initialization scripts
├── src/main/java/com/hypersense/boot/
│   ├── agents/                      # Agent business layer (Controller/Service)
│   ├── auth/                        # Authentication (JWT/SMS/WeChat login)
│   ├── codegen/                     # Code generator
│   ├── common/                      # Common components (base classes, annotations, exceptions, utils)
│   ├── file/                        # File management (MinIO/OSS/Local)
│   ├── framework/                   # Infrastructure layer
│   │   ├── agents/                  # ★ AI Agent Core
│   │   │   ├── engine/              # Graph engine (Plan/Execute/Delegate/Tool/Finalize)
│   │   │   ├── tool/                # Built-in tools (search/file/sandbox)
│   │   │   ├── sandbox/             # Sandbox executors (Local/Docker/Podman/Remote)
│   │   │   ├── skill/               # Skill system (dynamic loading/registry)
│   │   │   ├── memory/              # Long-term memory (Embedding/pgvector)
│   │   │   ├── checkpoint/          # Checkpoint persistence (PostgreSQL)
│   │   │   ├── middleware/          # Middleware pipeline
│   │   │   └── config/              # Agent configuration
│   │   ├── security/                # Security config & filter chain
│   │   ├── tenant/                  # Multi-tenant interception
│   │   ├── mybatis/                 # MyBatis enhancements (data permissions/pagination)
│   │   └── web/                     # Web infrastructure (rate limiting/exceptions/CORS)
│   ├── message/                     # SSE real-time messaging
│   └── system/                      # System management (User/Role/Menu/Dept/Tenant/Dict/Log)
└── src/main/resources/
    ├── application.yml              # Main configuration
    ├── application-dev.yml          # Development profile
    ├── application-prod.yml         # Production profile
    ├── codegen.yml                  # Code generator config
    ├── mapper/                      # MyBatis XML mappers
    └── templates/                   # Velocity code generation templates
```

## Quick Start

### Prerequisites

- JDK 17+
- Maven 3.8+
- Docker & Docker Compose (for infrastructure services)

### 1. Start Infrastructure

```bash
docker-compose -f docker/docker-compose.yml -p godlikeagents up -d
```

This starts the following services:

| Service | Port | Description |
|---------|------|-------------|
| PostgreSQL (pgvector) | 5432 | Database, credentials: `postgres/123456` |
| Redis | 6379 | Cache, password: `123456` |
| MinIO | 9000 / 9001 | Object storage, console on 9001 |
| XXL-Job Admin | 8080 | Task scheduling center |
| SearXNG | 8888 | Agent web search engine |

### 2. Configure LLM

Edit `application-dev.yml` to set up your LLM provider:

```yaml
agent:
  llm:
    openai:
      endpoint: https://open.bigmodel.cn/api/coding/paas/v4
      api-key: ${YOUR_API_KEY}
      model-name: glm-4.7
```

Supports any OpenAI-compatible API (OpenAI / DeepSeek / Qwen / Zhipu, etc.).

### 3. Build & Run

```bash
# Build
mvn clean package -DskipTests

# Run
java -jar target/godlikeagents.jar

# Or run with Maven
mvn spring-boot:run
```

After startup:
- API Docs: `http://localhost:8000/doc.html`
- Application port: **8000** (dev) / **8989** (prod)

## Agent Usage

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
    .run("Search today's tech news and generate a summary");
```

### REST API

```bash
# Create session
curl -X POST http://localhost:8000/api/v1/agent/sessions \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{"prompt": "Analyze performance issues in this code", "tools": ["search", "sandbox"]}'

# SSE streaming execution
curl -N http://localhost:8000/api/v1/agent/sessions/{id}/stream \
  -H "Authorization: Bearer {token}"

# HITL approval
curl -X POST http://localhost:8000/api/v1/agent/sessions/{id}/approve \
  -H "Authorization: Bearer {token}" \
  -d '{"approved": true}'
```

## Agent Architecture

```
User Request
   │
   ▼
┌──────────┐     ┌──────────────┐     ┌──────────────┐
│ PlanNode │────▶│ ExecuteNode  │────▶│ DelegateNode │ (Sub-agent delegation)
└──────────┘     └──────┬───────┘     └──────────────┘
                        │
                   ┌────▼────┐
                   │ ToolNode │ (Tool invocation + retry)
                   └────┬────┘
                        │
                 ┌──────▼──────┐
                 │ FinalizeNode │ (Result synthesis)
                 └──────────────┘
                        │
                        ▼
                  User Response / SSE Stream
```

- **PlanNode** — Analyzes the task and breaks it into a TODO list
- **ExecuteNode** — Executes TODOs step by step, coordinates tools and sub-agents
- **DelegateNode** — Creates independent sub-agent contexts, supports recursive delegation
- **ToolNode** — Executes tool calls with exponential backoff retry
- **FinalizeNode** — Synthesizes all execution results into the final response
- **Route Nodes** — Conditional routing: completed → terminate, pending → continue execution

## Security Architecture

```
Request → RateLimiterFilter → CaptchaFilter → TokenAuthenticationFilter → Business Logic
```

- **Dual Session Mode**: JWT (stateless) or Redis Token, switchable via `security.session.type`
- **Authentication Providers**: Password / SMS verification
- **Whitelist**: `security.ignore-urls` (skip auth filter) / `security.unsecured-urls` (bypass Security entirely)
- **Data Permissions**: `@DataPermission` annotation + AOP dynamic SQL rewriting

## Custom Annotations

| Annotation | Purpose |
|-----------|---------|
| `@Log` | Operation audit logging |
| `@DataPermission` | Row-level data scope control |
| `@IgnoreTenant` | Skip multi-tenant filtering |
| `@RepeatSubmit` | Duplicate submission prevention |
| `@ValidField` | Custom field validation |

## Development

```bash
# Run all tests
mvn test

# Run a single test class
mvn test -Dtest=ClassName

# Run a single test method
mvn test -Dtest=ClassName#methodName
```

## License

[MIT License](./LICENSE) Copyright (c) 2026 HyperSense
