package com.toeic.backend.assessment.application;

import com.toeic.backend.assessment.infrastructure.AttemptHistoryRepository;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Đọc lịch sử của chính người dùng; lọc quyền trước phân trang, chỉ dùng kết quả đã lưu. */
@Service
public class AttemptHistoryService {
    private final AttemptHistoryRepository repository;
    public AttemptHistoryService(AttemptHistoryRepository repository) { this.repository = repository; }

    @PreAuthorize("hasAnyAuthority('practice:manage:self','exam:manage:self') and @practiceAccess.isActor(#actorId)")
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AttemptHistoryDtos.Page list(UUID actorId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw PracticeException.invalid();
        var authorities = SecurityContextHolder.getContext().getAuthentication().getAuthorities();
        boolean practice = authorities.stream().anyMatch(a -> a.getAuthority().equals("practice:manage:self"));
        boolean exam = authorities.stream().anyMatch(a -> a.getAuthority().equals("exam:manage:self"));
        return repository.find(actorId, practice, exam, page, size);
    }
}
