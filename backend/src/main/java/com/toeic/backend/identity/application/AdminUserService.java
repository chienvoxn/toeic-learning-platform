package com.toeic.backend.identity.application;

import com.toeic.backend.identity.infrastructure.AdminUserRepository;
import com.toeic.backend.identity.infrastructure.UserRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Kiểm quyền, lọc Learner/Teacher, tuần tự hóa login/status và ghi audit nguyên tử. */
@Service
@PreAuthorize("hasRole('ADMIN') and hasAuthority('user:manage')")
public class AdminUserService {
    private final AdminUserRepository adminUsers;
    private final UserRepository users;
    private final Clock clock;
    private final RbacMutationGuard guard;
    public AdminUserService(AdminUserRepository adminUsers,UserRepository users,Clock clock,RbacMutationGuard guard) {
        this.adminUsers=adminUsers; this.users=users; this.clock=clock;this.guard=guard;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public AdminUserView.Page search(AdminUserQuery query) {
        if (query==null) throw AdminUserException.invalid();
        return adminUsers.search(query);
    }
    @Transactional
    public AdminUserView changeStatus(UUID target,String next) {
        if (target==null || next==null || !Set.of("ACTIVE","LOCKED").contains(next)) throw AdminUserException.invalid();
        var actorState=guard.begin("user:manage");
        var user=users.findForUpdate(target).orElseThrow(AdminUserException::notFound);
        var roleNames=user.getRoles().stream().map(role -> role.getName()).toList();
        if(roleNames.contains("ADMIN"))guard.actor("rbac:manage");
        else if(roleNames.stream().noneMatch(Set.of("LEARNER","TEACHER")::contains))throw AdminUserException.notFound();
        String previous=user.getStatus();
        if (!Set.of("ACTIVE","LOCKED").contains(previous)) throw AdminUserException.transition();
        if (!previous.equals(next)) {
            var actor=(SessionPrincipal)SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            var now=OffsetDateTime.now(clock);
            user.changeAccountStatus(next,now);
            users.flush();
            guard.finish(actorState);
            adminUsers.audit(actor.userId(),target,previous,next,now);
        } else guard.finish(actorState);
        // AUTH-03: do not revoke sessions; unexpired/unrevoked tokens work again after unlock.
        return AdminUserView.from(user);
    }
}
