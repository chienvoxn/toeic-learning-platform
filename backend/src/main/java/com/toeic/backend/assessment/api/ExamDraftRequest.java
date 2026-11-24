package com.toeic.backend.assessment.api;

import com.toeic.backend.assessment.application.ExamDraftException;
import com.toeic.backend.assessment.domain.ExamDraftDocument;
import java.util.*;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Parser riêng: không coercion, field lạ hoặc duplicate; không đổi Jackson của API khác. */
final class ExamDraftRequest {
    private static final JsonMapper JSON=JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
    record Edit(int revision, ExamDraftDocument document) { }
    static JsonNode tree(String body){try{return JSON.readTree(body);}catch(RuntimeException e){throw ExamDraftException.invalid();}}
    static Edit parse(String body, boolean update) {
        try {
            var root=JSON.readTree(body); int revision=0; JsonNode doc=root;
            if(update) { fields(root,"revision","document"); if(root.size()!=2)throw ExamDraftException.invalid();
                revision=integer(root.get("revision")); if(revision<0)throw ExamDraftException.invalid(); doc=root.get("document"); }
            fields(doc,"title","description","testType","durationSeconds","sections");
            var sections=new ArrayList<ExamDraftDocument.Section>();
            for(var section:array(doc.get("sections"))) {
                fields(section,"type","parts"); var parts=new ArrayList<ExamDraftDocument.Part>();
                for(var part:array(section.get("parts"))) {
                    fields(part,"partNumber","questions"); var questions=new ArrayList<ExamDraftDocument.Question>();
                    for(var q:array(part.get("questions"))) {
                        fields(q,"questionVersionId","groupVersionId");
                        questions.add(new ExamDraftDocument.Question(uuid(q.get("questionVersionId"),false),uuid(q.get("groupVersionId"),true)));
                    }
                    parts.add(new ExamDraftDocument.Part(integer(part.get("partNumber")),questions));
                }
                sections.add(new ExamDraftDocument.Section(text(section.get("type"),false),parts));
            }
            var duration=doc.get("durationSeconds");
            return new Edit(revision,new ExamDraftDocument(text(doc.get("title"),false),text(doc.get("description"),true),
                    text(doc.get("testType"),false),duration==null || duration.isNull()?null:integer(duration),sections));
        } catch(ExamDraftException e) { throw e; }
        catch(RuntimeException e) { throw ExamDraftException.invalid(); }
    }
    static void fields(JsonNode node, String... allowed) {
        if(node==null || !node.isObject())throw ExamDraftException.invalid();
        int count=0; for(var field:allowed)if(node.has(field))count++;
        if(count!=node.size())throw ExamDraftException.invalid();
    }
    static JsonNode array(JsonNode node) { if(node==null || !node.isArray())throw ExamDraftException.invalid(); return node; }
    static String text(JsonNode node, boolean nullable) {
        if(nullable && (node==null || node.isNull()))return null;
        if(node==null || !node.isString())throw ExamDraftException.invalid(); return node.asString();
    }
    static int integer(JsonNode node) {
        if(node==null || !node.isIntegralNumber() || !node.canConvertToInt())throw ExamDraftException.invalid(); return node.asInt();
    }
    static UUID uuid(JsonNode node, boolean nullable) {
        var raw=text(node,nullable); if(raw==null)return null;
        try { var id=UUID.fromString(raw); if(!id.toString().equalsIgnoreCase(raw))throw ExamDraftException.invalid(); return id; }
        catch(IllegalArgumentException e){throw ExamDraftException.invalid();}
    }
}
