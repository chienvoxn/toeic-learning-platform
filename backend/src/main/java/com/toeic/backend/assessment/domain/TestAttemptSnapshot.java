package com.toeic.backend.assessment.domain;

import java.util.UUID;

/** Metadata chỉ đọc được pin lúc start; retire/fork không thay title hoặc blueprint trong kết quả cũ. */
public record TestAttemptSnapshot(UUID testId,UUID versionId,int version,String title,TestBlueprintSnapshot blueprint) { }
