# AI Task Orchestrator

## 一、项目简介

AI Task Orchestrator 是一个基于 Spring Boot 的 AI 任务编排与异步执行系统，用于模拟企业级 AI Agent / LLM 任务平台中的任务创建、异步调度、状态追踪、失败处理、重试、幂等、取消、超时控制、Prompt Template、Mock LLM 执行、模型路由、增量输出和文档上传切分。

当前项目重点不是直接调用真实大模型 API，而是先构建 AI 任务平台需要的可靠工程底座。当前仍然只使用 `MockLlmClient`，尚未接入真实 OpenAI / Claude / 本地模型 Provider。

## 二、项目解决的问题

- 长耗时 AI 任务不能阻塞 HTTP 请求。
- 任务需要完整状态追踪。
- 任务失败需要记录原因。
- 临时失败需要自动重试。
- MQ 重复投递需要幂等保护。
- 用户需要取消任务。
- 任务执行不能无限卡住，需要超时控制。
- LLM 调用需要抽象，方便后续替换真实 Provider。
- Prompt 需要模板化和可追踪。
- 模型选择需要和任务创建解耦。
- 输出结果需要完整保存，也需要支持持久化增量输出。
- RAG 前置能力需要文档上传和文本切分。
- 本地开发环境需要可复现。

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
- RUNNING 任务协作式取消
- 超时字段：`timeoutSeconds` / `timeoutAt`
- 超时扫描器
- Docker Compose 本地 MySQL / RabbitMQ
- `LlmClient` 抽象
- `MockLlmClient`
- 保存 LLM 执行结果：`resultContent` / `llmModel`
- Prompt Template 数据模型
- `PromptTemplateRenderer`
- 保存 `renderedPrompt` / `promptTemplateCode`
- LLM usage metadata：provider、token usage、latency
- Model Router：`requestedModel` -> selected `llmModel`
- 持久化 output chunks：`task_output_chunk`
- 查询增量输出：`GET /tasks/{taskId}/output-chunks`
- 文档上传：`POST /documents`
- 文档详情查询：`GET /documents/{documentId}`
- 文档 chunks 查询：`GET /documents/{documentId}/chunks`
- 支持 `.txt` / `.md` 文档切分

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
- Mock LLM Client

## 五、核心流程

```text
用户提交任务
-> task 入库，状态 PENDING
-> 保存 prompt / requestedModel
-> 发送 RabbitMQ 消息
-> Consumer 接收消息
-> tryStartTaskExecution 做入口幂等保护
-> PENDING / RETRY_PENDING -> RUNNING
-> ModelRouter 选择实际模型
-> 查询 default_task_prompt
-> PromptTemplateRenderer 渲染 renderedPrompt
-> 构造 LlmRequest
-> 调用 LlmClient.generate
-> MockLlmClient 返回 LlmResponse
-> 保存 LLM metadata
-> 成功时保存 output chunks
-> 成功时保存 resultContent / llmModel / renderedPrompt / promptTemplateCode
-> RUNNING -> SUCCESS
```

失败和重试流程：

```text
RUNNING -> RETRY_PENDING
-> RetryScheduler 到期重新投递 MQ
-> RETRY_PENDING -> RUNNING
-> 重试耗尽后 RUNNING -> FAILED
```

取消和超时流程：

```text
PENDING / RETRY_PENDING / RUNNING -> CANCELLED
RUNNING 超时 -> FAILED
```

文档处理流程：

```text
上传 .txt / .md
-> 保存 document 元信息
-> UTF-8 读取文本
-> 固定长度切分
-> 保存 document_chunk
-> document 状态变为 CHUNKED
```

## 六、状态流转

当前主要合法流转：

- `PENDING -> RUNNING`
- `PENDING -> CANCELLED`
- `RUNNING -> SUCCESS`
- `RUNNING -> RETRY_PENDING`
- `RUNNING -> FAILED`
- `RUNNING -> CANCELLED`
- `RETRY_PENDING -> RUNNING`
- `RETRY_PENDING -> FAILED`
- `RETRY_PENDING -> CANCELLED`

`SUCCESS` / `FAILED` / `CANCELLED` 是终态。

## 七、本地启动入口

详细本地启动方式见：

[docs/local-dev.md](docs/local-dev.md)

常用命令：

```powershell
docker compose up -d
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=docker"
```

## 八、主要接口

任务接口：

- `POST /tasks`
- `GET /tasks/{taskId}`
- `PATCH /tasks/{taskId}/status`
- `POST /tasks/{taskId}/cancel`
- `GET /tasks/{taskId}/output-chunks`

开发验收接口：

- `POST /dev/tasks/{taskId}/dispatch`

文档接口：

- `POST /documents`
- `GET /documents/{documentId}`
- `GET /documents/{documentId}/chunks`

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
- V0.11 本地开发环境与项目文档
- V1.0 LLM Client 抽象、MockLlmClient、结果保存
- V1.1 Prompt Template 执行闭环
- V1.2 LLM Metadata & Token Usage
- V1.3 Model Router
- V1.4 持久化增量输出
- V2.1 Document Upload & Chunking

## 十、后续规划

- Prompt Template CRUD API
- 真实 OpenAI / Claude / 本地模型 Provider
- API Key 配置与安全管理
- 真实 tokenizer
- 真实成本统计
- 基于成本、延迟、负载、上下文长度的真实 Model Router
- SSE / WebSocket 实时输出
- PDF / Word 解析
- OCR
- Embedding
- Vector DB
- Semantic Search
- RAG Answer
- Citation
- Tool Calling
- Agent Runtime
- Evaluation Harness
- KV Cache-aware Scheduling
- Actuator / Prometheus / Grafana 可观测性

## 十一、项目边界说明

当前项目已经实现的是 AI 任务平台的可靠任务编排底座，以及 RAG 前置的文档上传切分能力。

当前尚未实现：

- 真实 LLM Provider
- 真实 streaming provider
- SSE / WebSocket
- PDF / Word 解析
- Embedding
- 向量数据库
- 语义检索
- RAG 问答
- Tool Calling
- Agent Runtime
- KV Cache-aware Scheduling

