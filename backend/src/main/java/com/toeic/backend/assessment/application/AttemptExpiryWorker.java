package com.toeic.backend.assessment.application;

import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Quét batch hữu hạn, bỏ qua attempt đang khóa; retry bền sau restart, log không chứa key/đáp án/token. */
@Component
public class AttemptExpiryWorker {
    private static final Logger LOG=LoggerFactory.getLogger(AttemptExpiryWorker.class);
    private final AttemptExpiryTransaction transactions;private final int batchSize;
    public AttemptExpiryWorker(AttemptExpiryTransaction transactions,@Value("${app.assessment.expiry.batch-size:50}") int batchSize){
        if(batchSize<1||batchSize>500)throw new IllegalArgumentException("Expiry batch size must be 1..500");this.transactions=transactions;this.batchSize=batchSize;
    }
    public record Batch(int examined,int graded,int failed){}
    public Batch runBatch(){
        Set<UUID> seen=new HashSet<>();int graded=0,failed=0;
        for(int i=0;i<batchSize;i++){
            try{UUID id=transactions.next(seen);if(id==null)break;seen.add(id);graded++;}
            catch(AttemptExpiryTransaction.Failure ex){
                seen.add(ex.attemptId());failed++;
                try{transactions.retry(ex.attemptId());}catch(RuntimeException unavailable){LOG.warn("Expiry retry persistence unavailable for attempt {}",ex.attemptId());}
                LOG.warn("Expiry grading deferred for attempt {}",ex.attemptId());
            }catch(RuntimeException unavailable){LOG.warn("Expiry batch interrupted; pending attempts remain durable");break;}
        }
        return new Batch(seen.size(),graded,failed);
    }
}
