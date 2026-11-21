package com.toeic.interaction;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
interface ContentReviewRepository extends JpaRepository<ContentReview,UUID>{
 Optional<ContentReview> findByUserIdAndContentId(UUID userId,UUID contentId);
 List<ContentReview> findTop50ByContentIdOrderByCreatedAtDesc(UUID contentId);
}
