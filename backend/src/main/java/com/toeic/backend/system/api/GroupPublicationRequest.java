package com.toeic.backend.system.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.toeic.backend.system.application.GroupPublicationService.*;
import com.toeic.backend.system.application.ModerationException;
import java.util.*;
import tools.jackson.databind.JsonNode;

/** Payload batch strict: every pointer is explicit, including null for a first publication. */
public class GroupPublicationRequest {
    private final Map<String,JsonNode> fields=new HashMap<>();
    @JsonAnySetter public void field(String key,JsonNode value){if(fields.putIfAbsent(key,value)!=null)throw ModerationException.invalid();}
    public Command command(){
        if(!fields.keySet().equals(Set.of("expectedRevision","expectedPublishedVersionId","members")))throw ModerationException.invalid();
        var members=fields.get("members");if(members==null||!members.isArray()||members.size()<1||members.size()>200)throw ModerationException.invalid();
        var result=new ArrayList<Member>();
        for(var m:members){
            var keys=new HashSet<String>();if(!m.isObject())throw ModerationException.invalid();m.properties().forEach(e->keys.add(e.getKey()));
            if(!keys.equals(Set.of("questionId","questionVersionId","expectedRevision","expectedPublishedVersionId","order")))throw ModerationException.invalid();
            long order=number(m.get("order"));if(order>200)throw ModerationException.invalid();
            result.add(new Member(uuid(m.get("questionId"),false),uuid(m.get("questionVersionId"),false),number(m.get("expectedRevision")),uuid(m.get("expectedPublishedVersionId"),true),(int)order));
        }
        return new Command(number(fields.get("expectedRevision")),uuid(fields.get("expectedPublishedVersionId"),true),result);
    }
    private long number(JsonNode node){if(node==null||!node.isIntegralNumber()||!node.canConvertToLong()||node.asLong()<0)throw ModerationException.invalid();return node.asLong();}
    private UUID uuid(JsonNode node,boolean nullable){
        if(node!=null&&node.isNull()&&nullable)return null;
        if(node==null||!node.isString())throw ModerationException.invalid();
        try{var value=UUID.fromString(node.asString());if(!value.toString().equalsIgnoreCase(node.asString()))throw ModerationException.invalid();return value;}
        catch(IllegalArgumentException ex){throw ModerationException.invalid();}
    }
}
