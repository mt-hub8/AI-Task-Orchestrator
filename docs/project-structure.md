# 项目结构说明

## 一、文档目的

本文档用于帮助开发者理解 AI Task Orchestrator 的项目结构、核心类职责、任务执行链路、文档上传与 chunking 链路，以及当前架构边界。

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
├── local-dev.md
├── api-examples.md
├── project-structure.md
└── resume-interview.md
```

## 三、controller 包

Controller 层负责接收 HTTP 请求。

`TaskController`：

- `POST /tasks`
- `GET /tasks/{taskId}`
- `PATCH /tasks/{taskId}/status`
- `POST /tasks/{taskId}/cancel`
- `GET /tasks/{taskId}/output-chunks`

`DevTaskDispatchController`：

- `POST /dev/tasks/{taskId}/dispatch`
- 仅用于本地开发测试重复投递，不是生产接口。

`DocumentController`：

- `POST /documents`
- `GET /documents/{documentId}`
- `GET /documents/{documentId}/chunks`

## 四、dto 包

DTO 用于接口入参和出参，隔离 Entity 和 HTTP API。

任务相关：

- `CreateTaskRequest`
- `CreateTaskResponse`
- `TaskDetailResponse`
- `UpdateTaskStatusRequest`
- `DevTaskDispatchResponse`
- `TaskOutputChunkResponse`

文档相关：

- `DocumentUploadResponse`
- `DocumentDetailResponse`
- `DocumentChunkResponse`

LLM 相关对象在 `llm` 包中：

- `LlmRequest`
- `LlmResponse`

## 五、entity 包

Entity 映射数据库表。

`TaskEntity`：

- 对应 `task` 表。
- 保存任务当前状态、prompt、requestedModel、错误信息、重试字段、超时字段、LLM 结果、Prompt 渲染信息和 LLM usage metadata。

`TaskEventEntity`：

- 对应 `task_event` 表。
- 保存任务历史事件和状态变化。

`TaskOutputChunkEntity`：

- 对应 `task_output_chunk` 表。
- 保存任务输出片段。

`PromptTemplateEntity`：

- 对应 `prompt_template` 表。
- 保存 Prompt Template 定义。

`DocumentEntity`：

- 对应 `document` 表。
- 保存文档元信息、状态、chunk 数量和错误信息。

`DocumentChunkEntity`：

- 对应 `document_chunk` 表。
- 保存文档切分后的文本片段。

## 六、enums 包

枚举用于表达有限状态和事件类型。

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

`DocumentStatus`：

- `UPLOADED`
- `CHUNKED`
- `FAILED`

## 七、state 包

`TaskStateMachine` 负责限制任务状态合法流转。

主要合法流转：

- `PENDING -> RUNNING`
- `PENDING -> CANCELLED`
- `RUNNING -> SUCCESS`
- `RUNNING -> RETRY_PENDING`
- `RUNNING -> FAILED`
- `RUNNING -> CANCELLED`
- `RETRY_PENDING -> RUNNING`
- `RETRY_PENDING -> FAILED`
- `RETRY_PENDING -> CANCELLED`

状态机用于防止非法流转，例如 `SUCCESS -> RUNNING`、`FAILED -> RUNNING`。

## 八、repository 包

Repository 负责数据库访问。

- `TaskRepository`：查询任务、到期重试任务、超时 RUNNING 任务。
- `TaskEventRepository`：保存任务事件。
- `TaskOutputChunkRepository`：查询任务输出 chunks。
- `PromptTemplateRepository`：按 templateCode 查询启用模板。
- `DocumentRepository`：文档元信息访问。
- `DocumentChunkRepository`：按 documentId 查询文档 chunks。

## 九、mq 包

MQ 层负责 RabbitMQ 消息投递和消费。

- `RabbitMQConfig`：定义 exchange / queue / binding / message converter。
- `TaskDispatchMessage`：MQ 消息体。
- `TaskDispatchProducer`：发送任务调度消息。
- `TaskDispatchConsumer`：接收任务调度消息并调用 `TaskExecutionService`。

## 十、service 包

Service 层承载核心业务逻辑。

`TaskService`：

- 创建任务
- 查询任务
- 状态流转
- 记录 `task_event`
- 标记失败
- 标记重试等待
- 尝试开始执行 `tryStartTaskExecution`
- 取消任务
- 判断取消/运行状态
- 标记超时
- 保存 LLM metadata
- 保存成功结果

`TaskExecutionService`：

- Consumer 收到消息后的任务执行入口。
- 做入口幂等保护。
- 模拟执行延迟并检查取消。
- 使用 `ModelRouter` 选择模型。
- 查询 Prompt Template。
- 渲染 `renderedPrompt`。
- 调用 `LlmClient`。
- 保存 LLM metadata。
- 成功后保存 output chunks 和完整 resultContent。
- 失败后进入重试或最终失败。
- 避免覆盖已取消或已超时任务。

`TaskOutputChunkService`：

- 将完整输出拆成固定长度 chunks。
- 保存 `task_output_chunk`。
- 查询任务输出 chunks。

`DocumentService`：

- 校验上传文件。
- 读取 `.txt` / `.md` UTF-8 文本。
- 按固定长度切分文档内容。
- 保存 `document` 和 `document_chunk`。
- 查询文档详情。
- 查询文档 chunks。

## 十一、scheduler 包

Scheduler 负责后台定时扫描。

`TaskRetryScheduler`：

- 扫描 `RETRY_PENDING` 且 `nextRetryAt` 到期的任务。
- 重新投递 MQ。
- 推迟 `nextRetryAt`，避免短时间重复投递。

`TaskTimeoutScheduler`：

- 扫描 `RUNNING` 且 `timeoutAt` 到期的任务。
- 标记为 `FAILED`。
- `errorMessage = 任务执行超时`。

## 十二、llm 包

`llm` 包负责 LLM 调用抽象、Mock Provider 和模型路由。

- `LlmClient`：统一 LLM 调用接口。
- `LlmRequest`：LLM 请求对象，包含 taskId、prompt、model。
- `LlmResponse`：LLM 响应对象，包含 taskId、model、content、success、errorMessage、provider、token usage、latency。
- `MockLlmClient`：模拟 LLM Provider，不调用外部 API。
- `ModelRouter`：根据 requestedModel 选择实际执行模型。

当前 `ModelRouter` 支持：

- `mock-llm`
- `mock-fast`
- `mock-smart`

未知模型 fallback 到 `mock-llm`。

## 十三、prompt 包

`prompt` 包负责 Prompt Template 渲染。

`PromptTemplateRenderer`：

- 支持 `{{prompt}}`
- 支持 `{{ taskId }}`
- 支持 `{{model}}`
- 缺失变量时抛出异常

当前默认模板为 `default_task_prompt`。

## 十四、document 相关结构

Document Upload & Chunking 涉及：

- `DocumentController`
- `DocumentService`
- `DocumentEntity`
- `DocumentChunkEntity`
- `DocumentRepository`
- `DocumentChunkRepository`
- `DocumentStatus`
- `DocumentUploadResponse`
- `DocumentDetailResponse`
- `DocumentChunkResponse`

当前只支持 `.txt` / `.md` 文本文件，不保存原始文件到磁盘。

## 十五、task_output_chunk 相关结构

持久化增量输出涉及：

- `TaskOutputChunkEntity`
- `TaskOutputChunkRepository`
- `TaskOutputChunkService`
- `TaskOutputChunkResponse`
- `GET /tasks/{taskId}/output-chunks`

当前不是 SSE / WebSocket，只是异步任务系统下的持久化输出片段查询。

## 十六、resources/db/migration

Flyway 迁移脚本管理数据库结构。

- `V1__create_task_table.sql`
- `V2__create_task_event_table.sql`
- `V3__add_error_message_to_task.sql`
- `V4__add_retry_fields_to_task.sql`
- `V5__add_timeout_fields_to_task.sql`
- `V6__add_llm_result_fields_to_task.sql`
- `V7__create_prompt_template_table.sql`
- `V8__add_prompt_render_fields_to_task.sql`
- `V9__add_llm_usage_fields_to_task.sql`
- `V10__add_requested_model_to_task.sql`
- `V11__create_task_output_chunk_table.sql`
- `V12__create_document_tables.sql`

## 十七、一次任务完整执行链路

```text
1. 用户调用 POST /tasks
2. TaskController 接收请求
3. TaskService 创建 task，状态 PENDING
4. TaskService 写入 TASK_CREATED 事件
5. TaskDispatchProducer 发送 RabbitMQ 消息
6. TaskDispatchConsumer 收到消息
7. TaskExecutionService 调用 tryStartTaskExecution
8. PENDING / RETRY_PENDING -> RUNNING
9. ModelRouter 选择实际模型
10. 查询 default_task_prompt
11. PromptTemplateRenderer 渲染 renderedPrompt
12. LlmClient.generate 调用 MockLlmClient
13. 保存 LLM metadata
14. 成功：保存 output chunks
15. 成功：RUNNING -> SUCCESS
16. 失败可重试：RUNNING -> RETRY_PENDING
17. RetryScheduler 到期重新投递
18. 重试耗尽：RUNNING -> FAILED
19. 用户取消：进入 CANCELLED
20. TimeoutScheduler 超时：RUNNING -> FAILED
```

## 十八、文档上传与 chunking 链路

```text
1. 用户调用 POST /documents
2. DocumentController 接收 multipart file
3. DocumentService 校验文件名
4. 只允许 .txt / .md
5. 保存 document，状态 UPLOADED
6. UTF-8 读取文本
7. 按 500 字符切分
8. 保存 document_chunk
9. 更新 document 状态 CHUNKED
10. 返回 documentId / status / chunkCount
```

处理失败时：

```text
document -> FAILED
保存 errorMessage
返回 500 Document processing failed
```

## 十九、当前架构边界

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
- Prompt Template
- Mock LLM
- Model Router
- LLM usage metadata
- 持久化 output chunks
- Document Upload & Chunking
- 本地环境工程化

当前未实现：

- 真实 OpenAI / Claude / 本地模型 Provider
- 真实 streaming provider
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

## 二十、相关文档

- [README.md](../README.md)
- [docs/local-dev.md](local-dev.md)
- [docs/api-examples.md](api-examples.md)
- [docs/resume-interview.md](resume-interview.md)

