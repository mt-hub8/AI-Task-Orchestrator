# AI-Task-Orchestrator

A Spring Boot based AI task orchestration system.

## Current Version

V0.6 - Async task dispatch and simulated execution with RabbitMQ.

## What It Does

AI-Task-Orchestrator provides a basic backend workflow for creating, tracking, dispatching, and executing AI tasks.

The current workflow is:

1. A client creates a task through `POST /tasks`.
2. The task is saved to MySQL with status `PENDING`.
3. A task creation event is recorded in `task_event`.
4. The task ID is sent to RabbitMQ.
5. A consumer receives the message and simulates task execution.
6. The task status changes from `PENDING` to `RUNNING`, then to `SUCCESS`.

## Features

- Create AI tasks.
- Query task details.
- Update task status.
- Validate task status transitions with a state machine.
- Record task lifecycle events.
- Manage database schema with Flyway.
- Dispatch created tasks asynchronously through RabbitMQ.
- Simulate background task execution.

## Tech Stack

- Java 21
- Spring Boot 3.5.16
- Spring Web
- Spring Data JPA
- Bean Validation
- MySQL
- Flyway
- RabbitMQ
- Maven
- Lombok

## Version History

| Version | Description |
| --- | --- |
| V0.1 | Create task API |
| V0.2 | Task query API |
| V0.3 | Task state machine |
| V0.4 | Task event log |
| V0.5 | Flyway database migration |
| V0.6 | RabbitMQ async dispatch and simulated task execution |

## Task Status

Supported task statuses:

- `PENDING`
- `RUNNING`
- `SUCCESS`
- `FAILED`
- `CANCELLED`

Allowed transitions:

| From | To |
| --- | --- |
| `PENDING` | `RUNNING`, `CANCELLED` |
| `RUNNING` | `SUCCESS`, `FAILED`, `CANCELLED` |
| `SUCCESS` | No further transition |
| `FAILED` | No further transition |
| `CANCELLED` | No further transition |

## API

### Create Task

```http
POST /tasks
Content-Type: application/json
```

Request body:

```json
{
  "prompt": "Write a short summary about Spring Boot."
}
```

Response:

```json
{
  "taskId": 1,
  "status": "PENDING"
}
```

### Get Task Detail

```http
GET /tasks/{taskId}
```

Response:

```json
{
  "taskId": 1,
  "prompt": "Write a short summary about Spring Boot.",
  "status": "SUCCESS",
  "createdAt": "2026-06-28T15:00:00",
  "updatedAt": "2026-06-28T15:00:03"
}
```

### Update Task Status

```http
PATCH /tasks/{taskId}/status
Content-Type: application/json
```

Request body:

```json
{
  "status": "FAILED",
  "message": "Task execution failed"
}
```

Response:

```json
{
  "taskId": 1,
  "prompt": "Write a short summary about Spring Boot.",
  "status": "FAILED",
  "createdAt": "2026-06-28T15:00:00",
  "updatedAt": "2026-06-28T15:00:05"
}
```

## Local Development

### Requirements

- JDK 21 or later
- MySQL 8
- RabbitMQ

### Database

The default database configuration is in `src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/ai_task_orchestrator_flyway?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
spring.datasource.username=root
spring.datasource.password=123456
```

Flyway migrations are stored in:

```text
src/main/resources/db/migration
```

Current migration files:

- `V1__create_task_table.sql`
- `V2__create_task_event_table.sql`

### RabbitMQ

Default RabbitMQ configuration:

```properties
spring.rabbitmq.host=localhost
spring.rabbitmq.port=5672
spring.rabbitmq.username=guest
spring.rabbitmq.password=guest
```

The application declares:

- Exchange: `task.exchange`
- Queue: `task.created.queue`
- Routing key: `task.created`

### Run Tests

```bash
./mvnw test
```

On Windows:

```powershell
.\mvnw.cmd test
```

### Run The Application

```bash
./mvnw spring-boot:run
```

On Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

The application starts on:

```text
http://localhost:8080
```

## Project Structure

```text
src/main/java/com/tuoman/ai_task_orchestrator
|-- config        RabbitMQ configuration
|-- controller    REST API controllers
|-- dto           Request and response DTOs
|-- entity        JPA entities
|-- enums         Task status and event type enums
|-- mq            RabbitMQ producer, consumer, and message model
|-- repository    Spring Data JPA repositories
|-- service       Task business logic and execution simulation
`-- state         Task state machine
```

## Next Steps

Planned improvements for the next version:

- Move local credentials to environment variables or profiles.
- Ignore generated build output such as `target/`.
- Add unit tests for the task state machine and task service.
- Add safer transaction handling for database writes and RabbitMQ dispatch.
- Add failure handling for task execution.
