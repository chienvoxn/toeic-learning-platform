package com.toeic.backend.system.application;

import com.toeic.backend.identity.infrastructure.RbacRepository;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Ghi create/update/archive cùng transaction của aggregate; chỉ ID, trạng thái và tên trường. */
@Component
public class ContentManagementAudit {
    public enum Resource { CONTENT, QUESTION, QUESTION_GROUP }
    public enum Operation { CREATED, UPDATED, ARCHIVED }
    public static class Unavailable extends RuntimeException {
        private final Operation operation;
        public Unavailable(Operation operation) { this.operation=operation; }
        public Operation operation() { return operation; }
    }
    private final RbacRepository audit;
    public ContentManagementAudit(RbacRepository audit) { this.audit=audit; }

    @Transactional(propagation=Propagation.MANDATORY)
    public void changed(UUID actor,Resource resource,UUID id,String before,OffsetDateTime now) {
        if(!"ARCHIVED".equals(before))write(actor,resource,Operation.ARCHIVED,id,
                Map.of("status",before),Map.of("status","ARCHIVED"),now);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void created(UUID actor,Resource resource,UUID id,UUID version,Set<String> fields,OffsetDateTime now) {
        write(actor,resource,Operation.CREATED,id,null,Map.of("status","DRAFT","versionId",version,"fields",new TreeSet<>(fields)),now);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void updated(UUID actor,Resource resource,UUID id,String status,UUID beforeVersion,UUID afterVersion,Set<String> fields,OffsetDateTime now) {
        write(actor,resource,Operation.UPDATED,id,Map.of("status",status,"versionId",beforeVersion),
                Map.of("status",status,"versionId",afterVersion,"fields",new TreeSet<>(fields)),now);
    }
    private void write(UUID actor,Resource resource,Operation operation,UUID id,Object before,Object after,OffsetDateTime now) {
        try { audit.audit(actor,resource.name()+"_"+operation.name(),resource.name(),id,before,after,now); }
        catch(DataAccessException exception) { throw new Unavailable(operation); }
    }
}
