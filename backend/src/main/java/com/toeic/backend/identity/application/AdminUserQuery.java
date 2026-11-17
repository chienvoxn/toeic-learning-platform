package com.toeic.backend.identity.application;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record AdminUserQuery(String q, String role, String status, int page, int size, String sort) {
    public AdminUserQuery {
        if (q==null || q.length()>200 || page<0 || size<1 || size>100
                || (role!=null && !Set.of("LEARNER","TEACHER").contains(role))
                || (status!=null && !Set.of("ACTIVE","LOCKED","PENDING","DISABLED").contains(status))
                || sort==null || !Set.of("createdAt,desc","createdAt,asc","fullName,asc","fullName,desc","email,asc","email,desc").contains(sort)) {
            throw AdminUserException.invalid();
        }
        q=q.strip().replaceAll("\\s+"," ");
    }
    public static AdminUserQuery parse(Map<String,List<String>> params) {
        if (params==null || params.entrySet().stream().anyMatch(e -> !Set.of("q","role","status","page","size","sort").contains(e.getKey())
                || e.getValue()==null || e.getValue().size()!=1 || e.getValue().getFirst()==null)) throw AdminUserException.invalid();
        try {
            return new AdminUserQuery(value(params,"q",""),value(params,"role",null),value(params,"status",null),
                    Integer.parseInt(value(params,"page","0")),Integer.parseInt(value(params,"size","20")),value(params,"sort","createdAt,desc"));
        } catch (NumberFormatException e) { throw AdminUserException.invalid(); }
    }
    private static String value(Map<String,List<String>> params,String key,String fallback) {
        return params.containsKey(key)?params.get(key).getFirst():fallback;
    }
}
