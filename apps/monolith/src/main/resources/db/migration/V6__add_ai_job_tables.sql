CREATE TABLE equipment_draft_job (
    job_id VARCHAR(36) NOT NULL,
    owner_id BIGINT NOT NULL,
    request_json TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (job_id),
    INDEX idx_draft_owner_created (owner_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE condition_analysis_job (
    rental_id BIGINT NOT NULL,
    job_id VARCHAR(36) NOT NULL,
    owner_id BIGINT NOT NULL,
    request_json TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (rental_id),
    UNIQUE KEY uk_condition_analysis_job_id (job_id),
    INDEX idx_condition_ai_owner_created (owner_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE report_analysis_job (
    report_id BIGINT NOT NULL,
    job_id VARCHAR(36) NOT NULL,
    admin_id BIGINT NOT NULL,
    request_json TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (report_id),
    UNIQUE KEY uk_report_analysis_job_id (job_id),
    INDEX idx_report_ai_admin_created (admin_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
