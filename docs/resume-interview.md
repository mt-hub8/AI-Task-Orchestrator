# 简历与面试表达文档

## 一、文档目的

本文档用于整理 AI Task Orchestrator 当前阶段的简历表达、面试表达、技术亮点、当前边界和后续扩展方向。

## 二、项目一句话描述

AI Task Orchestrator 是一个基于 Spring Boot、RabbitMQ、MySQL 和 Flyway 构建的异步 AI 任务编排系统，当前阶段重点实现任务生命周期管理、异步调度、状态机、事件追踪、失败处理、重试、幂等、取消、超时、Mock LLM、Prompt Template、Model Router、LLM usage metadata、持久化增量输出和文档上传切分，为后续接入真实 LLM / RAG / Agent Runtime 打基础。

## 三、简历版本描述

一句话版本：

基于 Spring Boot、RabbitMQ、MySQL 和 Flyway 实现异步 AI 任务编排系统，支持任务状态机、失败重试、幂等、取消、超时、Mock LLM、Prompt Template、Model Router 和文档 chunking。

三行版本：

AI Task Orchestrator 是一个模拟 AI Agent / LLM 平台长耗时任务执行的后端系统。
项目使用 RabbitMQ 解耦任务创建与执行，使用状态机、事件表、重试、幂等、取消和超时保证任务生命周期可靠。
当前已扩展 Mock LLM、Prompt Template、Model Router、LLM usage、output chunks 和文档上传切分，为后续 RAG 和真实模型接入打基础。

完整项目经历版本：

项目背景：真实 AI 任务通常具有长耗时、易失败、需要重试、需要状态追踪、需要取消和超时控制等特点，不能简单同步阻塞 HTTP 请求。

技术栈：Java、Spring Boot、Spring Web、Spring Data JPA、MySQL、Flyway、RabbitMQ、Docker Compose、Lombok。

本人工作：

- 设计任务状态机与任务事件表。
- 实现 RabbitMQ 异步调度和 Consumer 执行。
- 实现失败处理、重试、幂等、取消、超时。
- 设计 LLM Client 抽象和 Mock Provider。
- 接入 Prompt Template、Model Router、LLM usage metadata。
- 实现持久化 output chunks。
- 实现 `.txt/.md` 文档上传与 chunking。
- 编写本地开发和验收文档。

核心成果：完成一个可本地运行、可验收、可扩展的 AI 任务编排底座。

工程亮点：状态机约束、事件追踪、异步调度、失败恢复、幂等控制、数据库迁移、Mock LLM 解耦、文档处理前置能力。

## 四、简历 bullet 示例

- 基于 Spring Boot 和 RabbitMQ 实现异步任务调度，将任务创建与后台执行解耦。
- 设计任务状态机和 `task_event`，记录任务生命周期并防止非法状态流转。
- 实现失败处理、自动重试、Consumer 入口幂等、协作式取消和超时扫描。
- 设计 `LlmClient` 抽象与 `MockLlmClient`，实现任务执行链路与模型调用解耦。
- 设计 Prompt Template 数据模型与渲染器，保存实际使用的 `renderedPrompt`。
- 设计 `ModelRouter`，支持用户请求模型与实际执行模型分离。
- 记录 LLM provider、token usage 和 latency，为后续成本统计打基础。
- 实现持久化 output chunks，为后续 polling / SSE / WebSocket 输出打基础。
- 实现 `.txt/.md` 文档上传与 chunking，为后续 Embedding、向量检索和 RAG 问答打基础。
- 使用 Flyway 管理数据库结构演进，使用 Docker Compose 搭建本地 MySQL / RabbitMQ 环境。

## 五、面试开场怎么讲

这个项目模拟的是 AI Agent / LLM 平台里的长耗时任务执行系统。它用 Spring Boot 提供 API，用 RabbitMQ 解耦任务创建和执行，用 MySQL 保存任务、事件、Prompt 模板、执行结果、输出片段和文档 chunks，用 Flyway 管理数据库迁移。当前已经完成可靠任务系统底座，并用 Mock LLM 模拟模型调用链路，同时支持 Prompt Template、Model Router、LLM usage metadata、持久化 output chunks 和文档上传切分。它还没有接入真实模型和向量数据库，但已经具备后续接入真实 Provider、Embedding 和 RAG 的工程基础。

## 六、系统架构怎么讲

