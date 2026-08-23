DROP TABLE IF EXISTS export_task_attempt;

CREATE TABLE export_task_attempt (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    attempt_no INT NOT NULL,
    execution_token VARCHAR(64) NOT NULL,
    worker_id VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    started_at TIMESTAMP NOT NULL,
    heartbeat_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP NULL,
    failure_code VARCHAR(64) NULL,
    failure_message VARCHAR(1000) NULL
);
