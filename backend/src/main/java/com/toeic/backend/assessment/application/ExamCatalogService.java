package com.toeic.backend.assessment.application;

import com.toeic.backend.assessment.infrastructure.ExamRepository;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExamCatalogService {
    private final ExamRepository db;
    public ExamCatalogService(ExamRepository db){this.db=db;}
    public record Part(int partNumber,String name,int questionCount,int estimatedMinutes) {}
    public record Test(UUID id,String title,String description,String testType,int totalQuestions,int durationMinutes,List<Integer> parts,List<Part> availableParts,boolean remote) {}
    private Test view(Map<String,Object> v) {
        UUID id=(UUID)v.get("test_version_id");
        var parts=db.rows("select p.part_number,p.name,count(*) n from test_questions tq join question_versions qv using(question_version_id) join questions q using(question_id) join toeic_parts p using(part_id) where tq.test_version_id=? group by p.part_number,p.name order by p.part_number",id).stream().map(p->new Part((int)p.get("part_number"),(String)p.get("name"),((Number)p.get("n")).intValue(),0)).toList();
        return new Test(id,(String)v.get("title"),(String)v.get("description"),(String)v.get("test_type"),(int)v.get("total_questions"),(int)v.get("duration_seconds")/60,parts.stream().map(Part::partNumber).toList(),parts,true);
    }
    @Transactional(readOnly=true) public List<Test> list(int page) { if(page<0||page>100000)throw ExamException.invalid("Trang không hợp lệ.");return db.rows("select * from test_versions where status='PUBLISHED' and draft_document is not null order by published_at desc,test_version_id limit 50 offset ?",page*50).stream().map(this::view).toList(); }
    @Transactional(readOnly=true) public Test get(UUID id){return view(db.one("select * from test_versions where test_version_id=? and status='PUBLISHED' and draft_document is not null",id));}
}
