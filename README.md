# AI Task Orchestrator

## 1. Project Overview

AI Task Orchestrator 是一个基于 Java / Spring Boot 的 AI 任务编排后端系统。它的重点不是简单调用大模型 API，而是构建面向 LLM / RAG / Agent 工作负载的异步任务编排底座。

当前项目已经覆盖长耗时任务创建、异步调度、状态追踪、失败处理、自动重试、幂等控制、取消、超时、Transactional Outbox、Atomic Task Claim、Task Attempt、Mock LLM 执行、Prompt Template、Model Router、LLM usage metadata、持久化输出片段，以及 RAG 检索前置能力。

英文一句话定位：

> AI Task Orchestrator is a Java / Spring Boot based backend system for orchestrating long-running AI workloads. It focuses on the reliability foundation behind LLM / RAG / Agent workloads, including asynchronous dispatch, task lifecycle management, transactional outbox, atomic task claiming, retry / cancellation / timeout handling, execution attempts, LLM routing metadata, and a RAG retrieval prototype.

当前项目是一个 production-oriented prototype，不是完整 production-grade platform，也不是完整 Agent Runtime。

## 2. Architecture

当前核心架构：

```text
HTTP API
-> TaskService / DocumentService
-> MySQL
   -> task
   -> task_event
   -> task_outbox
   -> task_attempt
   -> task_output_chunk
   -> document
   -> document_chunk
   -> document_chunk_embedding
-> Outbox Dispatcher
-> RabbitMQ
-> Consumer
-> Atomic Task Claim
-> TaskExecutionService
-> Prompt Template
-> ModelRouter
-> MockLlmClient
-> task_attempt / task / task_output_chunk 更新
```

RAG 检索原型架构：

```text
Document Upload
-> Adaptive Chunking
-> document_chunk
-> MockEmbeddingClient
-> document_chunk_embedding
-> Query embedding
-> Java in-memory cosine similarity exact scan
-> TopK document chunks
```

## 3. Core Flow: Outbox + Atomic Claim + Attempt

README 旧流程里曾经是：

```text
用户提交任务
-> task 入库
-> 直接发送 RabbitMQ
-> Consumer 执行
```

当前真实流程已经升级为：

```text
POST /tasks
-> task 入库，status = PENDING
-> task_event 写入 TASK_CREATED
-> task_outbox 写入 TASK_DISPATCH_REQUESTED
-> 数据库事务提交
-> Outbox Dispatcher 扫描 PENDING / FAILED outbox
-> outbox 原子 claim：PENDING / FAILED -> PROCESSING
-> 发送 RabbitMQ
-> outbox 标记 SENT
-> Consumer 接收 TaskDispatchMessage
-> task 原子 claim：PENDING / RETRY_PENDING -> RUNNING
-> 创建 task_attempt，status = RUNNING
-> 渲染 Prompt Template
-> ModelRouter 选择模型
-> 调用 MockLlmClient
-> 保存 attempt metadata
-> 保存 task_output_chunk
-> task_attempt -> SUCCESS / FAILED / CANCELLED
-> task -> SUCCESS / RETRY_PENDING / FAILED / CANCELLED
```

关键说明：

1. `createTask` 不再直接发送 RabbitMQ。
2. RabbitMQ 投递由 `Outbox Dispatcher` 负责。
3. Consumer 入口通过 atomic claim 防止重复执行。
4. `task_attempt` 用于保存每次执行尝试。
5. `task` 表保存当前状态和最终摘要。
6. `task_event` 保存状态变化历史。
7. `task_outbox` 保存可靠投递消息。

## 4. Features

### 阶段 0：Reliable Async Task System

- task lifecycle
- state machine
- `task_event`
- RabbitMQ async dispatch
- failure handling
- retry
- cancellation
- timeout
- idempotency
- Docker Compose 本地 MySQL / RabbitMQ

### 阶段 1：LLM Execution System

- `LlmClient`
- `MockLlmClient`
- Prompt Template
- Model Router
- LLM usage metadata
- persisted output chunks
- `GET /tasks/{taskId}/output-chunks`

### 阶段 2：RAG Retrieval Prototype

当前已经实现 RAG 检索原型：

- Document Upload
- Adaptive Chunking
- `headingPath`
- `startOffset` / `endOffset`
- `chunkStrategy`
- `MockEmbeddingClient`
- `document_chunk_embedding`
- MySQL `TEXT` vector storage
- Java in-memory cosine similarity exact scan
- `POST /documents/{documentId}/embeddings`
- `POST /documents/search`

