package com.toeic.backend.system.api;

import com.toeic.backend.system.application.*;
import com.toeic.backend.system.application.AdminReadViews.*;
import java.time.Clock;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminMonitoringController {
    private final AdminReadService reads;
    private final AdminHealthService health;
    private final Clock clock;
    public AdminMonitoringController(AdminReadService reads,AdminHealthService health,Clock clock) {this.reads=reads;this.health=health;this.clock=clock;}
    @GetMapping("/audit-logs") public Page<AuditItem> audit(@RequestParam MultiValueMap<String,String> params) {return reads.audit(AdminReadQuery.audit(params));}
    @GetMapping("/audit-logs/{id}") public AuditDetail audit(@PathVariable String id,@RequestParam MultiValueMap<String,String> params) {AdminReadQuery.noQuery(params);return reads.audit(AdminReadQuery.uuid(id));}
    @GetMapping("/health") public Health health(@RequestParam MultiValueMap<String,String> params) {AdminReadQuery.noQuery(params);return health.health();}
    @GetMapping("/usage-statistics") public Usage usage(@RequestParam MultiValueMap<String,String> params) {return reads.usage(AdminReadQuery.usage(params,clock));}
}
