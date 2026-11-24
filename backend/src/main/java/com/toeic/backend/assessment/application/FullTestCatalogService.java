package com.toeic.backend.assessment.application;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Giữ DTO catalog của UI nhóm; nguồn và điều kiện cung cấp lấy từ engine version/blueprint chung. */
@Service
public class FullTestCatalogService {
    private final FullMockSessionService sessions;
    public FullTestCatalogService(FullMockSessionService sessions) { this.sessions=sessions; }
    public record PublishedFullTest(UUID testId,UUID testVersionId,String title,String description,
            int durationSeconds,int totalQuestions,OffsetDateTime publishedAt,String blueprintCode,Integer blueprintVersion) { }
    public List<PublishedFullTest> listPublished(UUID actorId) {
        return sessions.catalog(actorId).stream().map(r->new PublishedFullTest((UUID)r.get("testId"),(UUID)r.get("versionId"),
                (String)r.get("title"),(String)r.get("description"),((Number)r.get("durationSeconds")).intValue(),
                ((Number)r.get("totalQuestions")).intValue(),(OffsetDateTime)r.get("publishedAt"),(String)r.get("blueprintCode"),
                ((Number)r.get("blueprintVersion")).intValue())).toList();
    }
}
