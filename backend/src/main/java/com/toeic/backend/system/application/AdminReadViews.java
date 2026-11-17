package com.toeic.backend.system.application;

import java.time.OffsetDateTime;
import java.util.*;

public final class AdminReadViews {
    private AdminReadViews() {}
    public record Page<T>(List<T> items,long totalElements,int page,int size) { public Page {items=List.copyOf(items);} }
    public record AuditItem(UUID auditId,UUID actorUserId,String action,String entityType,String entityId,OffsetDateTime createdAt) {}
    public record AuditDetail(AuditItem event,Map<String,Object> changes) { public AuditDetail {changes=Map.copyOf(changes);} }
    public record Count(String name,long count) {}
    public record RoleCount(int roleId,String name,long count) {}
    public record Users(long total,long withoutRoleCount,List<Count> byStatus,List<RoleCount> byRole) {}
    public record Attempts(long startedCount,long submittedCount) {}
    public record AuditActivity(long recordedCount,List<Count> byAction) {}
    public record Usage(OffsetDateTime generatedAt,AdminReadQuery.Window window,Users users,Attempts practice,Attempts section,AuditActivity administrativeActivity) {}
    public record Component(String status,OffsetDateTime checkedAt) {}
    public record Health(OffsetDateTime checkedAt,Map<String,Component> components) {public Health {components=Map.copyOf(components);} }
}
