package com.toeic.backend.assessment.application;

import com.toeic.backend.identity.application.RbacMutationGuard;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

/** Cùng khóa RBAC với moderation; đọc quyền ACTIVE từ DB sau khi đợi khóa. */
@Component
@Transactional(propagation=Propagation.MANDATORY)
public class ExamMutationAccess {
    private final JdbcTemplate jdbc;private final RbacMutationGuard rights;
    public ExamMutationAccess(JdbcTemplate jdbc,RbacMutationGuard rights){this.jdbc=jdbc;this.rights=rights;}
    public RbacMutationGuard.State lock(UUID actor){jdbc.query("SELECT pg_advisory_xact_lock_shared(73532002)",r->{});var a=rights.state(actor);if(!a.active())throw new AccessDeniedException("Active actor required");return a;}
    public void manage(RbacMutationGuard.State a){if(!a.permissions().contains("test:manage:self")&&!a.permissions().contains("test:manage:any"))throw new AccessDeniedException("Test management permission required");}
    public boolean any(RbacMutationGuard.State a){return a.permissions().contains("test:manage:any");}
    public boolean reviewer(RbacMutationGuard.State a){return a.admin()&&(a.permissions().contains("test:review")||a.permissions().contains("test:publish"));}
}