### 阶段 2.2.x：Production Hardening

- GitHub Actions CI
- baseline tests
- atomic task claim
- transactional outbox
- outbox dispatcher
- reliable dispatch
- `task_attempt`
- structured logs baseline

当前 README 不声明已经完成完整 metrics pipeline。后续如果真正接入 Micrometer / Actuator / Prometheus，再更新为已实现。

## 5. API

### 正式任务 API

| Method | Path | 说明 |
| --- | --- | --- |
| `POST` | `/tasks` | 创建 AI 任务 |
| `GET` | `/tasks/{taskId}` | 查询任务详情 |
| `POST` | `/tasks/{taskId}/cancel` | 取消任务 |
| `GET` | `/tasks/{taskId}/output-chunks` | 查询持久化输出片段 |

`GET /tasks/{taskId}/attempts` 当前尚未作为 Controller API 暴露。它可以作为后续版本能力，用于展示 `task_attempt` 执行历史。

### 正式文档 / 检索 API

| Method | Path | 说明 |
| --- | --- | --- |
| `POST` | `/documents` | 上传 `.txt` / `.md` 文档 |
| `GET` | `/documents/{documentId}` | 查询文档详情 |
| `GET` | `/documents/{documentId}/chunks` | 查询文档 chunks |
| `POST` | `/documents/{documentId}/embeddings` | 为文档 chunks 生成 Mock Embedding |
| `POST` | `/documents/search` | 根据 query 搜索 TopK chunks |

### Development / Internal API

| Method | Path | 说明 |
| --- | --- | --- |
| `POST` | `/dev/tasks/{taskId}/dispatch` | 本地开发测试重复投递 |
| `PATCH` | `/tasks/{taskId}/status` | 历史开发调试状态接口 |

`PATCH /tasks/{taskId}/status` 不应作为正式用户 API 暴露。后续计划迁移为 `/dev/tasks/{taskId}/status`，并只在 dev profile 下启用。

## 6. Data Model

主要表职责：

- `task`：任务当前状态和最终摘要。
- `task_event`：任务状态变化事件历史。
- `task_outbox`：待投递消息，支撑 Transactional Outbox。
- `task_attempt`：每次执行尝试的审计记录。
- `task_output_chunk`：LLM 输出分块。
- `document`：上传文档元信息。
- `document_chunk`：文档切分片段和 metadata。
- `document_chunk_embedding`：chunk embedding 派生数据。

职责边界：

- `task` 表不是所有执行历史，只保存当前状态和最终摘要。
- `task_event` 保存生命周期事件，例如创建、状态变化。
- `task_outbox` 保存可靠投递消息，解决 DB 与 MQ 的 dual write problem。
- `task_attempt` 保存每次执行证据，包括 provider、model、prompt、token、latency、error、status。
- `task_output_chunk` 保存输出片段，支撑增量输出查询。
- `document_chunk_embedding` 保存由 chunk 派生出的 Mock vector 数据。

## 7. Reliability Design

### State Machine

状态机约束任务生命周期，防止终态任务重新进入 RUNNING。

主要状态：

- `PENDING`
- `RUNNING`
- `RETRY_PENDING`
- `SUCCESS`
- `FAILED`
- `CANCELLED`

终态：

- `SUCCESS`
- `FAILED`
- `CANCELLED`

### Retry

任务失败后，如果仍有重试次数，可以进入 `RETRY_PENDING`。重试由 `retryCount`、`maxRetry`、`nextRetryAt` 和调度器配合完成。

### Cancellation

任务支持等待中取消，也支持 RUNNING 任务协作式取消。执行线程会定期检查任务是否已被取消。

### Timeout

任务进入 RUNNING 时设置 `timeoutAt`。超时扫描器会把到期 RUNNING 任务标记为 FAILED。执行线程在写 SUCCESS 前会再次检查任务是否仍是 RUNNING，避免覆盖超时结果。

### Atomic Task Claim

Consumer 执行任务前必须通过数据库原子条件更新：

```text
PENDING / RETRY_PENDING -> RUNNING
```

如果更新行数为 `0`，说明任务已经被其他 Consumer claim，或已经取消、失败、成功、超时，当前消息会被安全忽略。

Atomic Claim 解决多 Consumer / 重复 MQ 消息下的重复执行风险。

### Transactional Outbox

`createTask` 在同一个数据库事务中写入：

- `task`
- `task_event`
- `task_outbox`

RabbitMQ 投递不再发生在 `createTask` 内，而是由 Outbox Dispatcher 异步完成。

