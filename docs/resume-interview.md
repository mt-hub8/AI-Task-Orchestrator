# 简历与面试表达文档

## 一、文档目的

本文档用于整理 AI Task Orchestrator 当前阶段的简历表达、面试表达、技术亮点和后续扩展方向，帮助开发者在简历和面试中清楚、诚实地介绍这个项目。

## 二、项目一句话描述

AI Task Orchestrator 是一个基于 Spring Boot、RabbitMQ、MySQL 和 Flyway 构建的异步 AI 任务编排系统，当前阶段重点实现任务生命周期管理、异步调度、状态机、事件追踪、失败处理、重试、幂等、取消、超时和本地环境工程化，为后续接入 LLM / RAG / Agent Runtime 打基础。

当前项目尚未接入真实 LLM、RAG 或 Agent Runtime。

## 三、简历版本描述

### 1. 一句话版本

基于 Spring Boot、RabbitMQ、MySQL 和 Flyway 实现异步 AI 任务编排系统，支持任务状态机、事件追踪、失败重试、幂等控制、取消、超时和本地 Docker Compose 开发环境。

### 2. 三行版本

AI Task Orchestrator 是一个模拟 AI 长任务平台的异步任务编排系统。  
项目使用 Spring Boot、RabbitMQ、MySQL、Flyway 实现任务创建、异步执行、状态流转、事件记录、失败重试、幂等、取消和超时处理。  
当前重点是构建可靠任务调度底座，为后续接入 LLM、RAG、Tool Calling 和 Agent Runtime 做工程准备。

### 3. 完整项目经历版本

项目背景：  
面向 AI Agent / LLM 类长耗时任务场景，设计并实现一个异步任务编排系统，用于解决 HTTP 请求阻塞、任务状态不可追踪、失败不可恢复、重复消息导致脏状态、任务无法取消和执行超时等问题。

技术栈：  
Java、Spring Boot、Spring Web、Spring Data JPA、MySQL、Flyway、RabbitMQ、Docker Compose、Maven Wrapper、Lombok。

本人工作：  
负责从零搭建任务生命周期模型，设计任务状态机和 task_event 事件表；引入 RabbitMQ 实现任务创建与执行解耦；实现 Consumer 模拟执行、失败处理、错误记录、重试等待、Scheduler 到期重新投递、Consumer 入口幂等保护、协作式取消、超时扫描，以及本地 Docker Compose 开发环境和项目文档。

核心成果：  
完成从任务创建、异步调度、执行、成功、失败、重试、取消到超时处理的一套闭环流程，并通过文档沉淀 API 验收方式、项目结构和本地启动说明。

工程亮点：  
项目重点体现可靠任务系统能力，包括状态机约束、事件追踪、异步解耦、失败恢复、幂等控制、轻量防重复投递、协作式取消、超时治理、Flyway 数据库版本管理和本地环境工程化。

## 四、简历 bullet 示例

- 基于 Spring Boot 和 RabbitMQ 实现异步任务调度，将任务创建请求与后台执行流程解耦，避免长耗时任务阻塞 HTTP 请求。
- 设计任务状态机，约束 `PENDING`、`RUNNING`、`RETRY_PENDING`、`SUCCESS`、`FAILED`、`CANCELLED` 等状态流转，防止非法状态变更。
- 设计 `task_event` 事件表记录任务生命周期变化，为任务审计、排错和后续执行链路追踪提供基础。
- 使用 Flyway 管理数据库结构演进，完成任务表、事件表、失败原因、重试字段和超时字段的版本化迁移。
- 实现失败处理和重试机制，基于 `retryCount`、`maxRetry`、`nextRetryAt` 和 `RetryScheduler` 支持失败任务到期重新投递。
- 实现 Consumer 入口幂等保护，确保重复 MQ 消息不会让终态任务重新进入执行流程。
- 实现任务取消和超时处理，支持等待中任务取消、`RUNNING` 任务协作式取消，以及超时任务扫描失败。
- 使用 Docker Compose 搭建本地 MySQL / RabbitMQ 开发环境，并编写 README、API 验收和项目结构说明文档。

## 五、面试开场怎么讲

这个项目是我为了模拟 AI Agent / LLM 平台中的长耗时任务处理场景做的一个异步任务编排系统。因为真实 AI 任务通常不能在 HTTP 请求里同步执行，而且需要状态追踪、失败恢复、重复消息幂等、取消和超时控制，所以我先从任务系统底座做起。技术上使用 Spring Boot 提供 HTTP API，用 MySQL 保存 task 和 task_event，用 RabbitMQ 解耦任务创建和执行，用 Flyway 管理数据库迁移。当前已经实现任务创建、查询、状态机、异步调度、Consumer 模拟执行、失败重试、幂等、取消、超时和本地 Docker Compose 环境。后续计划是在这套任务编排底座上接入 LLM Client、Prompt Template、RAG、Tool Calling 和 Agent Runtime。

