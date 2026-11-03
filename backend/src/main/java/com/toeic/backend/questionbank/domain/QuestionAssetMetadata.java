package com.toeic.backend.questionbank.domain;

import java.util.UUID;

/** Metadata nội bộ; không chứa storage URL hoặc transcript có thể tiết lộ nội dung. */
public record QuestionAssetMetadata(UUID resourceId, String resourceType, String mimeType,
                                    Long fileSize, Integer durationSeconds) { }
