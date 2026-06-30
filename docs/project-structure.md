# 项目结构说明

## 一、文档目的

本文档用于帮助开发者理解 AI Task Orchestrator 的项目结构、核心包职责、核心类职责，以及一次任务从 HTTP 创建到异步执行、LLM mock 调用、重试、取消和超时处理的完整调用链路。

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

Controller 层负责接收 HTTP 请求，完成接口参数接收，并将业务处理委托给 Service 层。

`TaskController` 是任务主接口控制器，包含：

- `POST /tasks`：创建任务。
- `GET /tasks/{taskId}`：查询任务详情。
- `PATCH /tasks/{taskId}/status`：手动更新任务状态。
- `POST /tasks/{taskId}/cancel`：取消任务。

`DevTaskDispatchController` 是本地开发辅助控制器，包含：

- `POST /dev/tasks/{taskId}/dispatch`：手动重复投递指定任务的 MQ 消息。

该接口仅用于本地开发测试重复投递，不是生产接口。

## 四、dto 包

DTO 用于接口入参和出参，隔离数据库 Entity 和 HTTP API，避免直接把持久化模型暴露给外部调用方。

- `CreateTaskRequest`：创建任务请求体，包含任务 prompt。
- `CreateTaskResponse`：创建任务响应体，返回任务 ID 和初始状态。
- `TaskDetailResponse`：任务详情响应体，返回任务状态、错误信息、重试字段、超时字段、LLM 结果字段和时间信息。
- `UpdateTaskStatusRequest`：手动更新任务状态请求体。
- `DevTaskDispatchResponse`：本地开发重复投递接口响应体。

## 五、entity 包

Entity 负责映射数据库表，是 JPA 持久化层使用的对象。

`TaskEntity` 对应 `task` 表，用于保存任务当前状态和核心字段，包括：

- 任务 prompt。
- 当前状态。
- 错误信息。
- 重试字段：`retryCount`、`maxRetry`、`nextRetryAt`。
- 超时字段：`timeoutSeconds`、`timeoutAt`。
- LLM 结果字段：`resultContent`、`llmModel`。
- 创建时间和更新时间。

`TaskEventEntity` 对应 `task_event` 表，用于保存任务历史事件和状态变化，包括事件类型、原状态、目标状态、事件消息和创建时间。

## 六、enums 包

枚举用于表达系统中的有限状态和事件类型，让状态值和事件值保持可控。

`TaskStatus` 表示任务状态：

- `PENDING`
- `RUNNING`
- `RETRY_PENDING`
- `SUCCESS`
- `FAILED`
- `CANCELLED`

`TaskEventType` 表示任务事件类型：

- `TASK_CREATED`
- `STATUS_CHANGED`

## 七、state 包

状态机负责限制任务状态的合法流转，避免业务代码随意修改任务状态。

`TaskStateMachine` 用于判断 `fromStatus -> toStatus` 是否允许。它可以防止非法流转，例如：

- `SUCCESS -> RUNNING`
- `FAILED -> RUNNING`

当前主要合法流转：

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

Repository 负责数据库访问，封装对任务表和任务事件表的查询与保存。

`TaskRepository` 负责：

- 查询任务。
- 查询到期重试任务。
- 查询超时 `RUNNING` 任务。

`TaskEventRepository` 负责：

- 保存任务事件。

## 九、mq 包

MQ 层负责 RabbitMQ 消息投递和消费。当前 RabbitMQ 基础配置类位于 `config` 包，消息模型、生产者和消费者位于 `mq` 包。

`RabbitMQConfig` 负责定义 exchange、queue、binding 和 message converter。

`TaskDispatchMessage` 是 MQ 消息体，用于承载需要调度执行的任务 ID。

`TaskDispatchProducer` 负责发送任务调度消息。

`TaskDispatchConsumer` 负责接收任务调度消息，并调用 `TaskExecutionService` 执行任务。

## 十、llm 包

`llm` 包负责 LLM 调用抽象和模拟实现。

- `LlmClient`：统一 LLM 调用接口。
- `LlmRequest`：LLM 请求对象，包含 `taskId`、`prompt`、`model`。
- `LlmResponse`：LLM 响应对象，包含 `taskId`、`model`、`content`、`success`、`errorMessage`。
- `MockLlmClient`：模拟 LLM Provider，不调用外部 API，用于本地开发和流程验证。

