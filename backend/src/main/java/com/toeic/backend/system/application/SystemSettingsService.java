package com.toeic.backend.system.application;

import com.toeic.backend.identity.application.RbacMutationGuard;
import com.toeic.backend.identity.infrastructure.RbacRepository;
import com.toeic.backend.system.infrastructure.SystemSettingsRepository;
import java.time.*;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Cấu hình/audit nguyên tử; khóa RBAC trước row, kiểm lại actor sau chờ và chống lost update. */
@Service
@PreAuthorize("hasRole('ADMIN') and hasAuthority('system:settings:manage')")
public class SystemSettingsService {
    private final SystemSettingsRepository repository;private final RbacMutationGuard guard;private final RbacRepository audit;private final Clock clock;
    public SystemSettingsService(SystemSettingsRepository repository,RbacMutationGuard guard,RbacRepository audit,Clock clock){this.repository=repository;this.guard=guard;this.audit=audit;this.clock=clock;}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public List<SystemSettingView> list(){guard.actor("system:settings:manage");return Arrays.stream(SettingDefinition.values()).map(key -> repository.read(key,false)).toList();}
    @Transactional
    public SystemSettingView update(String key,int value,long expected){
        var definition=SettingDefinition.require(key);if(!definition.accepts(value) || expected<0)throw SystemSettingsException.invalid();
        var actor=guard.begin("system:settings:manage");var before=repository.read(definition,true);
        if(before.revision()!=expected)throw SystemSettingsException.stale();
        if(before.value()==value)return before;
        if(before.revision()==Long.MAX_VALUE)throw SystemSettingsException.unavailable();
        repository.update(definition,value,expected,actor.userId(),OffsetDateTime.now(clock));var after=repository.read(definition,false);
        audit.audit(actor.userId(),"SYSTEM_SETTING_UPDATED","SYSTEM_SETTING",null,snapshot(before),snapshot(after),OffsetDateTime.now(clock));return after;
    }
    private Map<String,Object> snapshot(SystemSettingView value){return Map.of("key",value.key(),"value",value.value(),"revision",value.revision());}
}
