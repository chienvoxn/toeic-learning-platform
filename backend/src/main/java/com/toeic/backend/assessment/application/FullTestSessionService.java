package com.toeic.backend.assessment.application;

import java.util.UUID;
import org.springframework.stereotype.Service;

/** Adapter URL develop: dùng đúng engine publication/blueprint/pin hiện hành, không tạo dòng FULL_TEST mới. */
@Service
public class FullTestSessionService {
    private final FullMockSessionService sessions;
    public FullTestSessionService(FullMockSessionService sessions) { this.sessions=sessions; }
    public PracticeSessionDtos.Session start(UUID actorId,UUID testVersionId) { return sessions.create(actorId,testVersionId); }
}
