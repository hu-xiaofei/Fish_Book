ALTER TABLE catch_records ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE admin_photo_operations (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    actor_user_id BIGINT NOT NULL,
    owner_user_id BIGINT NOT NULL,
    catch_record_id BIGINT NOT NULL,
    operation VARCHAR(16) NOT NULL,
    previous_version BIGINT NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT ck_admin_photo_operation CHECK (operation IN ('REPLACED', 'REMOVED')),
    INDEX ix_admin_photo_record_time (catch_record_id, occurred_at DESC, id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE media_cleanup_jobs DROP CHECK ck_media_cleanup_reason;
ALTER TABLE media_cleanup_jobs ADD CONSTRAINT ck_media_cleanup_reason CHECK
    (reason IN ('REPLACED', 'REMOVED', 'RECORD_DELETED', 'UPLOAD_ROLLBACK'));
