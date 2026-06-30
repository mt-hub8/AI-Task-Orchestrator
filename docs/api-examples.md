# API 验收文档

## 一、文档目的

本文档用于记录 AI Task Orchestrator 当前阶段的 API 调用示例、状态流转、数据库验证方式和手动验收步骤。

## 二、前置条件

验收前需要：

- Docker Compose 已启动 MySQL 和 RabbitMQ。
- Spring Boot 已用 `docker` profile 启动。
- RabbitMQ 管理后台可访问。
- MySQL 可连接。
- Flyway 迁移已执行成功。

本地启动方式见：[docs/local-dev.md](local-dev.md)。

## 三、创建任务

接口：

```http
POST http://localhost:8080/tasks
Content-Type: application/json
```

请求体：

```json
{
  "prompt": "normal task"
}
```

指定模型：

```json
{
  "prompt": "normal task",
  "model": "mock-fast"
}
```

说明：

- 创建任务后初始状态为 `PENDING`。
- 系统会发送 RabbitMQ 消息，由 Consumer 异步执行。
- `model` 会保存为 `requestedModel`，执行阶段由 `ModelRouter` 选择实际模型。

期望响应包含：

- `taskId`
- `status = PENDING`

## 四、查询任务

接口：

```http
GET http://localhost:8080/tasks/{taskId}
```

主要响应字段：

- `taskId`
- `prompt`
- `requestedModel`
- `status`
- `errorMessage`
- `retryCount`
- `maxRetry`
- `nextRetryAt`
- `timeoutSeconds`
- `timeoutAt`
- `resultContent`
- `llmModel`
- `renderedPrompt`
- `promptTemplateCode`
- `llmProvider`
- `promptTokenCount`
- `completionTokenCount`
- `totalTokenCount`
- `llmLatencyMs`
- `createdAt`
- `updatedAt`

## 五、手动修改任务状态

接口：

```http
PATCH http://localhost:8080/tasks/{taskId}/status
Content-Type: application/json
```

请求体：

```json
{
  "status": "CANCELLED",
  "message": "手动取消任务"
}
```

说明：

- 该接口用于测试状态机，不是主要业务入口。
- 状态机会阻止非法流转，例如 `SUCCESS -> RUNNING`、`FAILED -> RUNNING`、`CANCELLED -> RUNNING`。

## 六、取消任务

接口：

```http
POST http://localhost:8080/tasks/{taskId}/cancel
```

支持取消：

- `PENDING`
- `RETRY_PENDING`
- `RUNNING`

取消后状态变为 `CANCELLED`。

验收场景：

- `PENDING -> CANCELLED`
- `RETRY_PENDING -> CANCELLED`
- `RUNNING -> CANCELLED`
- `SUCCESS / FAILED` 取消应返回 400

## 七、开发测试重复投递

接口：

```http
POST http://localhost:8080/dev/tasks/{taskId}/dispatch
```

说明：

- 仅用于本地开发测试。
- 用来模拟 RabbitMQ 重复投递同一个 `taskId`。
- 不是生产接口。

验收：

- 对 `SUCCESS` 任务重复投递，任务不应重新执行。
- 对 `FAILED` 任务重复投递，任务不应重新执行。
- 对 `CANCELLED` 任务重复投递，任务不应重新执行。
- `task_event` 不应出现 `SUCCESS -> RUNNING`、`FAILED -> RUNNING`、`CANCELLED -> RUNNING`。

## 八、查询 output chunks

接口：

```http
GET http://localhost:8080/tasks/{taskId}/output-chunks
```

说明：

- 查询持久化增量输出片段。
- 当前不是 SSE / WebSocket。
- chunk 来自 Mock LLM 成功响应后的 content 拆分。

期望：

- 返回多个 chunk。
- `chunkIndex` 从 0 开始递增。
- 按 `chunkIndex` 拼接所有 `content` 后，能组成 `resultContent` 的主要内容。

## 九、上传文档

接口：

```http
POST http://localhost:8080/documents
Content-Type: multipart/form-data
```

参数：

- `file`

支持：

- `.txt`
- `.md`

期望响应：

```json
{
  "documentId": 1,
  "originalFilename": "demo.txt",
  "status": "CHUNKED",
  "chunkCount": 2
}
```

非 `.txt` / `.md` 文件应返回 400。

## 十、查询文档

接口：

```http
GET http://localhost:8080/documents/{documentId}
```

响应字段：

- `id`
- `originalFilename`
- `contentType`
- `fileSize`
- `status`
- `chunkCount`
- `errorMessage`
- `createdAt`
- `updatedAt`

## 十一、查询文档 chunks

接口：

```http
GET http://localhost:8080/documents/{documentId}/chunks
```

响应字段：

- `id`
- `documentId`
- `chunkIndex`
- `content`
- `contentLength`
- `createdAt`

期望：

- `chunkIndex` 从 0 开始递增。
- 每个 chunk 最多约 500 字符。
- 空白文本不生成 chunk。

## 十二、正常任务验收

步骤：

1. 创建 `prompt = normal task` 的任务。
2. 等待几秒。
3. 查询任务。
4. 期望 `status = SUCCESS`。
5. 查询 `task_event`。

期望事件：

- `TASK_CREATED`
- `PENDING -> RUNNING`
- `RUNNING -> SUCCESS`

SQL：

