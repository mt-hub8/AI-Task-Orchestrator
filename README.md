# AI Task Orchestrator

## V1.4 Streaming Output 补充

当前已实现持久化增量输出闭环：新增 `task_output_chunk` 表保存任务输出片段；`TaskExecutionService` 在 Mock LLM 成功返回后把完整 content 按固定长度拆成多个 chunk 保存；`GET /tasks/{taskId}/output-chunks` 可以按 `chunkIndex` 升序查询输出片段；最终 `resultContent` 仍然保存完整输出。

当前版本不是 SSE / WebSocket，也没有接入真实 OpenAI / Claude streaming。它是异步任务系统下的“持久化增量输出 + 查询接口”，后续可以基于 `task_output_chunk` 扩展 polling、SSE 或 WebSocket。

## V1.3 Model Router 补充

当前已实现 Model Router 闭环：创建任务时可以传入 `model`，系统保存为 `requestedModel`；执行阶段由 `ModelRouter` 选择实际模型，并把路由后的模型用于 Prompt Template 变量和 `LlmRequest.model`。当前支持 `mock-llm`、`mock-fast`、`mock-smart`，未知模型会 fallback 到 `mock-llm`。`GET /tasks/{taskId}` 可以同时看到用户请求的 `requestedModel` 和实际执行的 `llmModel`。

当前 Model Router 仍然只路由到 `MockLlmClient`，尚未接入真实 OpenAI / Claude / 本地模型 Provider，也尚未实现基于成本、延迟、负载、上下文长度的真实路由和 KV Cache-aware Scheduling。

## 一、项目简介

AI Task Orchestrator 是一个基于 Spring Boot 的 AI 任务编排与异步执行系统，用于模拟企业级 AI Agent / LLM 任务平台中的任务创建、异步调度、状态追踪、失败处理、重试、幂等、取消、超时控制、Prompt Template 渲染、Mock LLM 执行和 LLM usage 记录。

当前系统仍然只使用 `MockLlmClient`，尚未接入真实 OpenAI / Claude / 本地模型 Provider。当前 token usage 是基于字符串长度的 Mock 估算，不是真实 tokenizer 统计，也不是实际成本计费。

## 二、当前已实现能力

- 创建任务：`POST /tasks`
- 查询任务：`GET /tasks/{taskId}`
- 状态机：`PENDING` / `RUNNING` / `RETRY_PENDING` / `SUCCESS` / `FAILED` / `CANCELLED`
- RabbitMQ 异步调度与 Consumer 执行
- `task_event` 事件日志
- 失败处理、重试、幂等、取消和超时
- `LlmClient` 抽象
- `MockLlmClient`
- Prompt Template 数据模型
- `PromptTemplateRenderer`
- 使用 `default_task_prompt` 渲染 `renderedPrompt`
- `LlmRequest.prompt` 使用 `renderedPrompt`
- 保存 `resultContent` / `llmModel`
- 保存 `renderedPrompt` / `promptTemplateCode`
- 保存 LLM metadata：`llmProvider`、`promptTokenCount`、`completionTokenCount`、`totalTokenCount`、`llmLatencyMs`
- Docker Compose 本地 MySQL / RabbitMQ 环境

## 三、核心流程

```text
用户提交任务
-> task 入库，状态 PENDING
-> 发送 RabbitMQ 消息
-> Consumer 接收消息
-> PENDING / RETRY_PENDING -> RUNNING
-> 查询 default_task_prompt
-> PromptTemplateRenderer 渲染 renderedPrompt
-> 构造 LlmRequest，prompt = renderedPrompt
-> 调用 LlmClient.generate
-> MockLlmClient 返回 LlmResponse
-> 保存 provider / token usage / latency
-> 成功：保存 resultContent / llmModel / renderedPrompt / promptTemplateCode
-> RUNNING -> SUCCESS
-> 失败可重试：RUNNING -> RETRY_PENDING
-> 重试耗尽：RUNNING -> FAILED
```

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

## 五、主要接口

- `POST /tasks`
- `GET /tasks/{taskId}`
- `PATCH /tasks/{taskId}/status`
- `POST /tasks/{taskId}/cancel`
- `POST /dev/tasks/{taskId}/dispatch`

`/dev/tasks/{taskId}/dispatch` 仅用于本地开发测试重复投递，不是生产接口。

## 六、本地启动

详细本地启动方式见：

```text
docs/local-dev.md
```

```powershell
docker compose up -d
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=docker"
```

## 七、当前版本进度

- V0.1-V0.11：可靠异步任务系统与本地开发环境
- V1.0：LLM Client 抽象、MockLlmClient、执行链路接入、结果保存
- V1.1：Prompt Template 数据模型、渲染器、执行接入、renderedPrompt 保存
- V1.2：LLM metadata 与 Mock token usage 记录闭环

## 八、后续规划

- Prompt Template CRUD API
- 真实 OpenAI / Claude / 本地模型 Provider
- API Key 配置与安全管理
- 真实 tokenizer
- 真实成本统计
- Streaming Output
- RAG
- Tool Calling
- Agent Runtime
- KV Cache-aware Scheduling
- Actuator / Prometheus / Grafana 可观测性

## 九、项目定位说明

当前项目重点不是直接“调大模型 API”，而是构建 AI 任务平台需要的可靠工程底座：状态管理、异步调度、失败恢复、幂等控制、事件追踪、Prompt 渲染、Mock LLM 执行、usage 记录和本地环境工程化。
