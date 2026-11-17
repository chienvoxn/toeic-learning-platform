package com.toeic.backend.system.application;

import java.time.*;
import java.util.*;

/** Bộ lọc allowlist, thời gian UTC [from,to), sort ổn định không nhận SQL từ client. */
public final class AdminReadQuery {
    private AdminReadQuery() {}
    public record Window(OffsetDateTime from,OffsetDateTime to) {
        public Window {
            if(from!=null)from=from.withOffsetSameInstant(ZoneOffset.UTC);
            if(to!=null)to=to.withOffsetSameInstant(ZoneOffset.UTC);
            if(from!=null && to!=null && !from.isBefore(to))throw AdminReadException.invalid();
        }
    }
    public record Audit(UUID actor,String action,String entityType,String entityId,Window window,int page,int size,String sort) {
        public long offset() { return (long)page*size; }
    }
    private static void keys(Map<String,List<String>> params,Set<String> allowed) {
        if(params==null || params.entrySet().stream().anyMatch(e -> !allowed.contains(e.getKey()) || e.getValue()==null
                || e.getValue().size()!=1 || e.getValue().getFirst()==null || e.getValue().getFirst().isBlank()))throw AdminReadException.invalid();
    }
    private static String value(Map<String,List<String>> params,String key,String fallback) { return params.containsKey(key)?params.get(key).getFirst():fallback; }
    private static OffsetDateTime time(Map<String,List<String>> params,String key) {
        return params.containsKey(key)?OffsetDateTime.parse(value(params,key,null)).withOffsetSameInstant(ZoneOffset.UTC):null;
    }
    private static String code(Map<String,List<String>> params,String key) {
        String value=value(params,key,null);
        if(value!=null && !value.matches("[A-Z][A-Z0-9_]{0,99}"))throw AdminReadException.invalid();return value;
    }
    public static UUID uuid(String value) {
        if(value==null || !value.matches("(?i)[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}"))throw AdminReadException.invalid();
        return UUID.fromString(value);
    }
    public static Audit audit(Map<String,List<String>> params) {
        keys(params,Set.of("actor","action","entityType","entityId","from","to","page","size","sort"));
        try {
            int page=Integer.parseInt(value(params,"page","0")),size=Integer.parseInt(value(params,"size","20"));
            String sort=value(params,"sort","createdAt,desc");
            if(page<0 || size<1 || size>100 || !Set.of("createdAt,asc","createdAt,desc","action,asc","action,desc").contains(sort))throw AdminReadException.invalid();
            String type=code(params,"entityType"),id=value(params,"entityId",null);
            if(id!=null) {
                if("ROLE".equals(type) && id.matches("[1-9][0-9]{0,9}")) { if(Long.parseLong(id)>Integer.MAX_VALUE)throw AdminReadException.invalid(); }
                else id=uuid(id).toString();
            }
            return new Audit(params.containsKey("actor")?uuid(value(params,"actor",null)):null,code(params,"action"),type,id,
                    new Window(time(params,"from"),time(params,"to")),page,size,sort);
        } catch(IllegalArgumentException|DateTimeException exception) { throw AdminReadException.invalid(); }
    }
    public static Window usage(Map<String,List<String>> params,Clock clock) {
        keys(params,Set.of("from","to"));
        try {
            var to=params.containsKey("to")?time(params,"to"):OffsetDateTime.now(clock);
            var from=params.containsKey("from")?time(params,"from"):to.minusDays(30);
            return new Window(from,to);
        } catch(DateTimeException exception) { throw AdminReadException.invalid(); }
    }
    public static void noQuery(Map<String,List<String>> params) { if(params==null || !params.isEmpty())throw AdminReadException.invalid(); }
}
