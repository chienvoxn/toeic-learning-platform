package com.toeic.backend.vocabulary.api;

import java.util.UUID;

public record CatalogEnrollmentResponse(
        UUID systemDeckId,
        UUID personalDeckId,
        boolean alreadyEnrolled,
        int vocabularyCount
) {}
