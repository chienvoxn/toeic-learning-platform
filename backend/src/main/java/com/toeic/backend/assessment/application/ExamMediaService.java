package com.toeic.backend.assessment.application;

import com.toeic.backend.assessment.infrastructure.ExamRepository;
import java.time.*;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lưu media nguyên tử trong PostgreSQL; cấp nội dung theo ownership/bài làm, không lộ storage path. */
@Service
public class ExamMediaService {
    private final ExamRepository db; private final ExamAccess access; private final Clock clock;
    public ExamMediaService(ExamRepository db,ExamAccess access,Clock clock){this.db=db;this.access=access;this.clock=clock;}
    public record Media(UUID resourceId,String resourceType,String mimeType,int fileSize) {}
    public record Download(byte[] data,String mimeType) {}
    @Transactional
    @PreAuthorize("hasAuthority('media:upload') and @examAccess.actor(#actorId)")
    public Media upload(UUID actorId,byte[] bytes) {
        if(bytes==null||bytes.length==0||bytes.length>20*1024*1024)throw ExamException.invalid("Media phải có kích thước từ 1 byte đến 20 MB.");
        String mime=detect(bytes);String type=mime.startsWith("image/")?"IMAGE":"AUDIO";UUID id=UUID.randomUUID();
        db.write("insert into media_resources(resource_id,resource_type,storage_url,mime_type,file_size,created_at,visibility,owner_id) values(?,?,?,?,?,?,'AUTHENTICATED',?)",id,type,"db:"+id,mime,bytes.length,OffsetDateTime.now(clock),actorId);
        db.write("insert into media_uploads values(?,?)",id,bytes);return new Media(id,type,mime,bytes.length);
    }
    private String detect(byte[] b) {
        if(b.length>=8&&(b[0]&255)==137&&b[1]==80&&b[2]==78&&b[3]==71&&b[4]==13&&b[5]==10&&b[6]==26&&b[7]==10)return "image/png";
        if(b.length>=3&&(b[0]&255)==255&&(b[1]&255)==216&&(b[2]&255)==255)return "image/jpeg";
        if(b.length>=12&&new String(b,0,4,java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")){
            var kind=new String(b,8,4,java.nio.charset.StandardCharsets.US_ASCII);if(kind.equals("WAVE"))return "audio/wav";if(kind.equals("WEBP"))return "image/webp";
        }
        if(b.length>=4&&b[0]=='O'&&b[1]=='g'&&b[2]=='g'&&b[3]=='S')return "audio/ogg";
        if(b.length>=3&&(b[0]=='I'&&b[1]=='D'&&b[2]=='3'||(b[0]&255)==255&&(b[1]&224)==224))return "audio/mpeg";
        throw ExamException.invalid("Chỉ nhận PNG/JPEG/WebP hoặc MP3/WAV/OGG có định dạng phù hợp; không nhận HTML/SVG.");
    }
    @Transactional(readOnly=true)
    @PreAuthorize("@examAccess.actor(#actorId)")
    public Download download(UUID actorId,UUID id) {
        var media=db.one("select m.*,u.data from media_resources m join media_uploads u using(resource_id) where resource_id=? and visibility in ('PUBLIC','AUTHENTICATED')",id);
        if(!actorId.equals(media.get("owner_id"))&&!access.has("test:manage:any")) {
            var refs=db.rows("""
                select 1 from attempts a join attempt_question_snapshots s on s.attempt_id=a.attempt_id
                where a.user_id=? and (exists(select 1 from question_resources r where r.question_version_id=s.question_version_id and r.resource_id=?)
                 or exists(select 1 from question_group_version_resources r where r.group_version_id=s.question_group_version_id and r.resource_id=?)) limit 1
                """,actorId,id,id);
            if(refs.isEmpty())throw ExamException.missing();
        }
        return new Download((byte[])media.get("data"),(String)media.get("mime_type"));
    }
}
