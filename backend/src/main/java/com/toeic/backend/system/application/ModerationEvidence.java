package com.toeic.backend.system.application;

import com.toeic.backend.system.infrastructure.ModerationRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

/** Ghi contributor trong transaction authoring da khoa parent; legacy khong duoc suy creator. */
@Component
public class ModerationEvidence {
    private final ModerationRepository data;
    public ModerationEvidence(ModerationRepository data){this.data=data;}
    @Transactional(propagation=Propagation.MANDATORY)
    public void record(String kind,UUID parent,UUID version,UUID source,UUID actor,boolean created){data.record(ModerationRepository.Kind.parse(kind),parent,version,source,actor,created);}
}
