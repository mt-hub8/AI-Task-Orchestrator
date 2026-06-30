# 项目结构说明

## 一、文档目的

本文档用于帮助开发者理解 AI Task Orchestrator 的项目结构、核心包职责、核心类职责，以及一次任务从 HTTP 创建到异步执行、Prompt Template 渲染、Mock LLM 调用、重试、取消和超时处理的完整链路。

## 二、整体目录结构

```text
src/main/java/com/tuoman/ai_task_orchestrator
├── config
├── controller
├── dto
├── entity
├── enums
├── llm
├── mq
├── prompt
├── repository
├── scheduler
├── service
├── state
└── AiTaskOrchestratorApplication.java

src/main/resources
├── application.properties
├── application-docker.properties
└── db/migration

docs
├── api-examples.md
├── local-dev.md
├── project-structure.md
└── resume-interview.md
```

## 三、controller 包

Controller 层负责接收 HTTP 请求。

`TaskController` 包含：

- `POST /tasks`
- `GET /tasks/{taskId}`
- `PATCH /tasks/{taskId}/status`
- `POST /tasks/{taskId}/cancel`

`DevTaskDispatchController` 包含：

- `POST /dev/tasks/{taskId}/dispatch`

该接口仅用于本地开发测试重复投递，不是生产接口。

## 四、dto 包

DTO 用于接口入参和出参，隔离 Entity 和 HTTP API。

- `CreateTaskRequest`
- `CreateTaskResponse`
- `TaskDetailResponse`：返回状态、错误、重试、超时、LLM 结果、`renderedPrompt` 和 `promptTemplateCode`。
- `UpdateTaskStatusRequest`
- `DevTaskDispatchResponse`

## 五、entity 包

`TaskEntity` 对应 `task` 表，保存：

- 原始用户输入 `prompt`
- 当前状态
- 错误信息
- 重试字段
- 超时字段
- LLM 结果字段：`resultContent`、`llmModel`
- Prompt 渲染字段：`renderedPrompt`、`promptTemplateCode`

`PromptTemplateEntity` 对应 `prompt_template` 表，保存：

- `templateCode`
- `templateName`
- `templateContent`
- `enabled`
- 创建和更新时间

`TaskEventEntity` 对应 `task_event` 表，保存任务历史事件和状态变化。

## 六、enums 包

`TaskStatus`：

- `PENDING`
- `RUNNING`
- `RETRY_PENDING`
- `SUCCESS`
- `FAILED`
- `CANCELLED`

`TaskEventType`：

- `TASK_CREATED`
- `STATUS_CHANGED`

## 七、state 包

`TaskStateMachine` 负责限制任务状态合法流转，防止 `SUCCESS -> RUNNING`、`FAILED -> RUNNING` 等非法状态。

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

## 八、repository 包

- `TaskRepository`：查询任务、到期重试任务、超时 RUNNING 任务。
- `TaskEventRepository`：保存任务事件。
- `PromptTemplateRepository`：根据 `templateCode` 查询启用中的 Prompt Template。

## 九、mq 包

MQ 层负责 RabbitMQ 消息投递和消费。

- `RabbitMQConfig`：定义 exchange、queue、binding、message converter。
- `TaskDispatchMessage`：MQ 消息体。
- `TaskDispatchProducer`：发送任务调度消息。
- `TaskDispatchConsumer`：接收任务调度消息并调用 `TaskExecutionService`。

## 十、prompt 包

`prompt` 包负责 Prompt Template 渲染。

- `PromptTemplateRenderer`：将模板中的 `{{prompt}}`、`{{ taskId }}`、`{{model}}` 等变量替换成实际值。

当前已接入任务执行链路，但尚未提供 Prompt Template CRUD API。

## 十一、llm 包

`llm` 包负责 LLM 调用抽象和模拟实现。

- `LlmClient`：统一 LLM 调用接口。
- `LlmRequest`：包含 `taskId`、`prompt`、`model`。其中 `prompt` 当前使用 `renderedPrompt`。
- `LlmResponse`：包含 `taskId`、`model`、`content`、`success`、`errorMessage`。
- `MockLlmClient`：模拟 LLM Provider，不调用外部 API。

## 十二、service 包

`TaskService` 负责：

- 创建任务
- 查询任务
- 状态流转
- 记录 `task_event`
- 标记失败
- 标记重试等待
- 标记成功并保存 `resultContent` / `llmModel` / `renderedPrompt` / `promptTemplateCode`
- 取消任务
- 标记超时

`TaskExecutionService` 负责：

- 入口幂等保护
- 协作式取消检查
- 查询 `default_task_prompt`
- 使用 `PromptTemplateRenderer` 渲染 `renderedPrompt`
- 构造 `LlmRequest`
- 调用 `LlmClient.generate(...)`
- 成功后保存结果和渲染信息
- 失败后进入重试或最终失败
- 避免覆盖已取消或已超时任务

## 十三、scheduler 包

- `TaskRetryScheduler`：扫描 `RETRY_PENDING` 且 `nextRetryAt` 到期的任务，重新投递 MQ。
- `TaskTimeoutScheduler`：扫描 `RUNNING` 且 `timeoutAt` 到期的任务，标记为 `FAILED`。

## 十四、resources/db/migration

当前迁移脚本包括：

- `V1__create_task_table.sql`
- `V2__create_task_event_table.sql`
- `V3__add_error_message_to_task.sql`
- `V4__add_retry_fields_to_task.sql`
- `V5__add_timeout_fields_to_task.sql`
- `V6__add_llm_result_fields_to_task.sql`
- `V7__create_prompt_template_table.sql`
- `V8__add_prompt_render_fields_to_task.sql`

## 十五、一次任务完整执行链路

```text
1. 用户调用 POST /tasks
2. TaskController 接收请求
3. TaskService 创建 task，状态 PENDING
4. TaskService 写入 TASK_CREATED 事件
5. TaskDispatchProducer 发送 RabbitMQ 消息
6. TaskDispatchConsumer 收到消息
7. TaskExecutionService 调用 tryStartTaskExecution
8. PENDING / RETRY_PENDING -> RUNNING
9. 查询 default_task_prompt
10. PromptTemplateRenderer 渲染 renderedPrompt
11. 构造 LlmRequest，prompt = renderedPrompt
12. 调用 LlmClient.generate
13. MockLlmClient 返回 LlmResponse
14. 成功：保存 resultContent / llmModel / renderedPrompt / promptTemplateCode
15. RUNNING -> SUCCESS
16. 失败可重试：RUNNING -> RETRY_PENDING
17. RetryScheduler 到期重新投递
18. 重试耗尽：RUNNING -> FAILED
19. 用户取消：进入 CANCELLED
20. TimeoutScheduler 超时：RUNNING -> FAILED
```

## 十六、当前架构边界

当前已实现：

- 可靠任务生命周期
- RabbitMQ 异步调度
- 状态机
- 事件记录
- 失败处理和重试
- 幂等、取消、超时
- `LlmClient` 抽象
- `MockLlmClient`
- Prompt Template 数据模型
- Prompt Template 渲染器
- 执行链路使用 `default_task_prompt`
- 保存 `renderedPrompt` / `promptTemplateCode`

当前未实现：

- 真实 OpenAI / Claude / 本地模型 Provider
- Prompt Template CRUD API
- Token Usage
- Streaming Output
- RAG
- Tool Calling
- Agent Runtime
- KV Cache-aware Scheduling

## 十七、相关文档

- [README.md](../README.md)
- [docs/local-dev.md](local-dev.md)
- [docs/api-examples.md](api-examples.md)
- [docs/resume-interview.md](resume-interview.md)