```text
用户请求
-> TaskController / DocumentController
-> Service 层
-> MySQL
-> RabbitMQ Producer
-> RabbitMQ Queue
-> Consumer
-> TaskExecutionService
-> ModelRouter
-> PromptTemplateRenderer
-> LlmClient / MockLlmClient
-> 状态流转
-> RetryScheduler / TimeoutScheduler
```

文档处理链路：

```text
DocumentController
-> DocumentService
-> document
-> document_chunk
```

## 七、为什么用 RabbitMQ

- HTTP 请求不应该阻塞长耗时任务。
- MQ 解耦任务创建和任务执行。
- 支持异步消费。
- 支持后续扩展多 Worker。
- 为后续 LLM / RAG / Agent 长任务执行打基础。

## 八、为什么做状态机

- 防止非法状态流转。
- 明确任务生命周期。
- 让失败、重试、取消、超时都有统一约束。
- `SUCCESS` / `FAILED` / `CANCELLED` 是终态。
- 防止重复消息导致 `SUCCESS -> RUNNING` 等脏状态。

## 九、为什么做 task_event

- `task` 表保存当前状态。
- `task_event` 表保存历史过程。
- 便于审计、排错、复盘。
- 后续可以扩展成 Agent Trace / Execution Trace。

## 十、重试机制怎么讲

重试机制由 `retryCount`、`maxRetry`、`nextRetryAt`、`RETRY_PENDING` 和 `RetryScheduler` 组成。任务失败后，如果还可以重试，就进入 `RETRY_PENDING`，设置下一次重试时间。Scheduler 到期后重新投递 MQ，由 Consumer 再次执行。重试耗尽后进入 `FAILED`。

当前是本地单实例轻量实现，生产级还需要分布式锁、Outbox、DLQ、ack/nack 深度治理等增强。

## 十一、幂等怎么讲

- Consumer 收到消息后先调用 `tryStartTaskExecution`。
- 只有 `PENDING / RETRY_PENDING` 可以进入 `RUNNING`。
- `RUNNING / SUCCESS / FAILED / CANCELLED` 收到重复消息会被忽略。
- `DevTaskDispatchController` 用于本地模拟重复投递。
- Scheduler 投递后推迟 `nextRetryAt`，降低短时间重复投递。

当前边界：

- 还没有数据库乐观锁 version。
- 还没有 Redis 分布式锁。
- 还没有 Outbox。
- 还没有 RabbitMQ ack/nack 深度治理。

## 十二、取消和超时怎么讲

- `PENDING / RETRY_PENDING` 可以直接取消。
- `RUNNING` 使用协作式取消。
- 执行过程中定期检查任务是否 `CANCELLED`。
- `timeoutSeconds / timeoutAt` 表示任务超时时间。
- `TimeoutScheduler` 扫描 `RUNNING` 且 `timeoutAt` 到期的任务。
- 超时后标记 `FAILED`。
- `TaskExecutionService` 避免覆盖已经 `CANCELLED / FAILED` 的终态。

## 十三、LLM Client / MockLlmClient 怎么讲

`LlmClient` 是统一模型调用接口，`MockLlmClient` 是当前的模拟实现，不调用外部 API。TaskExecutionService 只依赖 `LlmClient`，因此后续可以替换为真实 OpenAI / Claude / 本地模型 Provider。

MockLlmClient 当前支持：

- 成功返回 content。
- prompt 包含 `fail` 或 `失败` 时返回失败。
- 返回 provider、model、token usage、latency。

## 十四、Prompt Template 怎么讲

Prompt Template 用来把用户输入和系统模板合成最终 LLM Prompt。

当前实现：

- `prompt_template` 表保存模板。
- 默认模板为 `default_task_prompt`。
- `PromptTemplateRenderer` 支持 `{{prompt}}`、`{{taskId}}`、`{{model}}`。
- TaskExecutionService 使用 renderedPrompt 构造 LlmRequest。
- task 表保存 `renderedPrompt` 和 `promptTemplateCode`。

## 十五、Model Router 怎么讲

创建任务时用户可以传入 `model`，系统保存为 `requestedModel`。执行阶段由 `ModelRouter` 选择实际执行模型，支持：

- `mock-llm`
- `mock-fast`
- `mock-smart`

未知模型 fallback 到 `mock-llm`。最终任务详情中可以同时看到 `requestedModel` 和实际执行的 `llmModel`。

当前还没有真实成本、延迟、负载、上下文长度路由。

## 十六、LLM usage metadata 怎么讲

MockLlmClient 返回：

