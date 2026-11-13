package com.toeic.backend.system.api;

import com.toeic.backend.system.application.*;
import java.util.List;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/system-settings")
public class SystemSettingsController {
    private final SystemSettingsService service;
    public SystemSettingsController(SystemSettingsService service){this.service=service;}
    @GetMapping public List<SystemSettingView> list(@RequestParam MultiValueMap<String,String> query){AdminReadQuery.noQuery(query);return service.list();}
    @PatchMapping("/{key}") public SystemSettingView update(@PathVariable String key,@RequestBody SettingUpdateRequest request,@RequestParam MultiValueMap<String,String> query){
        AdminReadQuery.noQuery(query);var value=request.validate();return service.update(key,value.value(),value.expectedRevision());
    }
}
