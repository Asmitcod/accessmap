CREATE INDEX idx_reports_user_feature_reported_at
    ON reports(user_id, feature_id, reported_at DESC);