Transactional Outbox 解决 DB 和 MQ 的 dual write problem：

- DB 回滚但 MQ 已发出。
- DB 提交成功但 MQ 发送失败。

Outbox 本质仍是 at-least-once 投递，因此仍然需要 Consumer 侧幂等和 atomic claim。

### Task Attempt

`task_attempt` 记录每次执行尝试，避免重试历史被 `task` 表最终摘要覆盖。

它可以支撑：

- retry 审计；
- 失败诊断；
- 模型和 provider 分析；
- token 和 latency 分析；
- 后续成本统计。

### CI / Tests

GitHub Actions 会在 `push` / `pull_request` 时运行 Maven test。

当前项目已经有 baseline tests 覆盖状态机、Prompt 渲染、Chunking、Embedding 工具、Mock Embedding、Atomic Claim、Outbox、Task Attempt 等关键逻辑。

本地运行测试：

```powershell
.\mvnw.cmd test
```

README 不编造测试数量。最新 Tests run 数字以本地或 CI 实际输出为准。

## 8. RAG Retrieval Prototype

当前已经实现：

- Mock Embedding
- `document_chunk_embedding`
- MySQL `TEXT` vector storage
- Java in-memory cosine similarity exact scan
- document-level semantic search prototype
- `POST /documents/search`

当前检索流程：

```text
上传文档
-> Adaptive Chunking
-> 保存 document_chunk
-> 生成 Mock chunk embedding
-> 保存 document_chunk_embedding
-> query 生成 query embedding
-> Java 内存中计算 cosine similarity
-> 返回 TopK chunks 和 metadata
```

尚未实现：

- Real Embedding Provider
- Vector DB
- ANN Search
- Hybrid Search
- Rerank
- RAG Answer
- Citation
- Retrieval Evaluation Harness

当前是 RAG retrieval prototype，不是完整 production RAG system。

## 9. Local Development

详细本地开发说明：

[docs/local-dev.md](docs/local-dev.md)

PowerShell 进入项目目录：

```powershell
cd E:\code\ai-task-orchestrator
```

PowerShell 中不要使用：

```powershell
cd /d E:\code\ai-task-orchestrator
```

启动本地基础设施：

```powershell
docker compose up -d
docker compose ps
```

运行测试：

```powershell
.\mvnw.cmd test
```

使用 docker profile 启动 Spring Boot：

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=docker"
```

RabbitMQ 管理页面：

- URL: <http://localhost:15672>
- username: `guest`
- password: `guest`

## 10. CI & Tests

CI 使用 GitHub Actions。

触发时机：

- `push`
- `pull_request`

CI 行为：

- checkout 代码；
- 设置 Java 21；
- 运行 Maven test。

本地测试命令：

```powershell
.\mvnw.cmd test
```

如果后续要在 README 中写测试数量，只能根据最新本地或 CI 输出更新，不要手写猜测数字。

## 11. Current Limitations

当前项目边界：

- 当前使用 `MockLlmClient`，不是真实 LLM。
- 当前使用 `MockEmbeddingClient`，不代表真实语义效果。
- `embedding_vector` 使用 MySQL `TEXT`，不适合生产级向量检索。
- search 是 Java in-memory exact scan，不适合大规模。
- 没有 Vector DB。
- 没有 ANN search。
- 没有 RAG Answer。
- 没有 Citation。
- 没有 Retrieval Evaluation。
- 没有 Rerank。
- 没有 Hybrid Search。
- 还不是完整 Agent Runtime。
- 还不是完整 production-grade orchestration platform。

## 12. Roadmap

后续路线：

- V2.2.6 Task Attempt Read API & Dev Status Endpoint
  - `GET /tasks/{taskId}/attempts`
  - `PATCH /tasks/{taskId}/status` 迁移到 `/dev/tasks/{taskId}/status`
- V2.2.7 Outbox Boundary & Minimal Metrics Polish
  - claim / send / mark result 事务边界拆分
  - 最小 metrics
- V2.3 RAG Answer with Citation
- V2.4 Chunking Evaluation
- V2.5 Real Embedding Provider
- V2.6 Vector DB Selection
- V2.7 Retrieval Policy & VIP Search
- Agent Runtime
- KV Cache-aware Scheduling

## 13. Interview Docs

面试 deep-dive 文档：

- [V2.2 Mock Embedding & Vector Search](docs/interview/V2.2-mock-embedding-vector-search.md)
- [V2.2.x Production Hardening](docs/interview/V2.2.x-production-hardening.md)

更多分版本面试文档见：

[docs/interview](docs/interview)

