CREATE TABLE media_cleanup_jobs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    object_key VARCHAR(512) NOT NULL,
    reason VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempt_count INT NOT NULL,
    next_attempt_at TIMESTAMP(6) NULL,
    last_attempt_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_media_cleanup_jobs PRIMARY KEY (id),
    CONSTRAINT ck_media_cleanup_reason CHECK
        (reason IN ('REPLACED', 'REMOVED', 'RECORD_DELETED')),
    CONSTRAINT ck_media_cleanup_status CHECK (status IN ('PENDING', 'FAILED')),
    CONSTRAINT ck_media_cleanup_attempt_count CHECK (attempt_count BETWEEN 0 AND 8),
    INDEX ix_media_cleanup_due (status, next_attempt_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
