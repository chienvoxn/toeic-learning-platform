-- CONTENT-02: explicit access history, independent of learning_progress/completion.
CREATE TABLE learning_content_history (
    user_id UUID NOT NULL REFERENCES users(user_id),
    content_id UUID NOT NULL REFERENCES learning_contents(content_id),
    content_version_id UUID NOT NULL,
    last_accessed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, content_id),
    CONSTRAINT fk_content_history_matching_version
        FOREIGN KEY (content_id, content_version_id)
        REFERENCES learning_content_versions(content_id, content_version_id)
);

CREATE INDEX idx_content_history_user_time
    ON learning_content_history (user_id, last_accessed_at DESC, content_id);
CREATE INDEX idx_content_history_version ON learning_content_history (content_version_id);
CREATE INDEX idx_bookmarks_user_time ON bookmarks (user_id, created_at DESC, content_id);

INSERT INTO permissions (code, description) VALUES
    ('content:bookmark:self', 'Quan ly bai hoc da luu cua chinh minh'),
    ('content:history:self', 'Ghi va doc lich su truy cap hoc lieu cua chinh minh')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'LEARNER' AND p.code IN ('content:bookmark:self', 'content:history:self')
ON CONFLICT (role_id, permission_id) DO NOTHING;
