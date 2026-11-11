package com.toeic.backend.questionbank.infrastructure;

import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Publication candidates only. Selection/count/media policy and actor authorization belong to the caller. */
@Repository
@Transactional(propagation = Propagation.MANDATORY, readOnly = true)
public class PracticePublicationRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public PracticePublicationRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }
    public record Candidate(UUID questionId, UUID questionVersionId, UUID groupVersionId, int partId,
                            int partNumber, Integer topicId, String difficulty, OffsetDateTime questionCreatedAt) { }
    public record PoolMember(Candidate candidate, UUID groupId, boolean eligible, Integer groupOrder) { }

    /** Keep every current member of a group, including invalid/hidden members, until the unit is validated. */
    public List<PoolMember> practicePool(Set<Integer> partNumbers, Integer topic, String difficulty, OffsetDateTime now) {
        var params = new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                .addValue("parts",partNumbers).addValue("now",now)
                .addValue("topic",topic,java.sql.Types.INTEGER).addValue("difficulty",difficulty,java.sql.Types.VARCHAR);
        return jdbc.query("""
                select q.question_id,v.question_version_id,v.group_version_id,q.group_id,q.part_id,
                       p.part_number,q.topic_id,v.difficulty,q.created_at,v.group_question_order,
                       (q.status='PUBLISHED' and v.status='PUBLISHED'
                        and v.published_at is not null and v.published_at<=:now and v.archived_at is null
                        and t.code='SINGLE_CHOICE' and v.question_text ~ '[^[:space:]]'
                        and (:topic is null or q.topic_id=:topic)
                        and (:difficulty is null or v.difficulty=:difficulty)
                        and p.part_number between 1 and 7
                        and ((p.part_number in (1,2,5) and q.group_id is null and v.group_version_id is null)
                         or (p.part_number in (3,4,6,7) and g.group_id is not null
                          and gv.group_id=g.group_id and g.status='PUBLISHED' and gv.status='PUBLISHED'
                          and g.current_published_version_id=gv.group_version_id
                          and gv.published_at is not null and gv.published_at<=:now and gv.archived_at is null
                          and not exists(
                            select 1 from questions sibling
                            left join question_versions sv on sv.question_version_id=sibling.current_published_version_id
                            left join question_group_versions sg on sg.group_version_id=sv.group_version_id
                            where sibling.group_id=g.group_id
                              and ((sibling.status in ('PUBLISHED','ARCHIVED')
                                    and (sv.question_version_id is null or sv.group_version_id is null or sg.group_id<>g.group_id))
                                or (sv.question_version_id is null and exists(
                                    select 1 from question_versions history where history.question_id=sibling.question_id
                                      and history.group_version_id=gv.group_version_id and history.published_at is not null))))))
                        and (select count(*) from question_choices c where c.question_version_id=v.question_version_id
                             and c.is_correct)=1
                        and (select count(*) from question_choices c where c.question_version_id=v.question_version_id)
                            =case when p.part_number=2 then 3 else 4 end
                        and not exists(select 1 from question_choices c where c.question_version_id=v.question_version_id
                             and (c.content !~ '[^[:space:]]' or c.display_order<1
                               or c.display_order>case when p.part_number=2 then 3 else 4 end
                               or c.choice_label<>chr(64+c.display_order)))) as eligible
                from questions q join question_versions v
                  on v.question_id=q.question_id and v.question_version_id=q.current_published_version_id
                join toeic_parts p on p.part_id=q.part_id join question_types t on t.question_type_id=q.question_type_id
                left join question_groups g on g.group_id=q.group_id
                left join question_group_versions gv on gv.group_version_id=v.group_version_id
                where p.part_number in (:parts) or v.group_version_id in (
                  select v2.group_version_id from questions q2
                  join question_versions v2 on v2.question_version_id=q2.current_published_version_id
                  join toeic_parts p2 on p2.part_id=q2.part_id where p2.part_number in (:parts))
                order by p.part_number,q.created_at,q.question_id
                """,params,(r,n)->new PoolMember(new Candidate(r.getObject("question_id",UUID.class),
                r.getObject("question_version_id",UUID.class),r.getObject("group_version_id",UUID.class),r.getInt("part_id"),
                r.getInt("part_number"),(Integer)r.getObject("topic_id"),r.getString("difficulty"),r.getObject("created_at",OffsetDateTime.class)),
                r.getObject("group_id",UUID.class),r.getBoolean("eligible"),(Integer)r.getObject("group_question_order")));
    }

    private void lockRows(String table, String id, Collection<UUID> ids, String order) {
        if (!ids.isEmpty()) jdbc.query("select "+id+" from "+table+" where "+id+" in (:ids) order by "+order+" for share",
                Map.of("ids",ids),(r,n)->r.getObject(1));
    }
    public void lockSelected(List<PoolMember> selected) {
        var groups=selected.stream().map(PoolMember::groupId).filter(Objects::nonNull).distinct().toList();
        var gv=selected.stream().map(m->m.candidate().groupVersionId()).filter(Objects::nonNull).distinct().toList();
        var q=selected.stream().map(m->m.candidate().questionId()).distinct().toList();
        var v=selected.stream().map(m->m.candidate().questionVersionId()).distinct().toList();
        lockRows("question_groups","group_id",groups,"group_id::text");
        lockRows("question_group_versions","group_version_id",gv,"group_id::text,version_number");
        lockRows("questions","question_id",q,"question_id::text");
        lockRows("question_versions","question_version_id",v,"question_id::text,version_number");
        var media=new HashSet<UUID>(jdbc.query("select resource_id from question_resources where question_version_id in (:ids)",
                Map.of("ids",v),(r,n)->r.getObject(1,UUID.class)));
        if(!gv.isEmpty()) media.addAll(jdbc.query("select resource_id from question_group_version_resources where group_version_id in (:ids)",
                Map.of("ids",gv),(r,n)->r.getObject(1,UUID.class)));
        lockRows("media_resources","resource_id",media,"resource_id::text");
    }
    public List<Candidate> findPublished(Set<Integer> partIds, OffsetDateTime now) {
        if (partIds == null || partIds.isEmpty() || partIds.stream().anyMatch(id -> id == null || id < 1)) {
            throw new IllegalArgumentException("Invalid Part IDs");
        }
        return jdbc.query("""
                select q.question_id, v.question_version_id, v.group_version_id, q.part_id,
                       p.part_number, q.topic_id, v.difficulty, q.created_at
                from questions q join question_versions v
                  on v.question_id=q.question_id and v.question_version_id=q.current_published_version_id
                join toeic_parts p on p.part_id=q.part_id
                left join question_groups g on g.group_id=q.group_id
                left join question_group_versions gv on gv.group_version_id=v.group_version_id
                where q.part_id in (:parts) and p.part_number between 1 and 7
                  and q.status='PUBLISHED' and v.status='PUBLISHED'
                  and v.published_at is not null and v.published_at<=:now and v.archived_at is null
                  and ((q.group_id is null and v.group_version_id is null)
                    or (g.group_id is not null and gv.group_id=g.group_id
                        and g.status='PUBLISHED' and gv.status='PUBLISHED'
                        and g.current_published_version_id=gv.group_version_id
                        and gv.published_at is not null and gv.published_at<=:now and gv.archived_at is null))
                order by p.part_number, q.created_at, q.question_id
                """, Map.of("parts", Set.copyOf(partIds), "now", Objects.requireNonNull(now)), (r, n) -> new Candidate(
                r.getObject("question_id", UUID.class), r.getObject("question_version_id", UUID.class),
                r.getObject("group_version_id", UUID.class), r.getInt("part_id"), r.getInt("part_number"),
                (Integer) r.getObject("topic_id"), r.getString("difficulty"), r.getObject("created_at", OffsetDateTime.class)));
    }
}
