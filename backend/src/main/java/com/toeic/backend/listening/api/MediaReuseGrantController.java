package com.toeic.backend.listening.api;

import com.toeic.backend.identity.application.SessionPrincipal;
import com.toeic.backend.listening.application.*;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

/** API owner-only, không nhận actor/owner từ body và không trả URL storage hoặc thông tin tài khoản. */
@RestController
@RequestMapping("/api/v1/teacher/media")
public class MediaReuseGrantController {
    private final MediaReuseGrantService service;
    public MediaReuseGrantController(MediaReuseGrantService service){this.service=service;}
    private int[] page(MultiValueMap<String,String> query){
        if(!Set.of("page","size").containsAll(query.keySet())||query.values().stream().anyMatch(v->v.size()!=1))throw ListeningMediaException.invalid();
        String p=query.getFirst("page"),s=query.getFirst("size");
        if(p!=null&&!p.matches("[0-9]{1,5}")||s!=null&&!s.matches("[0-9]{1,3}"))throw ListeningMediaException.invalid();
        int pn=p==null?0:Integer.parseInt(p),sn=s==null?20:Integer.parseInt(s);
        if(pn>10000||sn<1||sn>100)throw ListeningMediaException.invalid();return new int[]{pn,sn};
    }
    @GetMapping public Object owned(@AuthenticationPrincipal SessionPrincipal actor,@RequestParam MultiValueMap<String,String> query){var p=page(query);return service.owned(actor.userId(),p[0],p[1]);}
    @GetMapping("/{mediaId}/reuse-grants") public Object grants(@AuthenticationPrincipal SessionPrincipal actor,@PathVariable UUID mediaId,@RequestParam MultiValueMap<String,String> query){var p=page(query);return service.grants(actor.userId(),mediaId,p[0],p[1]);}
    @RequestMapping(value="/{mediaId}/reuse-grants/{userId}",method={RequestMethod.PUT,RequestMethod.DELETE})
    public Object change(@AuthenticationPrincipal SessionPrincipal actor,@PathVariable UUID mediaId,@PathVariable UUID userId,@RequestParam MultiValueMap<String,String> query,@RequestBody(required=false) byte[] body,jakarta.servlet.http.HttpServletRequest request){
        if(!query.isEmpty()||body!=null&&body.length>0)throw ListeningMediaException.invalid();return service.change(actor.userId(),mediaId,userId,request.getMethod().equals("PUT"));
    }
}
