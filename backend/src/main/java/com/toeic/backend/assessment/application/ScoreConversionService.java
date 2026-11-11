package com.toeic.backend.assessment.application;

import com.toeic.backend.assessment.infrastructure.ExamRepository;
import java.time.*;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Nhận bảng quy đổi có nguồn do Admin cung cấp; không tự suy ra điểm TOEIC từ tỷ lệ đúng. */
@Service
public class ScoreConversionService {
    private final ExamRepository db;private final Clock clock;
    public ScoreConversionService(ExamRepository db,Clock clock){this.db=db;this.clock=clock;}
    public record Table(String name,List<Integer> listening,List<Integer> reading) {}
    @Transactional(readOnly=true)
    @PreAuthorize("hasAnyAuthority('test:manage:self','test:manage:any')")
    public Object list(){return db.rows("select score_conversion_table_id,name,version_number,status from score_conversion_tables order by created_at desc");}
    @Transactional
    @PreAuthorize("hasAuthority('score_conversion:manage') and @examAccess.actor(#actorId)")
    public UUID create(UUID actorId,Table table){
        if(table==null||table.name()==null||table.name().isBlank()||table.name().length()>150)throw ExamException.invalid("Cần tên bảng và nguồn quy đổi (tối đa 150 ký tự).");
        for(var scores:Arrays.asList(table.listening(),table.reading())){if(scores==null||scores.size()!=101)throw ExamException.invalid("Mỗi kỹ năng cần đủ 101 điểm ứng với số đúng 0–100.");int last=5;for(var s:scores){if(s==null||s<last||s>495||s%5!=0)throw ExamException.invalid("Điểm phải từ 5–495, bội số 5 và không giảm.");last=s;}}
        UUID id=UUID.randomUUID();var now=OffsetDateTime.now(clock);db.write("insert into score_conversion_tables values(?,?,1,'ACTIVE',?,?)",id,table.name(),now,now);
        for(int i=0;i<=100;i++){db.write("insert into score_conversion_rules values(?,?,'LISTENING',?,?)",UUID.randomUUID(),id,i,table.listening().get(i));db.write("insert into score_conversion_rules values(?,?,'READING',?,?)",UUID.randomUUID(),id,i,table.reading().get(i));}
        db.audit(actorId,"SCORE_CONVERSION_CREATED",id);return id;
    }
}
