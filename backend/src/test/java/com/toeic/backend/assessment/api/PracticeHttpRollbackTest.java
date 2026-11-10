package com.toeic.backend.assessment.api;

import com.toeic.backend.questionbank.application.PracticeQuestionCatalog;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PracticeHttpRollbackTest extends PracticeTestSupport {
    @MockitoSpyBean PracticeQuestionCatalog catalog;
    @Test void httpFailureAfterFlushingParentAndSnapshotsRollsBackEverything()throws Exception {
        PracticeQuestionCatalog target=AopTestUtils.getUltimateTargetObject(catalog);
        doAnswer(invocation->{
            assertEquals(1,count("select count(*) from attempts where user_id=?",ACTOR));
            assertEquals(1,count("select count(*) from attempt_question_snapshots"));
            throw new PracticeQuestionCatalog.Unavailable(false);
        }).when(target).read(eq(ACTOR),any(UUID.class),nullable(UUID.class));
        call(post(API).contentType(APPLICATION_JSON).content(payload(List.of(2),1)),token(ACTOR))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRACTICE_SNAPSHOT_UNAVAILABLE"));
        assertEquals(0,count("select count(*) from attempts where user_id=?",ACTOR));assertEquals(0,count("select count(*) from attempt_question_snapshots"));
    }
}
