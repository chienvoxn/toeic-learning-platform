package com.toeic.backend.identity.application;

import com.toeic.backend.identity.infrastructure.RbacRepository;
import com.toeic.backend.identity.application.RbacViews.*;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Điều phối RBAC dùng bảng hiện có, guard DB chung, chống tự nâng quyền và audit nguyên tử. */
@Service
@PreAuthorize("hasRole('ADMIN') and hasAuthority('rbac:manage')")
public class RbacService {
    private final RbacRepository repository;
    private final RbacMutationGuard guard;
    private final Clock clock;
    public RbacService(RbacRepository repository,RbacMutationGuard guard,Clock clock) { this.repository=repository;this.guard=guard;this.clock=clock; }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Page<Role> roles(RbacQuery query) { guard.actor("rbac:manage");if(query==null)throw RbacException.invalid();return repository.roles(query); }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Role role(int id) { guard.actor("rbac:manage");return repository.role(id); }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public List<Permission> permissions() { guard.actor("rbac:manage");return repository.permissions(); }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Page<UserRoles> users(RbacQuery query) {
        var actor=guard.actor("rbac:manage");if(query==null)throw RbacException.invalid();
        return repository.users(query,actor.userId(),actor.permissions().contains("user:manage"));
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public UserRoles user(UUID id) {
        var actor=guard.actor("rbac:manage");return repository.user(id,actor.userId(),actor.permissions().contains("user:manage"));
    }
    @Transactional public Role create(RoleEdit edit) {
        var actor=guard.begin("rbac:manage");
        if(edit==null || !edit.namePresent())throw RbacException.invalid();
        if(repository.duplicateCode(edit.name(),0))throw RbacException.conflict("ROLE_CODE_CONFLICT","Mã vai trò đã tồn tại.");
        int id=repository.create(edit);var after=repository.role(id);guard.finish(actor);
        repository.audit(actor.userId(),"ROLE_CREATED","ROLE",null,null,after,OffsetDateTime.now(clock));return after;
    }
    @Transactional public Role edit(int id,RoleEdit edit) {
        var actor=guard.begin("rbac:manage");if(edit==null)throw RbacException.invalid();
        repository.lockRole(id);var before=repository.role(id);
        if(before.systemRole() && edit.namePresent() && !before.name().equals(edit.name()))
            throw RbacException.conflict("SYSTEM_ROLE_PROTECTED","Không được đổi mã vai trò hệ thống; có thể sửa tên hiển thị/mô tả.");
        if(edit.namePresent() && repository.duplicateCode(edit.name(),id))throw RbacException.conflict("ROLE_CODE_CONFLICT","Mã vai trò đã tồn tại.");
        repository.edit(id,edit);var after=repository.role(id);guard.finish(actor);
        repository.audit(actor.userId(),"ROLE_UPDATED","ROLE",null,before,after,OffsetDateTime.now(clock));return after;
    }
    @Transactional public void delete(int id) {
        var actor=guard.begin("rbac:manage");repository.lockRole(id);var before=repository.role(id);
        if(before.systemRole())throw RbacException.conflict("SYSTEM_ROLE_PROTECTED","Không được xóa vai trò hệ thống.");
        if(before.userCount()>0)throw RbacException.conflict("ROLE_IN_USE","Vai trò còn được gán cho tài khoản.");
        repository.delete(id);guard.finish(actor);
        repository.audit(actor.userId(),"ROLE_DELETED","ROLE",null,before,null,OffsetDateTime.now(clock));
    }
    @Transactional public Role permissions(int id,List<Integer> ids) {
        var actor=guard.begin("rbac:manage");validateIds(ids,1000);repository.lockRole(id);var before=repository.role(id);
        var supported=new HashSet<Integer>();repository.permissions().forEach(p -> supported.add(p.permissionId()));
        if(!supported.containsAll(ids))throw RbacException.permissionMissing();
        var sorted=ids.stream().sorted().toList();
        if(before.permissionIds().equals(sorted)) { guard.finish(actor);return before; }
        repository.permissions(id,sorted);guard.finish(actor);var after=repository.role(id);
        repository.audit(actor.userId(),"ROLE_PERMISSIONS_CHANGED","ROLE",null,before,after,OffsetDateTime.now(clock));return after;
    }
    @Transactional public UserRoles userRoles(UUID id,List<Integer> ids) {
        var actor=guard.begin("rbac:manage");validateIds(ids,100);repository.lockUser(id);
        var before=repository.user(id,actor.userId(),actor.permissions().contains("user:manage"));
        var sorted=ids.stream().sorted().toList();
        for(int role:sorted)repository.lockRole(role);
        var oldIds=before.roles().stream().map(RoleRef::roleId).sorted().toList();
        for(int role:sorted) {
            var value=repository.role(role);
            if(value.administrativeRole() && !oldIds.contains(role) && !before.status().equals("ACTIVE"))
                throw RbacException.conflict("USER_STATUS_CONFLICT","Chỉ cấp ADMIN cho tài khoản ACTIVE hiện có.");
        }
        if(oldIds.equals(sorted)) { guard.finish(actor);return before; }
        repository.userRoles(id,sorted,OffsetDateTime.now(clock));guard.finish(actor);
        var after=repository.user(id,actor.userId(),actor.permissions().contains("user:manage"));
        repository.audit(actor.userId(),"USER_ROLES_CHANGED","USER",id,Map.of("userId",id,"roles",before.roles()),
                Map.of("userId",id,"roles",after.roles()),OffsetDateTime.now(clock));return after;
    }
    private static void validateIds(List<Integer> ids,int max) {
        if(ids==null || ids.size()>max || ids.stream().anyMatch(id -> id==null || id<1) || new HashSet<>(ids).size()!=ids.size())throw RbacException.invalid();
    }
}
