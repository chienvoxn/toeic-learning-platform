package com.toeic.backend.assessment.infrastructure;

import com.toeic.backend.assessment.application.AttemptExpiryWorker;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;

/** Scheduler không có principal: Full Mock đã pin được chốt dù session người học hết hạn. */
@Configuration(proxyBeanMethods=false)
@EnableScheduling
@ConditionalOnProperty(name="app.assessment.expiry.enabled",havingValue="true",matchIfMissing=true)
public class AttemptExpirySchedule {
    private final AttemptExpiryWorker worker;
    public AttemptExpirySchedule(AttemptExpiryWorker worker){this.worker=worker;}
    @Scheduled(fixedDelayString="${app.assessment.expiry.poll-delay-ms:5000}",initialDelayString="${app.assessment.expiry.initial-delay-ms:5000}")
    public void expire(){worker.runBatch();}
}
