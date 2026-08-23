# 学习演示手册

以下演示均用于本地开发环境。先启动 MySQL、RabbitMQ、Redis 和后端，再打开订单页与任务页。

## 1. 两种导出与真实进度

1. 在订单页跨两页各勾选一条，点击“导出已选”。
2. 保持在订单页，确认成功通知包含任务编号和“查看任务”。
3. 设置订单状态、金额或创建时间，点击“按条件导出”。
4. 打开任务页，观察 `PENDING → PROCESSING → SUCCESS` 和 5%–95%–99%–100% 的真实阶段进度。
5. 打开详情，查看快照、Attempt、workerId、token 短值和重试链；成功任务可下载。

## 2. 幂等语义

```powershell
$body='{"exportType":"SELECTED","selectedOrderIds":[1,2,3]}'
$headers=@{'Idempotency-Key'='demo-key-1'}
Invoke-RestMethod -Method Post http://localhost:8080/api/export-tasks -Headers $headers -ContentType application/json -Body $body
Invoke-RestMethod -Method Post http://localhost:8080/api/export-tasks -Headers $headers -ContentType application/json -Body $body
```

第二次返回同一个任务并带 `idempotentReplay=true`。保持 Key 不变、修改 ID 列表会返回 409；换一个 Key 即使内容相同也会创建新任务。

## 3. Transactional Outbox

1. 暂停 RabbitMQ：`docker compose pause rabbitmq`。
2. 创建导出任务；HTTP 仍成功返回，任务停留 `PENDING`，任务与 Outbox 已在同一 MySQL 事务提交。
3. 恢复：`docker compose unpause rabbitmq`。
4. Outbox Publisher 收到 Publisher Confirm 后标记事件 `PUBLISHED`，任务随后被消费。

这展示了“数据库已提交但 MQ 短暂不可用”时任务不会丢失。

## 4. 自动重试与手动重试

可临时把导出目录改成不可写路径或在开发分支注入一次 I/O 异常。可重试异常会生成新的 Attempt，并经 15 秒重试队列再次投递，单任务总计最多 3 次。

最终 `FAILED` 后，任务页出现“重试”。点击后创建一条新 Task，旧任务保持失败，新任务复用原逻辑快照和已选 Item；根链最多 2 次手动重试。

## 5. Worker 失联恢复

1. 创建一个足够大的任务（建议 100 万行）。
2. 在 `PROCESSING` 时停止后端进程，不停止 MySQL/RabbitMQ/Redis。
3. 等待超过 60 秒后重新启动后端。
4. 恢复调度器把旧 Attempt 标记为 `LOST`，使旧 token 失效，并以 `RECOVERING` 状态重新投递。

数据库 CAS 保证即使旧 Worker 恢复，也不能覆盖新 Worker 的结果。

## 6. SSE 降级与恢复

在浏览器开发者工具中单独阻断 `/api/export-tasks/events`，保留其他 `/api` 请求。SSE 连续失败 3 次后，任务页标签变为“轮询模式”，列表每 2 秒刷新；30 秒后客户端尝试恢复 SSE，成功后标签回到“SSE 实时”。

对应自动化测试：`pnpm.cmd test --run src/pages/tasks/useTaskEvents.test.tsx`。

## 7. 文件过期清理

开发演示时可把清理间隔改短：

```text
--app.export.cleanup-interval=10s --app.export.file-retention=1m
```

任务成功 1 分钟后，调度器删除正式文件并把任务改为 `EXPIRED`；历史记录仍保留，下载返回 410。不提供用户删除接口，也不支持重新上传。

## 8. 10 万 / 100 万数据

IDEA Program arguments：

```text
--spring.profiles.active=dev --app.seed.orders=1000000
```

生成器按批次补齐数据。100 万行适合观察游标读取、SXSSF 临时文件、JVM 堆、SSE 进度和最终文件大小。