```sql
SELECT task_id, event_type, from_status, to_status, message, created_at
FROM task_event
WHERE task_id = 你的任务ID
ORDER BY created_at;
```

## 十三、失败重试验收

创建任务：

```json
{
  "prompt": "please fail this task"
}
```

说明：

- 当前 Mock 失败规则是 prompt 包含 `fail` 或 `失败`。

期望流程：

- `PENDING -> RUNNING`
- `RUNNING -> RETRY_PENDING`
- `RETRY_PENDING -> RUNNING`
- 多次重试
- 最终 `RUNNING -> FAILED`

期望最终：

- `status = FAILED`
- `retryCount = maxRetry`
- `errorMessage` 不为空

## 十四、重试成功验收

因为 prompt 包含 `fail` 会一直失败，所以需要在任务进入 `RETRY_PENDING` 后手动修改 prompt：

```sql
UPDATE task
SET prompt = 'retry success task'
WHERE id = 你的任务ID;
```

等待 Scheduler 重新投递。

期望：

- `RETRY_PENDING -> RUNNING`
- `RUNNING -> SUCCESS`

## 十五、取消验收

场景：

- `PENDING -> CANCELLED`
- `RETRY_PENDING -> CANCELLED`
- `RUNNING -> CANCELLED`

取消后不应再变成：

- `SUCCESS`
- `FAILED`
- `RETRY_PENDING`

## 十六、超时验收

当前超时字段：

- `timeout_seconds`
- `timeout_at`

验收方式：

```sql
UPDATE task
SET timeout_at = NOW(6) - INTERVAL 1 SECOND
WHERE id = 你的任务ID
  AND status = 'RUNNING';
```

等待 TimeoutScheduler 扫描。

期望：

- `status = FAILED`
- `errorMessage = 任务执行超时`
- `task_event` 有 `RUNNING -> FAILED`

## 十七、Model Router 验收

不传 `model`：

- `requestedModel = null`
- `llmModel = mock-llm`

传入 `mock-fast`：

```json
{
  "prompt": "normal task",
  "model": "mock-fast"
}
```

期望：

- `requestedModel = mock-fast`
- `llmModel = mock-fast`
- `status = SUCCESS`

传入未知模型：

```json
{
  "prompt": "normal task",
  "model": "unknown-model"
}
```

期望：

- `requestedModel = unknown-model`
- `llmModel = mock-llm`
- `status = SUCCESS`

## 十八、LLM usage 验收

正常任务成功后，期望：

- `llmProvider = mock`
- `llmModel` 不为空
- `promptTokenCount` 不为空
- `completionTokenCount` 不为空
- `totalTokenCount` 不为空
- `llmLatencyMs` 不为空

失败任务也应记录 prompt token 和 total token 等 metadata。

## 十九、Output chunks 验收

正常任务成功后：

```http
GET /tasks/{taskId}/output-chunks
```

期望：

- 返回 chunk 列表。
- `chunkIndex` 从 0 开始。
- 拼接 chunk content 后能组成 `resultContent` 的主要内容。

失败任务不应保存成功 output chunks。

## 二十、Document Upload & Chunking 验收

上传 `.txt` 文件：

- 返回 `status = CHUNKED`
- `chunkCount > 0`

查询详情：

```http
GET /documents/{documentId}
```

查询 chunks：

```http
GET /documents/{documentId}/chunks
```

上传非 `.txt` / `.md` 文件：

- 返回 400
- 错误信息为 `Only .txt and .md files are supported`

## 二十一、常用 SQL

查询最近任务：

```sql
SELECT id, prompt, requested_model, status, error_message,
       retry_count, max_retry, next_retry_at,
       timeout_seconds, timeout_at,
       llm_provider, llm_model,
       prompt_token_count, completion_token_count, total_token_count, llm_latency_ms,
       rendered_prompt, prompt_template_code,
       result_content, created_at, updated_at
FROM task
ORDER BY id DESC;
```

查询任务事件：

```sql
SELECT task_id, event_type, from_status, to_status, message, created_at
FROM task_event
WHERE task_id = 你的任务ID
ORDER BY created_at;
```

查询输出 chunks：

```sql
SELECT task_id, chunk_index, content, created_at
FROM task_output_chunk
WHERE task_id = 你的任务ID
ORDER BY chunk_index;
```

查询文档：

```sql
SELECT id, original_filename, content_type, file_size, status, chunk_count, error_message, created_at, updated_at
FROM document
ORDER BY id DESC;
```

查询文档 chunks：

```sql
SELECT document_id, chunk_index, content_length, content, created_at
FROM document_chunk
WHERE document_id = 你的文档ID
ORDER BY chunk_index;
```

查询 Flyway：

```sql
SELECT version, description, script, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

## 二十二、注意事项

- 当前项目尚未接入真实 OpenAI / Claude / 本地模型 Provider。
- 当前 LLM 执行由 `MockLlmClient` 模拟。
- 当前 token usage 是 Mock 估算，不是真实 tokenizer。
- 当前 output chunks 是持久化分片查询，不是 SSE / WebSocket。
- 当前文档上传只支持 `.txt` / `.md`。
- 当前尚未实现 PDF / Word 解析。
- 当前尚未实现 Embedding、Vector DB、Semantic Search 或 RAG Answer。
- `/dev` 开头接口只用于本地验收。
- Docker Compose 只管理 MySQL 和 RabbitMQ，Spring Boot 仍由本机启动。

