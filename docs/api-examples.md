# API 验收文档

## 一、文档目的

本文档用于记录 AI Task Orchestrator 当前阶段的 API 调用示例和验收方式，方便本地验证任务创建、查询、Prompt Template 渲染、Mock LLM 执行、结果保存、失败重试、幂等、取消和超时处理。

## 二、前置条件

验收前需要确认：

- Docker Compose 已启动 MySQL 和 RabbitMQ。
- Spring Boot 已使用 `docker` profile 启动。
- RabbitMQ 管理后台可访问。
- MySQL 可连接。
- Flyway 迁移已执行成功。
- `prompt_template` 表中存在启用的 `default_task_prompt`。

详细本地启动方式见：[docs/local-dev.md](local-dev.md)。

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

说明：

创建任务后，任务初始状态为 `PENDING`。Consumer 消费消息后，`TaskExecutionService` 会查询 `default_task_prompt`，用 `PromptTemplateRenderer` 渲染 `renderedPrompt`，再调用 `LlmClient.generate(...)`。当前默认由 `MockLlmClient` 返回模拟 LLM 结果。

期望响应示例：

```json
{
  "taskId": 1,
  "status": "PENDING"
}
```

## 四、查询任务

接口：

```http
GET http://localhost:8080/tasks/{taskId}
```

响应字段至少包括：

- `id`
- `prompt`
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
- `createdAt`
- `updatedAt`

响应示例：

```json
{
  "id": 1,
  "prompt": "normal task",
  "status": "SUCCESS",
  "errorMessage": null,
  "retryCount": 0,
  "maxRetry": 3,
  "nextRetryAt": null,
  "timeoutSeconds": 30,
  "timeoutAt": "2026-06-30T12:00:30",
  "resultContent": "Mock LLM response for prompt: 你是一个任务执行助手，请根据用户输入完成任务。用户输入：normal task",
  "llmModel": "mock-llm",
  "renderedPrompt": "你是一个任务执行助手，请根据用户输入完成任务。用户输入：normal task",
  "promptTemplateCode": "default_task_prompt",
  "createdAt": "2026-06-30T12:00:00",
  "updatedAt": "2026-06-30T12:00:05"
}
```

## 五、正常任务验收

步骤：

1. 创建 `prompt = normal task` 的任务。
2. 等待几秒。
3. 查询任务。
4. 期望 `status = SUCCESS`。
5. 期望 `llmModel = mock-llm`。
6. 期望 `resultContent` 不为空。
7. 期望 `renderedPrompt` 不为空。
8. 期望 `promptTemplateCode = default_task_prompt`。
9. 期望 `resultContent` 能看出 MockLlmClient 收到了模板渲染后的 Prompt。

SQL：

```sql
SELECT id, status, llm_model, result_content, rendered_prompt, prompt_template_code, error_message
FROM task
WHERE id = 你的任务ID;
```

事件 SQL：

```sql
SELECT task_id, event_type, from_status, to_status, message, created_at
FROM task_event
WHERE task_id = 你的任务ID
ORDER BY created_at;
```

期望事件：

- `TASK_CREATED`
- `PENDING -> RUNNING`
- `RUNNING -> SUCCESS`

## 六、失败重试验收

创建任务：

```json
{
  "prompt": "please fail this task"
}
```

说明：

默认模板渲染后的 Prompt 仍包含 `fail`，因此 `MockLlmClient` 会返回失败响应，不调用真实外部模型。

期望流程：

- `PENDING -> RUNNING`
- `RUNNING -> RETRY_PENDING`
- `RETRY_PENDING -> RUNNING`
- 多次重试
- 最终 `RUNNING -> FAILED`

期望最终：

- `status = FAILED`
- `retryCount = maxRetry`
- `errorMessage` 包含 `Mock LLM execution failed`
- 失败任务不应保存成功的 `resultContent`

## 七、Prompt Template 验收

查询默认模板：

```sql
SELECT template_code, template_name, template_content, enabled
FROM prompt_template
WHERE template_code = 'default_task_prompt';
```

期望：

- `enabled = 1`
- `template_content` 包含 `{{prompt}}`

临时禁用默认模板：

```sql
UPDATE prompt_template
SET enabled = FALSE
WHERE template_code = 'default_task_prompt';
```

创建任务后，期望进入失败处理逻辑：先 `RETRY_PENDING`，重试耗尽后 `FAILED`，错误信息包含 `Default prompt template not found: default_task_prompt`。

恢复默认模板：

```sql
UPDATE prompt_template
SET enabled = TRUE
WHERE template_code = 'default_task_prompt';
```

## 八、取消任务验收

验收三个场景：

1. `PENDING -> CANCELLED`
2. `RETRY_PENDING -> CANCELLED`
3. `RUNNING -> CANCELLED`

取消后不应再变成：

- `SUCCESS`
- `FAILED`
- `RETRY_PENDING`

## 九、超时任务验收

创建任务后，任务进入 `RUNNING` 时手动把 `timeout_at` 改为过去时间：

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

## 十、重复投递验收

接口：

```http
POST http://localhost:8080/dev/tasks/{taskId}/dispatch
```

说明：

该接口仅用于本地开发测试重复投递，不是生产接口。

期望：

- 对 `SUCCESS` / `FAILED` / `CANCELLED` 任务重复投递，任务不应重新执行。
- `task_event` 不应出现 `SUCCESS -> RUNNING`、`FAILED -> RUNNING`、`CANCELLED -> RUNNING`。

## 十一、常用 SQL

查询最近任务：

```sql
SELECT id, prompt, status, error_message, retry_count, max_retry, next_retry_at,
       timeout_seconds, timeout_at, llm_model, result_content,
       rendered_prompt, prompt_template_code, created_at, updated_at
FROM task
ORDER BY id DESC;
```

查询某个任务事件：

```sql
SELECT task_id, event_type, from_status, to_status, message, created_at
FROM task_event
WHERE task_id = 你的任务ID
ORDER BY created_at;
```

查询 Flyway 版本：

```sql
SELECT version, description, script, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

## 十二、当前 API 列表

- `POST /tasks`
- `GET /tasks/{taskId}`
- `PATCH /tasks/{taskId}/status`
- `POST /tasks/{taskId}/cancel`
- `POST /dev/tasks/{taskId}/dispatch`

## 十三、注意事项

- 当前项目尚未接入真实 OpenAI / Claude / 本地模型 Provider。
- 当前 LLM 执行由 `MockLlmClient` 模拟。
- 当前已接入 Prompt Template 执行链路，但尚未实现 Prompt Template CRUD API。
- 当前不保存额外的 Token Usage，也不支持 Streaming / RAG / Agent / KV Cache。
- `/dev` 开头接口只用于本地验收。
- 当前 Docker Compose 只管理 MySQL 和 RabbitMQ，Spring Boot 仍由本机启动。