当前系统只接入 `MockLlmClient`，尚未接入真实 OpenAI / Claude / 本地模型 Provider。

## 十一、service 包

Service 层承载核心业务逻辑，是任务生命周期控制的主要位置。

`TaskService` 负责：

- 创建任务。
- 查询任务。
- 状态流转。
- 记录 `task_event`。
- 标记失败。
- 标记重试等待。
- 标记成功并保存 `resultContent` / `llmModel`。
- 尝试开始执行：`tryStartTaskExecution`。
- 取消任务。
- 判断取消状态和运行状态。
- 标记超时。

`TaskExecutionService` 负责：

- 接收 Consumer 触发的任务执行。
- 调用 `tryStartTaskExecution` 做入口幂等保护。
- 构造 `LlmRequest`。
- 调用 `LlmClient.generate(...)`。
- 当前默认由 `MockLlmClient` 返回模拟 LLM 结果。
- 成功后保存 `resultContent` / `llmModel`。
- 失败后进入 `RETRY_PENDING` 或 `FAILED`。
- 协作式取消检查。
- 避免覆盖已取消或已超时任务。

## 十二、scheduler 包

Scheduler 负责后台定时扫描，把异步任务的重试和超时处理从 HTTP 请求链路中拆出来。

`TaskRetryScheduler` 负责：

- 扫描 `RETRY_PENDING` 且 `nextRetryAt` 到期的任务。
- 重新投递 MQ。
- 推迟 `nextRetryAt`，避免短时间重复投递同一个任务。

`TaskTimeoutScheduler` 负责：

- 扫描 `RUNNING` 且 `timeoutAt` 到期的任务。
- 将任务标记为 `FAILED`。
- 设置 `errorMessage = 任务执行超时`。

## 十三、resources/db/migration

`resources/db/migration` 目录由 Flyway 管理，用迁移脚本维护数据库结构版本。

当前迁移脚本包括：

- `V1__create_task_table.sql`
- `V2__create_task_event_table.sql`
- `V3__add_error_message_to_task.sql`
- `V4__add_retry_fields_to_task.sql`
- `V5__add_timeout_fields_to_task.sql`
- `V6__add_llm_result_fields_to_task.sql`

## 十四、一次任务完整执行链路

```text
1. 用户调用 POST /tasks
2. TaskController 接收请求
3. TaskService 创建 task，状态 PENDING
4. TaskService 写入 TASK_CREATED 事件
5. TaskDispatchProducer 发送 RabbitMQ 消息
6. TaskDispatchConsumer 收到消息
7. TaskExecutionService 调用 tryStartTaskExecution
8. PENDING / RETRY_PENDING -> RUNNING
9. TaskExecutionService 构造 LlmRequest
10. TaskExecutionService 调用 LlmClient.generate
11. MockLlmClient 返回 LlmResponse
12. 成功：保存 resultContent / llmModel，RUNNING -> SUCCESS
13. 失败可重试：RUNNING -> RETRY_PENDING
14. RetryScheduler 到期重新投递
15. 重试耗尽：RUNNING -> FAILED
16. 用户取消：进入 CANCELLED
17. TimeoutScheduler 超时：RUNNING -> FAILED
```

## 十五、当前架构边界

当前项目已经实现的是 AI 任务平台的可靠任务编排底座，并接入了 Mock LLM 执行链路。

当前已实现：

- 任务生命周期
- 异步调度
- 状态机
- 事件记录
- 失败处理
- 重试
- 幂等
- 取消
- 超时
- `LlmClient` 抽象
- `MockLlmClient`
- LLM mock 调用结果保存
- 本地环境工程化

当前未实现：

- 真实 OpenAI / Claude / 本地模型 Provider
- Prompt Template
- Token Usage
- Streaming Output
- RAG
- Tool Calling
- Agent Runtime
- Evaluation Harness
- KV Cache-aware Scheduling

## 十六、相关文档

- [README.md](../README.md)
- [docs/local-dev.md](local-dev.md)
- [docs/api-examples.md](api-examples.md)
- [docs/resume-interview.md](resume-interview.md)
