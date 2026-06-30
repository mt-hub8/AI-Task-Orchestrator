# 简历与面试表达文档

## 一、文档目的

本文档用于整理 AI Task Orchestrator 当前阶段的简历表达、面试表达、技术亮点和后续扩展方向。

## 二、项目一句话描述

AI Task Orchestrator 是一个基于 Spring Boot、RabbitMQ、MySQL 和 Flyway 构建的异步 AI 任务编排系统，支持任务生命周期管理、状态机、事件追踪、失败重试、幂等、取消、超时、Prompt Template 渲染、Mock LLM 执行和执行结果追踪。

当前已实现 `LlmClient`、`MockLlmClient`、Prompt Template 数据模型、`PromptTemplateRenderer`，并已接入任务执行链路：使用 `default_task_prompt` 渲染 `renderedPrompt`，将其作为 `LlmRequest.prompt`，并保存 `resultContent`、`llmModel`、`renderedPrompt`、`promptTemplateCode`。当前尚未接入真实 OpenAI / Claude / 本地模型 Provider。

## 三、简历版本描述

### 1. 一句话版本

基于 Spring Boot、RabbitMQ、MySQL 和 Flyway 实现异步 AI 任务编排系统，支持任务状态机、事件追踪、失败重试、幂等控制、取消、超时、Prompt Template 渲染、Mock LLM 调用和结果保存。

### 2. 三行版本

AI Task Orchestrator 是一个模拟 AI 长任务平台的异步任务编排系统。  
项目使用 Spring Boot、RabbitMQ、MySQL、Flyway 实现任务创建、异步执行、状态流转、事件记录、失败重试、幂等、取消和超时。  
当前通过 `LlmClient`、`MockLlmClient` 和 Prompt Template 渲染链路，完成从用户输入到最终 Prompt、Mock LLM 执行、结果保存的闭环。

### 3. 完整项目经历版本

项目背景：  
面向 AI Agent / LLM 类长耗时任务场景，设计并实现一个异步任务编排系统，用于解决 HTTP 请求阻塞、状态不可追踪、失败不可恢复、重复消息导致脏状态、任务无法取消、执行超时、Prompt 不可追踪和模型执行结果不可追踪等问题。

技术栈：  
Java、Spring Boot、Spring Web、Spring Data JPA、MySQL、Flyway、RabbitMQ、Mock LLM Client、Docker Compose、Maven Wrapper、Lombok。

本人工作：  
负责任务生命周期模型、状态机、`task_event`、RabbitMQ 异步调度、失败重试、幂等、取消、超时；设计 `LlmClient` 抽象和 `MockLlmClient`；设计 Prompt Template 数据模型和渲染器；将 `default_task_prompt` 接入任务执行链路，并保存 `renderedPrompt` / `promptTemplateCode` / `resultContent` / `llmModel`。

核心成果：  
完成从任务创建、异步调度、Prompt Template 渲染、Mock LLM 调用、成功结果保存、失败重试、取消到超时处理的一套闭环流程。

工程亮点：  
状态机约束、事件追踪、异步解耦、失败恢复、幂等控制、协作式取消、超时治理、LLM 调用抽象、Prompt Template 渲染、Mock Provider、执行结果保存、Flyway 数据库版本管理和本地环境工程化。

## 四、简历 bullet 示例

- 基于 Spring Boot 和 RabbitMQ 实现异步任务调度，将任务创建请求与后台执行流程解耦。
- 设计任务状态机，约束 `PENDING`、`RUNNING`、`RETRY_PENDING`、`SUCCESS`、`FAILED`、`CANCELLED` 等状态流转。
- 设计 `task_event` 事件表记录任务生命周期变化，为审计、排错和执行链路追踪提供基础。
- 使用 Flyway 管理数据库结构演进，覆盖任务表、事件表、失败原因、重试字段、超时字段、LLM 结果字段和 Prompt 渲染字段。
- 实现失败处理和重试机制，基于 `retryCount`、`maxRetry`、`nextRetryAt` 和 `RetryScheduler` 支持失败任务到期重新投递。
- 实现 Consumer 入口幂等保护，确保重复 MQ 消息不会让终态任务重新进入执行流程。
- 设计 `LlmClient` 抽象与 `MockLlmClient`，实现任务执行链路与 LLM 调用解耦。
- 设计 Prompt Template 数据模型与渲染器，将 `default_task_prompt` 接入执行链路，并保存实际使用的 `renderedPrompt` 和 `promptTemplateCode`。

## 五、面试开场怎么讲

这个项目是我为了模拟 AI Agent / LLM 平台中的长耗时任务处理场景做的异步任务编排系统。它用 Spring Boot 提供 HTTP API，用 MySQL 保存 task、task_event 和 prompt_template，用 RabbitMQ 解耦任务创建和执行，用 Flyway 管理数据库迁移。当前已经实现任务创建、查询、状态机、异步调度、失败重试、幂等、取消、超时，并通过 `LlmClient`、`MockLlmClient`、Prompt Template 渲染器接入了模拟 LLM 执行链路。任务执行时会用 `default_task_prompt` 渲染最终 Prompt，再把 renderedPrompt 传给 MockLlmClient，成功后保存模型名、输出结果、实际 Prompt 和模板编码。后续计划是实现 Prompt Template CRUD、真实模型 Provider、Token Usage、Streaming、RAG 和 Agent Runtime。

