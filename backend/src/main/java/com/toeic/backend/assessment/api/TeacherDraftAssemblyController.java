package com.toeic.backend.assessment.api;

import com.toeic.backend.assessment.application.*;
import com.toeic.backend.identity.application.SessionPrincipal;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Không tự gắn 200 DRAFT theo UUID: chủ đề phải dùng explicit unit/version/revision của cùng DRAFT engine. */
@RestController
@RequestMapping("/api/v1/teacher/test-versions")
@org.springframework.security.access.prepost.PreAuthorize("hasAnyAuthority('test:manage:self','test:manage:any')")
public class TeacherDraftAssemblyController {
    private final JdbcTemplate jdbc;private final ExamWorkflowService workflow;
    public TeacherDraftAssemblyController(JdbcTemplate jdbc,ExamWorkflowService workflow) {this.jdbc=jdbc;this.workflow=workflow;}
    @PostMapping("/{versionId}/assemble-demo")
    public Object assemble(@PathVariable UUID versionId,@AuthenticationPrincipal SessionPrincipal actor) {
        var tests=jdbc.query("SELECT test_id FROM test_versions WHERE test_version_id=?",(r,n)->r.getObject(1,UUID.class),versionId);
        if(tests.isEmpty())throw ExamDraftException.missing();
        workflow.read(actor.userId(),tests.getFirst(),versionId);
        throw ExamDraftException.conflict("EXPLICIT_TEST_SELECTION_REQUIRED");
    }
}