## 六、面试深入讲解：系统架构

整体链路可以这样讲：

```text
用户请求
-> TaskController
-> TaskService
-> MySQL task / task_event
-> RabbitMQ Producer
-> RabbitMQ Queue
-> Consumer
-> TaskExecutionService
-> 状态流转
-> RetryScheduler / TimeoutScheduler
```

用户通过 HTTP 创建任务，`TaskController` 接收请求后调用 `TaskService`。`TaskService` 负责创建 `task` 记录、写入 `task_event`，并通过 Producer 发送 RabbitMQ 消息。Consumer 收到消息后调用 `TaskExecutionService`，先尝试把任务从 `PENDING` 或 `RETRY_PENDING` 变为 `RUNNING`，再进行模拟执行。执行成功写入 `SUCCESS`，失败时根据重试次数进入 `RETRY_PENDING` 或 `FAILED`。后台 Scheduler 负责扫描到期重试任务和超时任务。

## 七、面试深入讲解：为什么要用 RabbitMQ

- HTTP 请求不应该阻塞长耗时任务。
- MQ 可以解耦任务创建和任务执行。
- MQ 支持异步消费，让后台 Worker 独立处理任务。
- 后续可以扩展多个 Worker，提高执行能力。
- 为后续 LLM / RAG / Agent 长任务执行打基础。

## 八、面试深入讲解：为什么要做状态机

状态机的价值是把任务生命周期显式建模，防止业务代码随意改状态。

- 防止非法状态流转。
- 明确任务生命周期。
- 让失败、重试、取消、超时都有统一约束。
- `SUCCESS` / `FAILED` / `CANCELLED` 是终态。
- 防止重复消息导致 `SUCCESS -> RUNNING` 等脏状态。

## 九、面试深入讲解：为什么要做 task_event

`task` 表保存当前状态，适合快速查询任务当前结果；`task_event` 表保存历史过程，适合审计、排错和复盘。

例如一个失败重试任务，`task` 最终只显示 `FAILED`，但 `task_event` 可以看到它经历过 `PENDING -> RUNNING`、`RUNNING -> RETRY_PENDING`、`RETRY_PENDING -> RUNNING`、`RUNNING -> FAILED`。后续这套事件记录可以扩展成 Agent Trace / Execution Trace。

## 十、面试深入讲解：重试机制怎么设计

当前重试机制包含：

- `retryCount`：当前已重试次数。
- `maxRetry`：最大重试次数。
- `nextRetryAt`：下一次允许重试时间。
- `RETRY_PENDING`：失败后等待重试的中间状态。
- `RetryScheduler`：扫描到期的 `RETRY_PENDING` 任务。

任务执行失败后，如果 `retryCount < maxRetry`，会进入 `RETRY_PENDING`，并设置下一次重试时间。`RetryScheduler` 到期后重新投递 MQ，Consumer 再次消费并尝试执行。重试耗尽后任务进入 `FAILED`。

当前是本地单实例轻量实现，生产级还需要分布式锁、幂等消费、Dead Letter Queue、Outbox Pattern 等增强。

## 十一、面试深入讲解：幂等怎么做

当前实现：

- Consumer 收到消息后先调用 `tryStartTaskExecution`。
- 只有 `PENDING` / `RETRY_PENDING` 可以进入 `RUNNING`。
- `RUNNING` / `SUCCESS` / `FAILED` / `CANCELLED` 收到重复消息会被忽略。
- `DevTaskDispatchController` 用于本地模拟重复投递。
- Scheduler 投递后推迟 `nextRetryAt`，降低短时间重复投递。

当前边界：

- 还没有数据库乐观锁 `version`。
- 还没有 Redis 分布式锁。
- 还没有 Outbox。
- 还没有 RabbitMQ ack/nack 深度治理。

## 十二、面试深入讲解：取消和超时怎么做

取消方面：

- `PENDING` / `RETRY_PENDING` 可以直接取消。
- `RUNNING` 使用协作式取消。
- 执行过程中定期检查任务是否已经变为 `CANCELLED`。
- 如果发现已取消，执行线程直接返回，不再写入 `SUCCESS` / `RETRY_PENDING` / `FAILED`。

超时方面：

