package com.toeic.backend.questionbank.application;

import java.util.*;
public record QuestionAuthoringQuery(int page,int size,String sort,String direction,String status,Integer partId,Integer topicId,String groupType) {
    public static QuestionAuthoringQuery parse(Map<String,List<String>> params,boolean group) {
        var allowed=group?Set.of("page","size","sort","status","groupType"):Set.of("page","size","sort","status","partId","topicId");
        if(!allowed.containsAll(params.keySet())||params.values().stream().anyMatch(v->v==null||v.size()!=1||v.getFirst()==null||v.getFirst().isBlank())) throw QuestionAuthoringException.invalid();
        try {
            int page=Integer.parseInt(value(params,"page","0")),size=Integer.parseInt(value(params,"size","20"));
            var sort=value(params,"sort","updatedAt,desc").split(",",-1);
            if(page<0||size<1||size>100||(long)page*size>Integer.MAX_VALUE||sort.length!=2
                    ||!Set.of("createdAt","updatedAt",group?"groupId":"questionId","status").contains(sort[0])
                    ||!Set.of("asc","desc").contains(sort[1]))throw QuestionAuthoringException.invalid();
            var status=value(params,"status",null);
            if(status!=null&&!Set.of("DRAFT","PUBLISHED","ARCHIVED").contains(status))throw QuestionAuthoringException.invalid();
            var part=positive(value(params,"partId",null)); var topic=positive(value(params,"topicId",null));
            var type=value(params,"groupType",null); if(type!=null&&type.length()>30)throw QuestionAuthoringException.invalid();
            return new QuestionAuthoringQuery(page,size,sort[0],sort[1],status,part,topic,type);
        } catch(NumberFormatException ex) { throw QuestionAuthoringException.invalid(); }
    }
    private static Integer positive(String value) { if(value==null)return null; int n=Integer.parseInt(value); if(n<1)throw QuestionAuthoringException.invalid(); return n; }
    private static String value(Map<String,List<String>> p,String key,String fallback) { return p.containsKey(key)?p.get(key).getFirst():fallback; }
}
