package com.toeic.backend.identity.api;

import com.toeic.backend.identity.application.*;
import java.util.UUID;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {
    private final AdminUserService service;
    public AdminUserController(AdminUserService service) { this.service=service; }
    @GetMapping public AdminUserView.Page list(@RequestParam MultiValueMap<String,String> query) {
        return service.search(AdminUserQuery.parse(query));
    }
    @PatchMapping("/{id}/status") public AdminUserView change(@PathVariable UUID id,@RequestBody AdminStatusRequest request,
            @RequestParam MultiValueMap<String,String> query) {
        if (!query.isEmpty()) throw AdminUserException.invalid();
        return service.changeStatus(id,request.status());
    }
}