- `llmProvider`
- `promptTokenCount`
- `completionTokenCount`
- `totalTokenCount`
- `llmLatencyMs`

这些字段保存到 task 表，用于后续真实 token usage 和成本统计打基础。

当前 token usage 是 Mock 估算，不是真实 tokenizer。

## 十七、Output chunks 怎么讲

当前项目实现的是持久化增量输出，不是 SSE / WebSocket。

流程：

```text
Mock LLM 返回完整 content
-> TaskExecutionService 拆分 content
-> 保存 task_output_chunk
-> GET /tasks/{taskId}/output-chunks 查询 chunks
-> resultContent 仍保存完整输出
```

这为后续 polling、SSE、WebSocket 实时输出打基础。

## 十八、Document Upload & Chunking 怎么讲

当前项目新增了 RAG 前置文档处理能力：

- 支持上传 `.txt / .md`。
- 保存文档元信息到 `document` 表。
- UTF-8 读取文本。
- 按固定长度切分。
- 保存到 `document_chunk` 表。
- 提供文档详情和 chunks 查询接口。

当前不做 PDF / Word / OCR，不做 Embedding、Vector DB、Semantic Search 或 RAG Answer。

## 十九、当前项目边界

当前已实现的是 AI 任务平台的可靠任务编排底座，以及 RAG 前置文档处理能力。

当前尚未实现：

- 真实 OpenAI / Claude / 本地模型 Provider
- API Key 配置
- 真实 tokenizer
- 真实成本统计
- SSE / WebSocket
- PDF / Word 解析
- OCR
- Embedding
- Vector DB
- Semantic Search
- RAG Answer
- Citation
- Tool Calling
- Agent Runtime
- KV Cache-aware Scheduling
- 多租户
- 权限系统
- 生产级监控
- 分布式锁
- Outbox Pattern
- Dead Letter Queue

## 二十、如何诚实回答“这是 AI 项目吗？”

这个项目当前还不是完整的 LLM 应用或 Agent 平台，也没有接入真实 OpenAI / Claude / 本地模型。它先实现 AI 任务平台底层最重要的任务编排能力，并用 MockLlmClient 模拟模型调用。因为真实 AI 任务通常具有长耗时、易失败、需要重试、需要状态追踪、需要取消和超时控制等特点，所以我先从可靠任务系统做起。后续接入 LLM、Embedding、RAG、Tool Calling、Agent Runtime 时，可以复用这套异步调度、状态管理、Prompt 追踪、输出追踪和文档处理底座。

## 二十一、后续扩展路线

下一阶段可以扩展：

- Prompt Template CRUD API
- 真实模型 Provider
- API Key 管理
- 真实 tokenizer
- 成本统计
- SSE / WebSocket
- PDF / Word 解析
- Embedding
- Vector DB
- Semantic Search
- RAG Answer
- Citation
- Tool Calling
- Agent Runtime
- Evaluation Harness
- KV Cache-aware Scheduling

## 二十二、面试可能被问的问题

1. 为什么不用线程直接执行？
   HTTP 请求不应该阻塞长耗时任务，MQ 可以解耦创建和执行。

2. 为什么要引入 RabbitMQ？
   支持异步消费、削峰、后续多 Worker 扩展。

3. 状态机解决了什么问题？
   防止非法流转，让生命周期可控。

4. task_event 有什么价值？
   保存历史过程，便于审计和排错。

5. 重试机制如何避免无限重试？
   使用 `retryCount` 和 `maxRetry` 控制上限。

6. 幂等怎么保证？
   Consumer 入口只允许 `PENDING / RETRY_PENDING` 进入 `RUNNING`。

7. 取消 RUNNING 任务为什么不能直接 kill 线程？
   直接中断风险高，当前使用协作式取消更可控。

8. 超时后如何避免执行线程覆盖状态？
   写成功或失败前再次检查任务是否仍是 `RUNNING`。

9. Model Router 当前有什么边界？
   只支持 Mock 模型 fallback，还没有真实多 Provider 策略。

10. Document chunking 和 RAG 有什么关系？
    chunking 是 RAG 的前置步骤，后续还需要 Embedding、向量库和检索。

11. 如果要生产化，还缺什么？
    权限、多租户、监控、分布式锁、Outbox、DLQ、真实 Provider、安全配置等。

## 二十三、相关文档

- [README.md](../README.md)
- [docs/local-dev.md](local-dev.md)
- [docs/project-structure.md](project-structure.md)
- [docs/api-examples.md](api-examples.md)

