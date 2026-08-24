ALTER TABLE export_task
    ADD COLUMN current_run_id BIGINT NULL AFTER auto_attempt_count,
    ADD COLUMN current_run_no INT NOT NULL DEFAULT 0 AFTER current_run_id,
    ADD COLUMN manual_retry_count INT NOT NULL DEFAULT 0 AFTER current_run_no,
    ADD COLUMN manual_retry_limit INT NOT NULL DEFAULT 2 AFTER manual_retry_count,
    ADD COLUMN archived BOOLEAN NOT NULL DEFAULT FALSE AFTER manual_retry_limit;

CREATE TABLE export_task_run (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    run_no INT NOT NULL,
    trigger_type VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    stage VARCHAR(32) NOT NULL,
    expected_count BIGINT NOT NULL DEFAULT 0,
    exported_count BIGINT NOT NULL DEFAULT 0,
    progress INT NOT NULL DEFAULT 0,
    file_name VARCHAR(255) NULL,
    file_path VARCHAR(1024) NULL,
    file_size BIGINT NULL,
    file_expire_at DATETIME NULL,
    auto_attempt_count INT NOT NULL DEFAULT 0,
    failure_code VARCHAR(64) NULL,
    failure_message VARCHAR(1000) NULL,
    retryable BOOLEAN NOT NULL DEFAULT FALSE,
    worker_id VARCHAR(128) NULL,
    execution_token VARCHAR(64) NULL,
    heartbeat_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    started_at DATETIME NULL,
    completed_at DATETIME NULL,
    last_downloaded_at DATETIME NULL,
    download_count INT NOT NULL DEFAULT 0,
    legacy_task_id BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_export_run_task_no (task_id, run_no),
    UNIQUE KEY uk_export_run_idempotency (idempotency_key),
    UNIQUE KEY uk_export_run_legacy_task (legacy_task_id),
    KEY idx_export_run_task_created (task_id, created_at),
    KEY idx_export_run_status_heartbeat (status, heartbeat_at),
    CONSTRAINT fk_export_run_task FOREIGN KEY (task_id) REFERENCES export_task(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO export_task_run (
    task_id, run_no, trigger_type, idempotency_key, request_hash, status, stage,
    expected_count, exported_count, progress, file_name, file_path, file_size, file_expire_at,
    auto_attempt_count, failure_code, failure_message, retryable, worker_id, execution_token,
    heartbeat_at, created_at, updated_at, started_at, completed_at, last_downloaded_at,
    download_count, legacy_task_id
)
SELECT
    COALESCE(root_task_id, id), manual_retry_index,
    CASE WHEN manual_retry_index = 0 THEN 'INITIAL' ELSE 'MANUAL_RETRY' END,
    idempotency_key, request_hash, status, stage,
    expected_count, exported_count, progress, file_name, file_path, file_size, file_expire_at,
    auto_attempt_count, failure_code, failure_message, retryable, worker_id, execution_token,
    heartbeat_at, created_at, updated_at, started_at, completed_at, last_downloaded_at,
    download_count, id
FROM export_task;

ALTER TABLE export_task_attempt
    ADD COLUMN run_id BIGINT NULL AFTER task_id;

ALTER TABLE export_task_attempt
    DROP INDEX uk_export_attempt_task_no;

UPDATE export_task_attempt attempt_record
JOIN export_task_run task_run ON task_run.legacy_task_id = attempt_record.task_id
SET attempt_record.run_id = task_run.id,
    attempt_record.task_id = task_run.task_id;

ALTER TABLE export_task_attempt
    MODIFY COLUMN run_id BIGINT NOT NULL,
    ADD UNIQUE KEY uk_export_attempt_run_no (run_id, attempt_no),
    ADD KEY idx_export_attempt_run_started (run_id, started_at),
    ADD CONSTRAINT fk_export_attempt_run FOREIGN KEY (run_id) REFERENCES export_task_run(id);

UPDATE export_task logical_task
JOIN (
    SELECT task_id, MAX(run_no) AS latest_run_no
    FROM export_task_run
    GROUP BY task_id
) latest ON latest.task_id = logical_task.id
JOIN export_task_run current_run
    ON current_run.task_id = latest.task_id AND current_run.run_no = latest.latest_run_no
SET logical_task.current_run_id = current_run.id,
    logical_task.current_run_no = current_run.run_no,
    logical_task.manual_retry_count = current_run.run_no,
    logical_task.status = current_run.status,
    logical_task.stage = current_run.stage,
    logical_task.expected_count = current_run.expected_count,
    logical_task.exported_count = current_run.exported_count,
    logical_task.progress = current_run.progress,
    logical_task.file_name = current_run.file_name,
    logical_task.file_path = current_run.file_path,
    logical_task.file_size = current_run.file_size,
    logical_task.file_expire_at = current_run.file_expire_at,
    logical_task.auto_attempt_count = current_run.auto_attempt_count,
    logical_task.failure_code = current_run.failure_code,
    logical_task.failure_message = current_run.failure_message,
    logical_task.retryable = current_run.retryable,
    logical_task.worker_id = current_run.worker_id,
    logical_task.execution_token = current_run.execution_token,
    logical_task.heartbeat_at = current_run.heartbeat_at,
    logical_task.updated_at = current_run.updated_at,
    logical_task.started_at = current_run.started_at,
    logical_task.completed_at = current_run.completed_at,
    logical_task.last_downloaded_at = current_run.last_downloaded_at,
    logical_task.download_count = current_run.download_count,
    logical_task.manual_retry_index = current_run.run_no;

UPDATE export_task
SET archived = TRUE
WHERE root_task_id IS NOT NULL;

UPDATE outbox_event event_record
JOIN export_task archived_task ON archived_task.id = event_record.aggregate_id AND archived_task.archived = TRUE
SET event_record.status = 'PUBLISHED',
    event_record.published_at = COALESCE(event_record.published_at, CURRENT_TIMESTAMP),
    event_record.last_error = 'Archived during task-run migration'
WHERE event_record.status = 'PENDING';

INSERT INTO outbox_event (
    event_id, aggregate_id, event_type, payload, status, publish_attempts,
    next_retry_at, published_at, created_at, last_error
)
SELECT
    UUID(), task.id, 'EXPORT_TASK_RECOVERED_MIGRATION',
    JSON_OBJECT('eventId', UUID(), 'taskId', task.id, 'eventType', 'EXPORT_TASK_RECOVERED_MIGRATION'),
    'PENDING', 0, CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, NULL
FROM export_task task
WHERE task.archived = FALSE
  AND task.status = 'PENDING'
  AND NOT EXISTS (
      SELECT 1 FROM outbox_event pending_event
      WHERE pending_event.aggregate_id = task.id AND pending_event.status = 'PENDING'
  );

ALTER TABLE export_task
    ADD KEY idx_export_task_visible_created (archived, created_at, id),
    ADD CONSTRAINT fk_export_task_current_run FOREIGN KEY (current_run_id) REFERENCES export_task_run(id);
