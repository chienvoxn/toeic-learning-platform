package com.toeic.backend.vocabulary.infrastructure;

import com.toeic.backend.vocabulary.domain.VocabularyCatalogEnrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface VocabularyCatalogEnrollmentRepository
        extends JpaRepository<VocabularyCatalogEnrollment, UUID> {

    Optional<VocabularyCatalogEnrollment>
        findByUserIdAndSystemDeckId(UUID userId, UUID systemDeckId);
}