## 六、系统架构怎么讲

```text
用户请求
-> TaskController
-> TaskService
-> MySQL task / task_event / prompt_template
-> RabbitMQ Producer
-> RabbitMQ Queue
-> Consumer
-> TaskExecutionService
-> PromptTemplateRepository
-> PromptTemplateRenderer
-> LlmClient
-> MockLlmClient
-> 保存 resultContent / llmModel / renderedPrompt / promptTemplateCode
-> 状态流转
-> RetryScheduler / TimeoutScheduler
```

## 七、为什么要做 Prompt Template

- 用户原始输入通常不是最终模型 Prompt。
- 模板可以沉淀系统角色、任务说明和固定指令。
- 渲染后的 `renderedPrompt` 可以用于审计和排错。
- 后续接入真实模型、RAG、Tool Calling 和 Agent Runtime 时，可以复用模板层。

当前只实现数据模型、渲染器和执行链路接入，尚未实现 Prompt Template CRUD API。

## 八、为什么要用 RabbitMQ

- HTTP 请求不应该阻塞长耗时任务。
- MQ 解耦任务创建和任务执行。
- MQ 支持异步消费和后续多 Worker 扩展。
- 为后续真实 LLM / RAG / Agent 长任务执行打基础。

## 九、为什么要做状态机

- 防止非法状态流转。
- 明确任务生命周期。
- 让失败、重试、取消、超时都有统一约束。
- `SUCCESS` / `FAILED` / `CANCELLED` 是终态。
- 防止重复消息导致 `SUCCESS -> RUNNING` 等脏状态。

## 十、为什么要做 task_event

`task` 表保存当前状态、错误信息、模型名、执行结果和实际 Prompt；`task_event` 表保存历史过程，适合审计、排错和复盘。后续可以扩展成 Agent Trace / Execution Trace。

## 十一、重试机制怎么设计

任务执行失败后，如果 `retryCount < maxRetry`，进入 `RETRY_PENDING` 并设置 `nextRetryAt`。`RetryScheduler` 到期后重新投递 MQ，Consumer 再次执行。重试耗尽后进入 `FAILED`。

当前是本地单实例轻量实现，生产级还需要分布式锁、幂等消费、Dead Letter Queue、Outbox Pattern 等增强。

## 十二、幂等怎么做

- Consumer 收到消息后先调用 `tryStartTaskExecution`。
- 只有 `PENDING` / `RETRY_PENDING` 可以进入 `RUNNING`。
- `RUNNING` / `SUCCESS` / `FAILED` / `CANCELLED` 收到重复消息会被忽略。
- Scheduler 投递后推迟 `nextRetryAt`，降低短时间重复投递。

## 十三、取消和超时怎么做

- `PENDING` / `RETRY_PENDING` 可以直接取消。
- `RUNNING` 使用协作式取消。
- 执行过程中定期检查是否已 `CANCELLED`。
- `TimeoutScheduler` 扫描超时的 `RUNNING` 任务并标记为 `FAILED`。
- 执行线程在写成功或失败前检查任务是否仍是 `RUNNING`，避免覆盖终态。

## 十四、项目技术亮点

- 任务生命周期建模
- 状态机约束
- 事件溯源雏形
- RabbitMQ 异步调度
- 失败处理和自动重试
- Consumer 入口幂等保护
- 协作式取消
- 超时扫描
- `LlmClient` 调用抽象
- `MockLlmClient` 本地模拟 Provider
- Prompt Template 数据模型
- `PromptTemplateRenderer`
- 保存 `renderedPrompt` / `promptTemplateCode`
- Flyway schema migration
- Docker Compose 本地环境

## 十五、当前项目边界

当前已实现的是 AI 任务平台的可靠任务编排底座和 Mock LLM 执行闭环。

当前尚未实现：

- 真实 OpenAI / Claude / 本地模型 Provider
- Prompt Template CRUD API
- API Key 管理
- Token Usage
- Streaming Output
- RAG
- Tool Calling
- Agent Runtime
- Evaluation Harness
- KV Cache-aware Scheduling
- 多租户
- 权限系统
- 生产级监控
- 分布式锁
- Outbox Pattern
- Dead Letter Queue

## 十六、如何诚实回答“这是 AI 项目吗？”

这个项目当前还不是完整的 LLM 应用或 Agent 平台，但已经实现了 AI 任务平台底层最重要的任务编排能力，并通过 `LlmClient`、`MockLlmClient` 和 Prompt Template 渲染链路模拟了 LLM 执行闭环。它没有调用真实 OpenAI / Claude / 本地模型，但已经具备后续接入真实 Provider 所需的异步调度、状态管理、失败恢复、Prompt 追踪和结果保存基础。

## 十七、后续扩展路线

下一阶段可以选择：

- Prompt Template CRUD API
- 真实 OpenAI / Claude / 本地模型 Provider
- API Key 配置和安全管理
- Token Usage
- Streaming Output
- RAG
- Tool Calling
- Agent Runtime
- Evaluation Harness
- KV Cache-aware Scheduling

## 十八、相关文档

- [README.md](../README.md)
- [docs/local-dev.md](local-dev.md)
- [docs/project-structure.md](project-structure.md)
- [docs/api-examples.md](api-examples.md)
