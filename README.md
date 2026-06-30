# AI Task Orchestrator

## 一、项目简介

AI Task Orchestrator 是一个基于 Spring Boot 的 AI 任务编排与异步执行系统，用于模拟企业级 AI Agent / LLM 任务平台中的任务创建、异步调度、状态追踪、失败处理、重试、幂等、取消和超时控制。

当前阶段还没有真正接入 LLM、RAG、Agent，也没有实现模型推理能力。项目当前重点是先构建可靠的任务编排底座，为后续接入真实 AI 能力打好工程基础。

## 二、项目要解决的问题

- 长耗时 AI 任务不能阻塞 HTTP 请求。
- 任务需要有明确的状态追踪。
- 任务失败后需要记录失败原因。
- 临时失败需要支持自动重试。
- MQ 重复投递需要有幂等保护。
- 用户需要能够取消任务。
- 任务执行不能无限卡住，需要超时控制。
- 本地开发环境需要可复现、可快速启动。

## 三、当前已实现能力

- 创建任务：`POST /tasks`
- 查询任务：`GET /tasks/{taskId}`
- 状态机：`PENDING` / `RUNNING` / `RETRY_PENDING` / `SUCCESS` / `FAILED` / `CANCELLED`
- 事件日志：`task_event`
- Flyway 数据库迁移
- RabbitMQ 异步任务投递
- Consumer 模拟任务执行
- 失败处理与 `errorMessage`
- 自动重试：`retryCount` / `maxRetry` / `nextRetryAt`
- Consumer 入口幂等保护
- Scheduler 重复投递保护
- 开发测试重复投递接口
- 取消任务接口：`POST /tasks/{taskId}/cancel`
- `RUNNING` 任务协作式取消
- 超时字段：`timeoutSeconds` / `timeoutAt`
- 超时扫描器
- Docker Compose 本地 MySQL / RabbitMQ 环境

## 四、技术栈

- Java
- Spring Boot
- Spring Web
- Spring Data JPA
- MySQL
- Flyway
- RabbitMQ
- Docker Compose
- Maven Wrapper
- Lombok

## 五、核心流程

```text
用户提交任务
-> task 入库，状态 PENDING
-> 发送 RabbitMQ 消息
-> Consumer 接收消息
-> PENDING / RETRY_PENDING -> RUNNING
-> 模拟执行
-> 成功：RUNNING -> SUCCESS
-> 失败可重试：RUNNING -> RETRY_PENDING
-> Scheduler 到期重新投递
-> 重试耗尽：RUNNING -> FAILED
-> 用户取消：PENDING / RETRY_PENDING / RUNNING -> CANCELLED
-> 超时扫描：RUNNING -> FAILED
```

## 六、状态流转

当前合法主流程：

```text
PENDING -> RUNNING
PENDING -> CANCELLED
RUNNING -> SUCCESS
RUNNING -> RETRY_PENDING
RUNNING -> FAILED
RUNNING -> CANCELLED
RETRY_PENDING -> RUNNING
RETRY_PENDING -> FAILED
RETRY_PENDING -> CANCELLED
```

`SUCCESS` / `FAILED` / `CANCELLED` 是终态。

## 七、本地启动

详细本地启动方式见：

```text
docs/local-dev.md
```

启动 MySQL / RabbitMQ：

```powershell
docker compose up -d
```

PowerShell 启动 Spring Boot：

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=docker"
```

## 八、主要接口

- `POST /tasks`
- `GET /tasks/{taskId}`
- `PATCH /tasks/{taskId}/status`
- `POST /tasks/{taskId}/cancel`
- `POST /dev/tasks/{taskId}/dispatch`

`/dev/tasks/{taskId}/dispatch` 仅用于本地开发测试重复投递，不是生产接口。

## 九、当前版本进度

当前已完成：

- V0.1 创建任务
- V0.2 查询任务
- V0.3 状态机
- V0.4 任务事件表
- V0.4.1 Flyway
- V0.5 RabbitMQ 异步调度
- V0.6 Consumer 模拟执行
- V0.7 失败处理
- V0.8 重试机制
- V0.9 幂等与重复消费控制
- V0.10 取消与超时
- V0.11 本地开发环境

## 十、后续规划

后续可能扩展：

- Spring Boot 应用容器化
- Actuator / Prometheus / Grafana 可观测性
- LLM Client 抽象
- Prompt Template
- Token Usage 成本统计
- Streaming Output
- RAG
- Tool Calling
- Agent Runtime
- Evaluation Harness
- KV Cache-aware Scheduling

以上内容属于后续规划，当前版本尚未实现 LLM / RAG / Agent / KV Cache 等能力。

## 十一、项目定位说明

当前项目重点不是“调大模型 API”，而是先构建 AI 任务平台需要的可靠工程底座：

- 状态管理
- 异步调度
- 失败恢复
- 幂等控制
- 可观测事件
- 本地环境工程化
