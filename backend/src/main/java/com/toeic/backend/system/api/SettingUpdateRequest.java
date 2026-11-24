package com.toeic.backend.system.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.toeic.backend.system.application.SystemSettingsException;
import java.util.*;
import tools.jackson.databind.JsonNode;

public class SettingUpdateRequest {
    private final Map<String,JsonNode> fields=new HashMap<>();
    @JsonAnySetter public void put(String key,JsonNode value){if(fields.containsKey(key))throw SystemSettingsException.invalid();fields.put(key,value);}
    public record Update(int value,long expectedRevision){}
    public Update validate(){
        if(!fields.keySet().equals(Set.of("value","expectedRevision")))throw SystemSettingsException.invalid();
        var value=fields.get("value");var revision=fields.get("expectedRevision");
        if(value==null || !value.isIntegralNumber() || !value.canConvertToInt() || revision==null || !revision.isIntegralNumber() || !revision.canConvertToLong() || revision.asLong()<0)throw SystemSettingsException.invalid();
        return new Update(value.asInt(),revision.asLong());
    }
}
