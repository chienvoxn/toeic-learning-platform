package com.toeic.backend.system.api;

import com.toeic.backend.system.application.*;
import com.toeic.backend.system.infrastructure.ModerationRepository.Kind;
import java.util.*;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/moderation")
public class ModerationController {
    private final ModerationService service;
    private final GroupPublicationService groups;
    public ModerationController(ModerationService service,GroupPublicationService groups){this.service=service;this.groups=groups;}
    private static void empty(MultiValueMap<String,String> params){if(!params.isEmpty())throw ModerationException.invalid();}
    @PostMapping("/groups/{parent}/versions/{version}/publish-batch")
    public Object publishGroup(@PathVariable UUID parent,@PathVariable UUID version,@RequestParam MultiValueMap<String,String> params,@RequestBody GroupPublicationRequest body){
        empty(params);return groups.publish(parent,version,body.command());
    }
    @GetMapping("/capabilities") public Object capabilities(@RequestParam MultiValueMap<String,String> params){empty(params);return service.capabilities();}
    @GetMapping("/{kind:contents|questions|groups}") public Object list(@PathVariable String kind,@RequestParam MultiValueMap<String,String> params){
        int page=0;if(!params.isEmpty()){
            if(!params.keySet().equals(Set.of("page"))||params.get("page").size()!=1||!params.getFirst("page").matches("[0-9]{1,5}"))throw ModerationException.invalid();page=Integer.parseInt(params.getFirst("page"));
        }return service.list(Kind.parse(kind),page);
    }
    @GetMapping("/{kind:contents|questions|groups}/{parent}/versions/{version}") public Object detail(@PathVariable String kind,@PathVariable UUID parent,@PathVariable UUID version,@RequestParam MultiValueMap<String,String> params){empty(params);return service.detail(Kind.parse(kind),parent,version);}
    @PostMapping("/{kind:contents|questions|groups}/{parent}/versions/{version}/{command:submit|approve|return|revoke|publish}")
    public Object transition(@PathVariable String kind,@PathVariable UUID parent,@PathVariable UUID version,@PathVariable String command,@RequestParam MultiValueMap<String,String> params,@RequestBody ModerationRequest body){
        empty(params);var value=body.validate(command,kind.equals("contents"));return service.transition(Kind.parse(kind),parent,version,command,value.expectedRevision(),value.reason(),value.visibility());
    }
}
