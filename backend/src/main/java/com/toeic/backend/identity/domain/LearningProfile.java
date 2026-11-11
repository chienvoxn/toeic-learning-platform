package com.toeic.backend.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.DynamicUpdate;

/** Identity chỉ sở hữu mục tiêu; không map/ghi các trường ước lượng của analytics. */
@Entity
@Table(name = "learning_profiles")
@DynamicUpdate
public class LearningProfile {
    @Id
    @Column(name = "profile_id", nullable = false)
    private UUID profileId;

    @Column(name = "user_id", nullable = false, unique = true, updatable = false)
    private UUID userId;

    @Column(name = "target_score")
    private Integer targetScore;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected LearningProfile() { }

    public LearningProfile(UUID userId, Integer targetScore, OffsetDateTime now) {
        if (userId == null) { throw new IllegalArgumentException("User is required"); }
        this.profileId = UUID.randomUUID();
        this.userId = userId;
        updateTargetScore(targetScore, now);
    }

    public void updateTargetScore(Integer score, OffsetDateTime now) {
        if (now == null || (score != null && (score < 10 || score > 990 || score % 5 != 0))) {
            throw new IllegalArgumentException("Invalid target score");
        }
        this.targetScore = score;
        this.updatedAt = now;
    }

    public Integer getTargetScore() { return targetScore; }
}
