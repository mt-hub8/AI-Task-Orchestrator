# 本地开发环境

本文说明 `ai-task-orchestrator` 在本地开发时如何使用 Docker Compose 启动 MySQL / RabbitMQ，并用 `docker` profile 启动 Spring Boot 应用。

## 前置要求

- 已安装 JDK 21 或更高版本
- 已安装 Docker Desktop
- 已安装 Maven Wrapper 所需环境
- 当前目录为项目根目录：`ai-task-orchestrator`

Spring Boot 应用仍然在本机 IDEA 或命令行启动，不放进 Docker 容器。

## 启动 MySQL / RabbitMQ

项目根目录已提供 `docker-compose.yml`。

启动基础环境：

```powershell
docker compose up -d
```

查看容器状态：

```powershell
docker compose ps
```

停止基础环境：

```powershell
docker compose down
```

当前 Compose 会启动：

| 服务 | 镜像 | 本机端口 | 容器端口 |
| --- | --- | --- | --- |
| MySQL | `mysql:8.0` | `3307` | `3306` |
| RabbitMQ | `rabbitmq:3-management` | `5672`, `15672` | `5672`, `15672` |

MySQL 默认信息：

```text
host: localhost
port: 3307
database: ai_task_orchestrator
username: root
password: 123456
```

RabbitMQ 默认信息：

```text
host: localhost
amqp port: 5672
management ui: http://localhost:15672
username: guest
password: guest
```

## 启动 Spring Boot

使用 Docker Compose 环境时，需要启用 `docker` profile。

在 IDEA 中启动：

```text
Active profiles: docker
```

用命令行启动：

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=docker"
```

`docker` profile 会读取：

```text
src/main/resources/application-docker.properties
```

该配置会连接：

- MySQL: `localhost:3307/ai_task_orchestrator`
- RabbitMQ: `localhost:5672`

## 验收 MySQL

确认 MySQL 容器运行：

```powershell
docker compose ps
```

也可以连接数据库：

```powershell
docker exec -it ai-task-mysql mysql -uroot -p123456 ai_task_orchestrator
```

进入 MySQL 后查看表：

```sql
SHOW TABLES;
```

如果 Spring Boot 已经启动成功，Flyway 应该已经创建业务表和 `flyway_schema_history`。

## 验收 RabbitMQ

打开 RabbitMQ 管理页面：

```text
http://localhost:15672
```

登录：

```text
username: guest
password: guest
```

可以检查：

- `Queues and Streams` 中是否有任务队列
- `Exchanges` 中是否有任务交换机
- 应用创建任务后队列消息是否被消费

## 验收 Flyway

Spring Boot 使用 `docker` profile 启动后，查看启动日志中是否出现类似内容：

```text
Successfully validated ... migrations
Schema `ai_task_orchestrator` is up to date
```

也可以在 MySQL 中查看：

```sql
SELECT version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

期望所有迁移脚本 `success = 1`。

## 验收 API

启动 Spring Boot 后，创建一个正常任务：

```http
POST http://localhost:8080/tasks
Content-Type: application/json
```

```json
{
  "prompt": "normal task"
}
```

创建后会返回 `taskId`。

查询任务：

```http
GET http://localhost:8080/tasks/{taskId}
```

正常情况下，任务会经历：

```text
PENDING -> RUNNING -> SUCCESS
```

如果需要验证失败重试，可以创建包含 `fail` 的任务：

```json
{
  "prompt": "please fail this task"
}
```

## 常见问题

### 端口冲突

MySQL 使用本机 `3307`，避免和本机已有 MySQL 的 `3306` 冲突。

如果 `3307` 仍然被占用，可以修改 `docker-compose.yml`：

```yaml
ports:
  - "3308:3306"
```

同时修改 `application-docker.properties` 中的 JDBC 端口。

RabbitMQ 使用：

```text
5672
15672
```

如果端口被占用，需要先关闭占用端口的本机服务，或调整 Compose 端口映射。

### MySQL 连接失败

先确认容器状态：

```powershell
docker compose ps
```

查看 MySQL 日志：

```powershell
docker logs ai-task-mysql
```

常见原因：

- MySQL 容器还没完全启动
- `application-docker.properties` 没有启用
- JDBC 端口仍然指向 `3306`
- 数据库名不是 `ai_task_orchestrator`

确认 Spring Boot 启动时使用了：

```text
docker
```

profile。

### RabbitMQ 端口占用

如果 RabbitMQ 启动失败，先查看端口是否被占用：

```powershell
netstat -ano | findstr :5672
netstat -ano | findstr :15672
```

查看 RabbitMQ 日志：

```powershell
docker logs ai-task-rabbitmq
```

如果本机已经运行了 RabbitMQ，可以关闭本机 RabbitMQ，或修改 Compose 端口映射。

### Flyway 校验失败

如果 Flyway 报错，先确认数据库是否是新的 `ai_task_orchestrator`，并查看：

```sql
SELECT * FROM flyway_schema_history;
```

本地开发环境如果需要重建数据库，可以停止并删除 volume：

```powershell
docker compose down -v
docker compose up -d
```

注意：`docker compose down -v` 会删除 Compose 创建的数据卷，本地数据库数据会被清空。
