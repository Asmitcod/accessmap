ALTER TABLE app_users
    ADD COLUMN is_moderator BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE reports
    ADD COLUMN review_status VARCHAR(16) NOT NULL DEFAULT 'APPROVED';

CREATE INDEX idx_reports_review_status_reported_at
    ON reports(review_status, reported_at);
