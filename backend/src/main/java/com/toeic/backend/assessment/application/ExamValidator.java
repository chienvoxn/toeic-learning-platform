package com.toeic.backend.assessment.application;

import com.toeic.backend.assessment.domain.ExamDocument;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class ExamValidator {
    private boolean blank(String s) { return s==null || s.isBlank(); }
    public List<ExamException.Issue> validate(ExamDocument d) {
        var errors=new ArrayList<ExamException.Issue>();
        if(d==null) return List.of(new ExamException.Issue("document","Thiếu dữ liệu đề."));
        if(blank(d.title()) || d.title().length()>255) errors.add(new ExamException.Issue("title","Tên đề từ 1–255 ký tự."));
        if(d.testType()==null || !Set.of("PRACTICE","LISTENING","READING","FULL").contains(d.testType())) errors.add(new ExamException.Issue("testType","Loại đề không hợp lệ."));
        if(d.durationSeconds()==null || d.durationSeconds()<60 || d.durationSeconds()>14400) errors.add(new ExamException.Issue("durationSeconds","Thời gian từ 60 đến 14400 giây."));
        if(d.maxAttempts()!=null && d.maxAttempts()<1) errors.add(new ExamException.Issue("maxAttempts","Để trống hoặc nhập số nguyên dương."));
        if(d.questions()==null || d.questions().isEmpty() || d.questions().size()>200) { errors.add(new ExamException.Issue("questions","Đề phải có 1–200 câu.")); return errors; }
        var groups=new HashMap<String,ExamDocument.Group>();
        if(d.groups()!=null) for(int i=0;i<d.groups().size();i++) {
            var g=d.groups().get(i); String field="groups["+i+"]";
            if(g==null || blank(g.key()) || g.key().length()>80 || !Set.of(3,4,6,7).contains(g.partNumber())) { errors.add(new ExamException.Issue(field,"Nhóm phải có mã và Part 3/4/6/7.")); continue; }
            if(groups.putIfAbsent(g.key(),g)!=null) errors.add(new ExamException.Issue(field,"Mã nhóm bị trùng."));
            if(g.partNumber()<=4 && g.audioId()==null || g.partNumber()>=6 && blank(g.passageText())) errors.add(new ExamException.Issue(field,"Nhóm nghe cần audio; nhóm đọc cần đoạn văn."));
        }
        var counts=new int[8]; var members=new HashMap<String,Integer>(); var closed=new HashSet<String>(); String last=null; int lastPart=0;
        for(int i=0;i<d.questions().size();i++) {
            var q=d.questions().get(i);String field="questions["+i+"]";
            if(q==null || q.partNumber()<1 || q.partNumber()>7) { errors.add(new ExamException.Issue(field,"Part phải từ 1–7.")); continue; }
            int p=q.partNumber();counts[p]++;
            if(p<lastPart) errors.add(new ExamException.Issue(field,"Sắp câu theo Part tăng dần.")); lastPart=p;
            if(blank(q.questionText()) || q.questionText().length()>20000) errors.add(new ExamException.Issue(field+".questionText","Nội dung câu từ 1–20000 ký tự."));
            int n=p==2?3:4;
            if(q.choices()==null || q.choices().size()!=n || q.choices().stream().anyMatch(x->blank(x)||x.length()>5000)) errors.add(new ExamException.Issue(field+".choices","Cần "+n+" lựa chọn có nội dung."));
            if(q.answer()==null || q.answer().length()!=1 || q.answer().charAt(0)<'A' || q.answer().charAt(0)>='A'+n) errors.add(new ExamException.Issue(field+".answer","Chọn đúng một đáp án A–"+(char)('A'+n-1)+"."));
            boolean grouped=Set.of(3,4,6,7).contains(p);var g=groups.get(q.groupKey());
            if(grouped && (g==null || g.partNumber()!=p) || !grouped && !blank(q.groupKey())) errors.add(new ExamException.Issue(field+".groupKey","Nhóm không tồn tại hoặc không khớp Part."));
            if(p<=2 && q.audioId()==null) errors.add(new ExamException.Issue(field+".audioId","Thiếu audio."));
            if(p==1 && q.imageId()==null) errors.add(new ExamException.Issue(field+".imageId","Part 1 cần ảnh."));
            if(!Objects.equals(last,q.groupKey())) { if(last!=null)closed.add(last); if(q.groupKey()!=null&&closed.contains(q.groupKey()))errors.add(new ExamException.Issue(field,"Các câu cùng nhóm phải liên tiếp."));last=q.groupKey(); }
            if(q.groupKey()!=null)members.merge(q.groupKey(),1,Integer::sum);
        }
        for(var g:groups.values()) { int n=members.getOrDefault(g.key(),0); if((g.partNumber()==3||g.partNumber()==4)&&n!=3 || g.partNumber()==6&&n!=4 || g.partNumber()==7&&(n<2||n>5)) errors.add(new ExamException.Issue("groups."+g.key(),"Part 3/4: 3 câu; Part 6: 4 câu; Part 7: 2–5 câu.")); }
        int[] standard={0,6,25,39,30,30,16,54};
        if(Set.of("FULL","LISTENING","READING").contains(Objects.toString(d.testType(),""))) {
            for(int p=1;p<=7;p++) { int expected="FULL".equals(d.testType()) || (p<=4)=="LISTENING".equals(d.testType())?standard[p]:0; if(counts[p]!=expected)errors.add(new ExamException.Issue("part"+p,"Cần "+expected+" câu cho loại đề này.")); }
            int seconds="FULL".equals(d.testType())?7200:"LISTENING".equals(d.testType())?2700:4500;
            if(!Objects.equals(seconds,d.durationSeconds()))errors.add(new ExamException.Issue("durationSeconds","Loại đề này cần "+seconds+" giây."));
            if(!"LISTENING".equals(d.testType())) {
                var readingGroups=groups.values().stream().filter(g->g.partNumber()==7).toList();
                long single=readingGroups.stream().filter(g->members.getOrDefault(g.key(),0)<5).count();
                long multiple=readingGroups.stream().filter(g->members.getOrDefault(g.key(),0)==5).count();
                int singleQuestions=readingGroups.stream().mapToInt(g->{int n=members.getOrDefault(g.key(),0);return n<5?n:0;}).sum();
                if(single!=10||multiple!=5||singleQuestions!=29)errors.add(new ExamException.Issue("groups","Reading chuẩn cần 10 nhóm Part 7 đơn (29 câu) và 5 nhóm đa văn bản (25 câu)."));
            }
        }
        return errors;
    }
}
