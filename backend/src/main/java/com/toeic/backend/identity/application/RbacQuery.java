package com.toeic.backend.identity.application;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record RbacQuery(String q,int page,int size) {
    public RbacQuery {
        if(q==null || q.length()>200 || page<0 || size<1 || size>100) throw RbacException.invalid();
        q=q.strip().replaceAll("\\s+"," ");
    }
    public static RbacQuery parse(Map<String,List<String>> params) {
        if(params==null || params.entrySet().stream().anyMatch(entry -> !Set.of("q","page","size").contains(entry.getKey())
                || entry.getValue()==null || entry.getValue().size()!=1 || entry.getValue().getFirst()==null)) throw RbacException.invalid();
        try { return new RbacQuery(value(params,"q",""),Integer.parseInt(value(params,"page","0")),Integer.parseInt(value(params,"size","20"))); }
        catch(NumberFormatException exception) { throw RbacException.invalid(); }
    }
    private static String value(Map<String,List<String>> params,String name,String fallback) { return params.containsKey(name)?params.get(name).getFirst():fallback; }
    public long offset() { return (long)page*size; }
}
