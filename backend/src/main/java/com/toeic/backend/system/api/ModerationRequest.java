package com.toeic.backend.system.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.toeic.backend.system.application.ModerationException;
import java.util.*;
import tools.jackson.databind.JsonNode;

public class ModerationRequest {
    private final Map<String,JsonNode> fields=new HashMap<>();
    @JsonAnySetter public void put(String key,JsonNode value){if(fields.containsKey(key))throw ModerationException.invalid();fields.put(key,value);}
    public record Command(long expectedRevision,String reason,String visibility){}
    public Command validate(String command,boolean content){
        var allowed=new HashSet<>(Set.of("expectedRevision"));boolean reason=Set.of("return","revoke").contains(command);boolean visibility=command.equals("publish")&&content;
        if(reason)allowed.add("reason");if(visibility)allowed.add("visibility");
        if(!fields.keySet().equals(allowed))throw ModerationException.invalid();var revision=fields.get("expectedRevision");
        if(revision==null||!revision.isIntegralNumber()||!revision.canConvertToLong()||revision.asLong()<0)throw ModerationException.invalid();
        String r=null,v=null;
        if(reason){var value=fields.get("reason");if(value==null||!value.isString()||value.asString().isBlank()||value.asString().length()>2000)throw ModerationException.invalid();r=value.asString().strip();}
        if(visibility){var value=fields.get("visibility");if(value==null||!value.isString()||!Set.of("PUBLIC","AUTHENTICATED").contains(value.asString()))throw ModerationException.invalid();v=value.asString();}
        return new Command(revision.asLong(),r,v);
    }
}
