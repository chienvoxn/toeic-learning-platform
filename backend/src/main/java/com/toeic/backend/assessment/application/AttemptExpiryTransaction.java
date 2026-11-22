package com.toeic.backend.assessment.application;

import com.toeic.backend.assessment.infrastructure.AttemptExpiryRepository;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Một attempt/transaction: dùng cùng grading và khóa với submit tay; lỗi rollback toàn bộ trước khi ghi retry. */
@Service
public class AttemptExpiryTransaction {
    public static class Failure extends RuntimeException {
        private final UUID attemptId;
        Failure(UUID id,RuntimeException cause){super("Expired attempt could not be graded",cause);this.attemptId=id;}
        public UUID attemptId(){return attemptId;}
    }
    private final AttemptExpiryRepository data;private final AttemptGrading grading;private final Clock clock;
    public AttemptExpiryTransaction(AttemptExpiryRepository data,AttemptGrading grading,Clock clock){this.data=data;this.grading=grading;this.clock=clock;}
    @Transactional(propagation=Propagation.REQUIRES_NEW,isolation=Isolation.READ_COMMITTED)
    public UUID next(Set<UUID> attempted){
        var now=OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);var candidate=data.next(now,attempted);
        if(candidate.isEmpty())return null;var id=candidate.get();
        try{
            var a=data.load(id);
            now=OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
            if(now.isBefore(a.getExpiresAt()))return null;
            if(!a.isTestVersion()||!Objects.equals(a.getTestVersionId(),a.getTestSnapshot().versionId()))throw new IllegalStateException("Invalid Full Mock pin");
            grading.grade(a.getUserId(),a,now);return a.getAttemptId();
        }catch(RuntimeException ex){throw new Failure(id,ex);}
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW,isolation=Isolation.READ_COMMITTED)
    public void retry(UUID id){data.failed(id,OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS));}
}
