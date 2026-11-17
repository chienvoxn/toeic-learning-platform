package com.toeic.backend.identity.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.toeic.backend.identity.application.RbacException;
import com.toeic.backend.identity.application.RoleEdit;
import java.util.*;
import tools.jackson.databind.JsonNode;

/** DTO chung cho các endpoint RBAC; mỗi lệnh kiểm đúng tập trường, kiểu và duplicate. */
public class RbacRequest {
    private final Map<String,JsonNode> fields=new LinkedHashMap<>();
    @JsonAnySetter public void put(String name,JsonNode value) {
        if(fields.containsKey(name)) throw RbacException.invalid();
        fields.put(name,value);
    }
    public RoleEdit roleEdit(boolean create) {
        if(!Set.of("name","description","displayName").containsAll(fields.keySet()) || (create && !fields.containsKey("name"))) throw RbacException.invalid();
        return new RoleEdit(fields.containsKey("name"),string("name",false),fields.containsKey("description"),string("description",true),
                fields.containsKey("displayName"),string("displayName",true));
    }
    public List<Integer> ids(String name) {
        if(!fields.keySet().equals(Set.of(name))) throw RbacException.invalid();
        var array=fields.get(name);
        int maximum=name.equals("roleIds")?100:1000;
        if(array==null || !array.isArray() || array.size()>maximum) throw RbacException.invalid();
        var result=new TreeSet<Integer>();
        for(var value:array) {
            if(!value.isIntegralNumber() || !value.canConvertToInt() || value.asInt()<1 || !result.add(value.asInt())) throw RbacException.invalid();
        }
        return List.copyOf(result);
    }
    private String string(String name,boolean nullable) {
        if(!fields.containsKey(name))return null;
        var value=fields.get(name);
        if(nullable && (value==null || value.isNull()))return null;
        if(value==null || !value.isString())throw RbacException.invalid();
        return value.asString();
    }
}
