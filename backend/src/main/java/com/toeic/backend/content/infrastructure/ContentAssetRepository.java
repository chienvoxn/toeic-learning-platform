package com.toeic.backend.content.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Link writes only after the application locks the parent/version and authorizes the entire final set. */
@Repository
public class ContentAssetRepository {
    public record Link(UUID mediaId, int displayOrder) { }
    private final JdbcTemplate jdbc;
    public ContentAssetRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Link> findAll(UUID versionId) {
        return jdbc.query("select resource_id,display_order from learning_content_version_resources where content_version_id=? order by display_order,resource_id",
                (row,n) -> new Link(row.getObject("resource_id",UUID.class),row.getInt("display_order")),versionId);
    }

    public void replace(UUID versionId, List<Link> links) {
        jdbc.update("delete from learning_content_version_resources where content_version_id=?",versionId);
        jdbc.batchUpdate("insert into learning_content_version_resources(content_version_id,resource_id,display_order) values (?,?,?)",
                links, Math.max(1,links.size()), (statement,link) -> {
                    statement.setObject(1,versionId);
                    statement.setObject(2,link.mediaId());
                    statement.setInt(3,link.displayOrder());
                });
    }
}