- `timeoutSeconds` 表示允许执行的秒数。
- `timeoutAt` 表示任务超时时间点。
- `TimeoutScheduler` 扫描 `RUNNING` 且 `timeoutAt` 到期的任务。
- 超时后标记为 `FAILED`，并记录错误原因。
- `TaskExecutionService` 在写成功或失败前会避免覆盖已经 `CANCELLED` / `FAILED` 的终态。

## 十三、项目技术亮点

- 任务生命周期建模。
- 状态机约束。
- 事件溯源雏形。
- RabbitMQ 异步调度。
- 失败处理。
- 自动重试。
- Consumer 入口幂等保护。
- Scheduler 轻量防重复投递。
- 协作式取消。
- 超时扫描。
- Flyway schema migration。
- Docker Compose 本地环境。
- 文档化工程习惯。

## 十四、当前项目边界

当前已实现的是 AI 任务平台的可靠任务编排底座。

当前尚未实现：

- 真实 LLM Client。
- Prompt Template。
- Token Usage。
- Streaming Output。
- RAG。
- Tool Calling。
- Agent Runtime。
- Evaluation Harness。
- KV Cache-aware Scheduling。
- 多租户。
- 权限系统。
- 生产级监控。
- 分布式锁。
- Outbox Pattern。
- Dead Letter Queue。

## 十五、如何诚实回答“这是 AI 项目吗？”

这个项目当前还不是完整的 LLM 应用或 Agent 平台，而是先实现 AI 任务平台底层最重要的任务编排能力。因为真实 AI 任务通常具有长耗时、易失败、需要重试、需要状态追踪、需要取消和超时控制等特点，所以我先从可靠任务系统做起。后续接入 LLM、RAG、Tool Calling、Agent Runtime 时，可以复用这套异步调度和状态管理底座。

## 十六、后续扩展路线

下一阶段：

V1.0 LLM Client 抽象

- 定义统一 `LLMClient` 接口。
- 支持 mock provider。
- 后续再接真实 OpenAI / Claude / 本地模型。
- 记录模型名、输入输出、错误信息。

后续再扩展：

- Prompt Template。
- Token Usage。
- Streaming Output。
- RAG。
- Tool Calling。
- Agent Runtime。
- Evaluation Harness。
- KV Cache-aware Scheduling。

## 十七、面试可能被问的问题

1. 为什么不用线程直接执行？  
   回答方向：HTTP 请求线程不适合承载长耗时任务；直接在线程里执行会影响接口响应和服务稳定性，MQ 可以解耦创建和执行。

2. 为什么要引入 RabbitMQ？  
   回答方向：用于异步调度、削峰解耦、支持后台 Worker 消费，并为后续多 Worker 扩展打基础。

3. 状态机解决了什么问题？  
   回答方向：限制合法状态流转，避免终态任务被重复执行，保证失败、重试、取消、超时都在统一规则内。

4. task_event 有什么价值？  
   回答方向：task 表记录当前状态，task_event 记录历史过程，便于审计、排错和后续扩展执行 Trace。

5. 重试机制如何避免无限重试？  
   回答方向：通过 `retryCount` 和 `maxRetry` 控制最大次数，通过 `nextRetryAt` 控制下次重试时间，耗尽后进入 `FAILED`。

6. 幂等怎么保证？  
   回答方向：Consumer 入口调用 `tryStartTaskExecution`，只有 `PENDING` / `RETRY_PENDING` 可以进入 `RUNNING`，终态和运行中任务收到重复消息会被忽略。

7. 取消 RUNNING 任务为什么不能直接 kill 线程？  
   回答方向：强杀线程容易造成资源不一致和状态不可控，所以当前使用协作式取消，让执行流程定期检查取消状态并安全退出。

8. 超时后如何避免执行线程覆盖状态？  
   回答方向：TimeoutScheduler 先把超时任务标记为 `FAILED`，执行线程在写 `SUCCESS` 或失败状态前检查任务是否仍然是 `RUNNING`。

9. 这个项目和真实 AI Agent 平台有什么关系？  
   回答方向：它不是完整 Agent 平台，而是 Agent / LLM 长任务平台需要的任务编排底座，后续可以接入 LLM、RAG、Tool Calling 和 Agent Runtime。

10. 如果要生产化，还缺什么？  
    回答方向：分布式锁、数据库乐观锁、Outbox、DLQ、ack/nack 治理、监控告警、权限、多租户、部署容器化和更完整的测试体系。

## 十八、相关文档

- [README.md](../README.md)
- [docs/local-dev.md](local-dev.md)
- [docs/project-structure.md](project-structure.md)
- [docs/api-examples.md](api-examples.md)
