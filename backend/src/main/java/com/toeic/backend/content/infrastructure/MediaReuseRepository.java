package com.toeic.backend.content.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MediaReuseRepository {
    public record Media(UUID mediaId, UUID ownerId, String visibility) { }
    private final JdbcTemplate jdbc;
    public MediaReuseRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<Media> lockMedia(UUID id) {
        return jdbc.query("select resource_id,owner_id,visibility from media_resources where resource_id=? for share",
                (row,n) -> new Media(row.getObject("resource_id",UUID.class),row.getObject("owner_id",UUID.class),
                        row.getString("visibility")),id).stream().findFirst();
    }

    public boolean lockActiveGrant(UUID id, UUID actorId) {
        // Lock even a revoked row; do not use a cached permission or infer ownership from a content link.
        return jdbc.query("select revoked_at is null as active from media_reuse_grants where media_id=? and user_id=? for share",
                (row,n) -> row.getBoolean("active"),id,actorId).stream().findFirst().orElse(false);
    }
}
