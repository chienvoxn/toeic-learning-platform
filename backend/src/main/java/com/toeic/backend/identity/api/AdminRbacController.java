package com.toeic.backend.identity.api;

import com.toeic.backend.identity.application.*;
import com.toeic.backend.identity.application.RbacViews.*;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminRbacController {
    private final RbacService service;
    public AdminRbacController(RbacService service) { this.service=service; }
    private static void noQuery(MultiValueMap<String,String> params) { if(!params.isEmpty())throw RbacException.invalid(); }
    @GetMapping("/roles") public Page<Role> roles(@RequestParam MultiValueMap<String,String> params) { return service.roles(RbacQuery.parse(params)); }
    @GetMapping("/roles/{id}") public Role role(@PathVariable int id,@RequestParam MultiValueMap<String,String> params) { noQuery(params);return service.role(id); }
    @GetMapping("/permissions") public List<Permission> permissions(@RequestParam MultiValueMap<String,String> params) { noQuery(params);return service.permissions(); }
    @GetMapping("/rbac/users") public Page<UserRoles> users(@RequestParam MultiValueMap<String,String> params) { return service.users(RbacQuery.parse(params)); }
    @GetMapping("/users/{id}/roles") public UserRoles user(@PathVariable UUID id,@RequestParam MultiValueMap<String,String> params) { noQuery(params);return service.user(id); }
    @PostMapping("/roles") @ResponseStatus(HttpStatus.CREATED)
    public Role create(@RequestBody RbacRequest request,@RequestParam MultiValueMap<String,String> params) { noQuery(params);return service.create(request.roleEdit(true)); }
    @PatchMapping("/roles/{id}") public Role edit(@PathVariable int id,@RequestBody RbacRequest request,@RequestParam MultiValueMap<String,String> params) { noQuery(params);return service.edit(id,request.roleEdit(false)); }
    @DeleteMapping("/roles/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable int id,@RequestBody(required=false) byte[] body,@RequestParam MultiValueMap<String,String> params) {
        noQuery(params);if(body!=null && body.length>0)throw RbacException.invalid();service.delete(id);
    }
    @PutMapping("/roles/{id}/permissions") public Role permissions(@PathVariable int id,@RequestBody RbacRequest request,@RequestParam MultiValueMap<String,String> params) { noQuery(params);return service.permissions(id,request.ids("permissionIds")); }
    @PutMapping("/users/{id}/roles") public UserRoles userRoles(@PathVariable UUID id,@RequestBody RbacRequest request,@RequestParam MultiValueMap<String,String> params) { noQuery(params);return service.userRoles(id,request.ids("roleIds")); }
}
