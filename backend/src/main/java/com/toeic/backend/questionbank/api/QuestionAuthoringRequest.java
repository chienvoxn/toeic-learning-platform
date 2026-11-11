package com.toeic.backend.questionbank.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.toeic.backend.questionbank.application.*;
import com.toeic.backend.questionbank.domain.QuestionChoiceDraft;
import java.util.*;
import tools.jackson.databind.JsonNode;

/** Local strict allowlist; no global Jackson behavior change. */
public class QuestionAuthoringRequest {
    private static final Set<String> QUESTION=Set.of("partId","topicId","questionTypeId","groupVersionId","questionText","explanation","difficulty","choices","assets");
    private static final Set<String> GROUP=Set.of("groupType","instruction","passageText","assets");
    private final Map<String,JsonNode> values=new HashMap<>();
    @JsonAnySetter public void field(String name,JsonNode value) {
        if ((!QUESTION.contains(name)&&!GROUP.contains(name)) || values.containsKey(name)) throw QuestionAuthoringException.invalid();
        values.put(name,value);
    }
    public QuestionAuthoringEdit command(boolean group) {
        if(values.isEmpty() || !((group?GROUP:QUESTION).containsAll(values.keySet()))) throw QuestionAuthoringException.invalid();
        try {
            List<QuestionChoiceDraft> choices=null;
            if(values.containsKey("choices")) {
                var node=values.get("choices"); if(node==null||!node.isArray()) throw QuestionAuthoringException.invalid();
                choices=new ArrayList<>();
                for(var choice:node) {
                    if(!choice.isObject()||choice.size()!=4||!choice.has("label")||!choice.has("content")
                            ||!choice.has("isCorrect")||!choice.has("displayOrder")||!choice.get("isCorrect").isBoolean()) throw QuestionAuthoringException.invalid();
                    choices.add(new QuestionChoiceDraft(text(choice.get("label"),false),text(choice.get("content"),false),
                            choice.get("isCorrect").asBoolean(),integer(choice.get("displayOrder"),false)));
                }
            }
            List<QuestionAuthoringEdit.Asset> assets=null;
            if(values.containsKey("assets")) {
                var node=values.get("assets"); if(node==null||!node.isArray()) throw QuestionAuthoringException.invalid();
                assets=new ArrayList<>(); var ids=new HashSet<UUID>();
                for(var asset:node) {
                    if(!asset.isObject()||!asset.has("mediaId")||asset.size()!=(asset.has("transcript")?2:1)) throw QuestionAuthoringException.invalid();
                    var id=uuid(asset.get("mediaId"),false);
                    if(!ids.add(id)) throw QuestionAuthoringException.invalid();
                    assets.add(new QuestionAuthoringEdit.Asset(id,asset.has("transcript")?text(asset.get("transcript"),true):null));
                }
            }
            return new QuestionAuthoringEdit(values.keySet(),number("partId",false),number("topicId",true),number("questionTypeId",false),
                    values.containsKey("groupVersionId")?uuid(values.get("groupVersionId"),true):null,
                    string("questionText",false),string("explanation",true),string("difficulty",true),string("groupType",true),
                    string("instruction",true),string("passageText",true),choices,assets);
        } catch(IllegalArgumentException|NullPointerException ex) { throw QuestionAuthoringException.invalid(); }
    }
    private String string(String key,boolean nullable) { return values.containsKey(key)?text(values.get(key),nullable):null; }
    private Integer number(String key,boolean nullable) { return values.containsKey(key)?integer(values.get(key),nullable):null; }
    private static String text(JsonNode n,boolean nullable) {
        if(n==null||n.isNull()) { if(nullable)return null; throw QuestionAuthoringException.invalid(); }
        if(!n.isString())throw QuestionAuthoringException.invalid();
        return n.asString();
    }
    private static Integer integer(JsonNode n,boolean nullable) {
        if(n==null||n.isNull()) { if(nullable)return null; throw QuestionAuthoringException.invalid(); }
        if(!n.isIntegralNumber()||!n.canConvertToInt()||n.asInt()<1)throw QuestionAuthoringException.invalid();
        return n.asInt();
    }
    private static UUID uuid(JsonNode n,boolean nullable) {
        var raw=text(n,nullable); if(raw==null)return null;
        var id=UUID.fromString(raw); if(!id.toString().equalsIgnoreCase(raw))throw QuestionAuthoringException.invalid(); return id;
    }
}
