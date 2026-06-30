# AI Task Orchestrator

## 一、项目简介

AI Task Orchestrator 是一个基于 Spring Boot 的 AI 任务编排与异步执行系统，用于模拟企业级 AI Agent / LLM 任务平台中的任务创建、异步调度、状态追踪、失败处理、重试、幂等、取消、超时控制、Prompt Template 渲染和 Mock LLM 执行流程。

当前系统已经实现 Prompt Template 执行闭环：任务执行时使用 `default_task_prompt` 渲染最终 Prompt，`LlmRequest.prompt` 使用渲染后的 `renderedPrompt`，并保存 `renderedPrompt` / `promptTemplateCode` / `resultContent` / `llmModel`。当前仍然只使用 `MockLlmClient`，尚未接入真实 OpenAI / Claude / 本地模型 Provider。

## 二、项目要解决的问题

- 长耗时 AI 任务不能阻塞 HTTP 请求。
- 任务需要有明确的状态追踪。
- 任务失败后需要记录失败原因。
- 临时失败需要支持自动重试。
- MQ 重复投递需要有幂等保护。
- 用户需要能够取消任务。
- 任务执行不能无限卡住，需要超时控制。
- Prompt Template 需要把用户输入渲染成最终 LLM Prompt。
- LLM 执行结果、实际 Prompt 和模板编码需要能追踪和查询。
- 本地开发环境需要可复现、可快速启动。

## 三、当前已实现能力

- 创建任务：`POST /tasks`
- 查询任务：`GET /tasks/{taskId}`
- 状态机：`PENDING` / `RUNNING` / `RETRY_PENDING` / `SUCCESS` / `FAILED` / `CANCELLED`
- 事件日志：`task_event`
- Flyway 数据库迁移
- RabbitMQ 异步任务投递
- Consumer 接收任务消息
- 失败处理与 `errorMessage`
- 自动重试：`retryCount` / `maxRetry` / `nextRetryAt`
- Consumer 入口幂等保护
- Scheduler 重复投递保护
- 取消任务与协作式取消
- 超时字段与超时扫描器
- `LlmClient` 抽象
- `MockLlmClient`
- Prompt Template 数据模型
- `PromptTemplateRenderer`
- `TaskExecutionService` 使用 `default_task_prompt` 渲染最终 Prompt
- `LlmRequest.prompt` 使用 `renderedPrompt`
- 保存 `resultContent` / `llmModel`
- 保存 `renderedPrompt` / `promptTemplateCode`
- Docker Compose 本地 MySQL / RabbitMQ 环境

## 四、技术栈

- Java
- Spring Boot
- Spring Web
- Spring Data JPA
- MySQL
- Flyway
- RabbitMQ
- Mock LLM Client
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
-> 查询 default_task_prompt
-> PromptTemplateRenderer 渲染 renderedPrompt
-> TaskExecutionService 构造 LlmRequest
-> LlmRequest.prompt = renderedPrompt
-> 调用 LlmClient.generate
-> MockLlmClient 返回 LlmResponse
-> 成功：保存 result_content / llm_model / rendered_prompt / prompt_template_code
-> RUNNING -> SUCCESS
-> 失败可重试：RUNNING -> RETRY_PENDING
-> Scheduler 到期重新投递
-> 重试耗尽：RUNNING -> FAILED
-> 用户取消：PENDING / RETRY_PENDING / RUNNING -> CANCELLED
-> 超时扫描：RUNNING -> FAILED
```

## 六、状态流转

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

```powershell
docker compose up -d
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
- V0.11 本地开发环境与文档
- V1.0 LLM Client 抽象、MockLlmClient、任务执行链路接入和结果保存
- V1.1 Prompt Template 数据模型、渲染器、执行接入和 renderedPrompt 保存

## 十、后续规划

- Prompt Template CRUD API
- 真实 OpenAI / Claude / 本地模型 Provider
- API Key 配置与安全管理
- Token Usage 成本统计
- Streaming Output
- RAG
- Tool Calling
- Agent Runtime
- Evaluation Harness
- KV Cache-aware Scheduling
- Actuator / Prometheus / Grafana 可观测性

当前尚未接入真实大模型 API，尚未实现 Prompt Template CRUD，也尚未实现 Token Usage / Streaming / RAG / Agent / KV Cache。

## 十一、项目定位说明

当前项目重点不是直接“调大模型 API”，而是构建 AI 任务平台需要的可靠工程底座：

- 状态管理
- 异步调度
- 失败恢复
- 幂等控制
- 可观测事件
- Prompt Template 渲染
- Mock LLM 执行链路
- LLM 结果和实际 Prompt 追踪
- 本地环境工程化
