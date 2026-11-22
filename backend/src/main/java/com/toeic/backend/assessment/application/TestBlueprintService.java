package com.toeic.backend.assessment.application;

import com.toeic.backend.assessment.domain.TestBlueprintSnapshot;
import com.toeic.backend.assessment.infrastructure.TestBlueprintRepository;
import com.toeic.backend.identity.application.RbacMutationGuard;
import com.toeic.backend.identity.infrastructure.RbacRepository;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Đăng ký cấu hình blueprint tường minh bởi Admin; không seed con số chưa được chốt. */
@Service
@PreAuthorize("hasAnyAuthority('test:manage:self','test:manage:any','test:review','test:publish') and @examAccess.actor(#actorId)")
@Transactional
public class TestBlueprintService {
    private final TestBlueprintRepository data;private final RbacMutationGuard rights;private final RbacRepository audit;private final JdbcTemplate jdbc;private final Clock clock;
    public TestBlueprintService(TestBlueprintRepository data,RbacMutationGuard rights,RbacRepository audit,JdbcTemplate jdbc,Clock clock){this.data=data;this.rights=rights;this.audit=audit;this.jdbc=jdbc;this.clock=clock;}
    @Transactional(readOnly=true) public List<TestBlueprintSnapshot> list(UUID actorId){return data.list();}
    public TestBlueprintSnapshot create(UUID actorId,TestBlueprintSnapshot input){
        jdbc.query("SELECT pg_advisory_xact_lock_shared(73532002)",r->{});var a=rights.state(actorId);
        if(!a.active()||!a.admin()||!a.permissions().contains("test:manage:any"))throw new AccessDeniedException("Admin blueprint management required");
        validate(input);data.create(input);audit.audit(actorId,"TEST_BLUEPRINT_CREATED","TEST_BLUEPRINT",input.blueprintId(),null,Map.of("code",input.code(),"version",input.version()),OffsetDateTime.now(clock));return input;
    }
    public static void validate(TestBlueprintSnapshot b){
        if(b==null||b.blueprintId()==null||b.code()==null||!b.code().matches("[A-Z][A-Z0-9_-]{1,79}")||b.version()<1||b.name()==null||b.name().isBlank()||b.name().length()>150||b.durationSeconds()<1||b.parts().size()!=7)throw ExamDraftException.invalid();
        int total=0;
        for(int i=0;i<7;i++){
            var p=b.parts().get(i);if(p==null||p.partNumber()!=i+1||p.questionCount()<1||p.groupCount()==null||p.groupCount()<0)throw ExamDraftException.invalid();
            if(p.partNumber()<=4&&!p.requireAudio()||p.partNumber()==1&&!p.requireImage())throw ExamDraftException.invalid();
            boolean grouped=Set.of(3,4,6,7).contains(p.partNumber());
            if(grouped){
                if(p.groupCount()<1||p.minGroupSize()==null||p.maxGroupSize()==null||p.minGroupSize()<1||p.maxGroupSize()<p.minGroupSize()
                        ||(long)p.groupCount()*p.minGroupSize()>p.questionCount()||(long)p.groupCount()*p.maxGroupSize()<p.questionCount())throw ExamDraftException.invalid();
            }else if(p.groupCount()!=0||p.minGroupSize()!=null||p.maxGroupSize()!=null)throw ExamDraftException.invalid();
            try{total=Math.addExact(total,p.questionCount());}catch(ArithmeticException e){throw ExamDraftException.invalid();}
        }
    }
}
