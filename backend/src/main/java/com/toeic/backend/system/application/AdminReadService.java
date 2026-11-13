package com.toeic.backend.system.application;

import com.toeic.backend.identity.application.RbacMutationGuard;
import com.toeic.backend.system.infrastructure.AdminReadRepository;
import java.time.*;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Phân quyền và đọc snapshot RR; không sửa audit hoặc suy diễn hoạt động chưa được ghi. */
@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class AdminReadService {
    private final AdminReadRepository repository;
    private final RbacMutationGuard actor;
    private final Clock clock;
    public AdminReadService(AdminReadRepository repository,RbacMutationGuard actor,Clock clock) {this.repository=repository;this.actor=actor;this.clock=clock;}
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('audit:read')")
    public AdminReadViews.Page<AdminReadViews.AuditItem> audit(AdminReadQuery.Audit query) {actor.actor("audit:read");return repository.audit(query);}
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('audit:read')")
    public AdminReadViews.AuditDetail audit(UUID id) {actor.actor("audit:read");return repository.audit(id);}
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('system:usage:read')")
    public AdminReadViews.Usage usage(AdminReadQuery.Window window) {actor.actor("system:usage:read");return repository.usage(window,OffsetDateTime.now(clock));}
}
