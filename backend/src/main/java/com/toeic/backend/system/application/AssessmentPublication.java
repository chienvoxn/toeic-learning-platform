package com.toeic.backend.system.application;

import java.util.UUID;

/** Port để moderation dùng validation/snapshot do module Assessment sở hữu. */
public interface AssessmentPublication {
    void validate(UUID version);
    void freeze(UUID version);
}
