package com.toeic.backend.assessment.api;

import com.fasterxml.jackson.annotation.*;
import com.toeic.backend.assessment.application.*;
import java.util.*;
import tools.jackson.databind.JsonNode;

/** Local allowlist and duplicate-field validation, without changing global Jackson behavior. */
public class StartPracticeRequest {
    private boolean sourceSeen,practiceSeen,sectionSeen;
    private JsonNode source;
    private Filter filter;
    private SectionFilter section;
    @JsonSetter("sourceType") public void source(JsonNode value) { if(sourceSeen)throw PracticeException.invalid(); sourceSeen=true;source=value; }
    @JsonSetter("practice") public void practice(Filter value) { if(practiceSeen)throw PracticeException.invalid();practiceSeen=true;filter=value; }
    @JsonSetter("section") public void section(SectionFilter value) { if(sectionSeen)throw PracticeException.invalid();sectionSeen=true;section=value; }
    @JsonAnySetter public void unknown(String name,JsonNode value) { throw PracticeException.invalid(); }
    public PracticeStartQuery query() {
        if(isSection()||filter==null)throw PracticeException.invalid();
        return filter.query();
    }
    public boolean isSection() {
        if(!sourceSeen||source==null||!source.isString())throw PracticeException.invalid();
        if("PRACTICE_SNAPSHOT".equals(source.asString())&&practiceSeen&&!sectionSeen&&filter!=null)return false;
        if("SECTION_SNAPSHOT".equals(source.asString())&&sectionSeen&&!practiceSeen&&section!=null)return true;
        throw PracticeException.invalid();
    }
    public SectionStartQuery sectionQuery() { if(!isSection())throw PracticeException.invalid();return section.query(); }
    public static class SectionFilter {
        private final Map<String,JsonNode> values=new HashMap<>();
        @JsonAnySetter public void value(String name,JsonNode value) {
            if(!Set.of("type","questionCount","durationMinutes").contains(name)||values.containsKey(name))throw PracticeException.invalid();
            values.put(name,value);
        }
        SectionStartQuery query() {
            var type=values.get("type");
            if(type==null||!type.isString())throw PracticeException.invalid();
            return new SectionStartQuery(type.asString(),Filter.integer(values.get("questionCount"),false),Filter.integer(values.get("durationMinutes"),false));
        }
    }
    public static class Filter {
        private final Map<String,JsonNode> values=new HashMap<>();
        @JsonAnySetter public void value(String name,JsonNode value) {
            if(!Set.of("partNumbers","questionCount","topicId","difficulty").contains(name)||values.containsKey(name))throw PracticeException.invalid();
            values.put(name,value);
        }
        PracticeStartQuery query() {
            var parts=values.get("partNumbers");
            if(parts==null||!parts.isArray())throw PracticeException.invalid();
            var numbers=new ArrayList<Integer>();for(var part:parts)numbers.add(integer(part,false));
            var difficulty=values.get("difficulty");
            if(difficulty!=null&&!difficulty.isNull()&&!difficulty.isString())throw PracticeException.invalid();
            return new PracticeStartQuery(numbers,integer(values.get("questionCount"),false),integer(values.get("topicId"),true),
                    difficulty==null||difficulty.isNull()?null:difficulty.asString());
        }
        private static Integer integer(JsonNode value,boolean nullable) {
            if(value==null||value.isNull()) { if(nullable)return null;throw PracticeException.invalid(); }
            if(!value.isIntegralNumber()||!value.canConvertToInt())throw PracticeException.invalid();
            return value.asInt();
        }
    }
}
