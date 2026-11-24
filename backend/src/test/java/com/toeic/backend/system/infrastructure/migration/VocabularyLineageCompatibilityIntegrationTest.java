package com.toeic.backend.system.infrastructure.migration;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import static com.toeic.backend.system.infrastructure.migration.MigrationCompatibilityIntegrationTest.*;
import static org.junit.jupiter.api.Assertions.*;

/** Dựng history bằng SQL gốc trong DB riêng; không sửa history để mô phỏng lineage. */
@EnabledIfEnvironmentVariable(named="SPRING_DATASOURCE_URL",matches="jdbc:postgresql:.*")
class VocabularyLineageCompatibilityIntegrationTest {
    static String canonical;
    static String[] locations(String lineage) {
        return new String[]{"classpath:db/migration","classpath:db/history/rag","classpath:db/history/v35/"+(lineage.equals("vocabulary")?"rag":"admin03"),"classpath:db/history/v36/"+lineage};
    }
    static void originalVocabulary(String db,String target) {
        Flyway.configure().dataSource(source(db)).locations(locations("vocabulary")).target(target).cleanDisabled(true).load().migrate();
    }
    @BeforeAll static void canonicalSqlSchema()throws Exception {
        String db=database(null);Flyway.configure().dataSource(source(db)).locations(locations("completion")).cleanDisabled(true).load().migrate();
        try(var c=source(db).getConnection()){canonical=MigrationSchema.normalizedSignature(c);}
    }
    static boolean exists(String db,String table)throws Exception {
        try(var c=source(db).getConnection();var s=c.createStatement();var r=s.executeQuery("SELECT to_regclass('public."+table+"') IS NOT NULL")){r.next();return r.getBoolean(1);}
    }
    static long sequence(String db)throws Exception {
        try(var c=source(db).getConnection();var s=c.createStatement();var r=s.executeQuery("SELECT last_value FROM permissions_permission_id_seq")){r.next();return r.getLong(1);}
    }
    static String retained(String db)throws Exception {
        var out=new StringBuilder();
        for(String table:List.of("users","vocabularies","vocabulary_review_requests")) {
            if(!exists(db,table))continue;
            try(var c=source(db).getConnection();var s=c.createStatement();var r=s.executeQuery("SELECT to_jsonb(t)::text FROM "+table+" t ORDER BY to_jsonb(t)::text")){while(r.next())out.append(r.getString(1)).append('\n');}
        }
        return out.toString();
    }
    @ParameterizedTest @ValueSource(strings={"36","37","38","38.1","39","40","41","42"})
    void vocabularyPrefixesConvergePreservingRowsHistorySequencesAndRevokedGrants(String target)throws Exception {
        String db=database(null);originalVocabulary(db,target);
        sql(db,"""
            INSERT INTO users(user_id,email,password_hash,full_name,status,created_at,updated_at)
              VALUES('b1111111-1111-1111-1111-111111111111','retained@example.invalid','fixture-only','Retained','ACTIVE',now(),now());
            INSERT INTO vocabularies(vocabulary_id,created_by,word,meaning,archived_at)
              VALUES('b2222222-2222-2222-2222-222222222222','b1111111-1111-1111-1111-111111111111','retained','giữ nguyên',now());
            SELECT setval('permissions_permission_id_seq',10000,true);
            """);
        if(exists(db,"vocabulary_review_requests"))sql(db,"INSERT INTO vocabulary_review_requests(user_id,request_key,vocabulary_id,rating,response) VALUES('b1111111-1111-1111-1111-111111111111','b3333333-3333-3333-3333-333333333333','b2222222-2222-2222-2222-222222222222',2,'{}')");
        boolean permission=Float.parseFloat(target)>=38;
        if(permission)sql(db,"DELETE FROM role_permissions WHERE permission_id=(SELECT permission_id FROM permissions WHERE code='vocabulary:learn:self')");
        String oldHistory=history(db),oldRows=retained(db);long oldSequence=sequence(db);
        var flyway=run(db,new MockEnvironment());flyway.validate();assertEquals("44",flyway.info().current().getVersion().toString());
        assertTrue(history(db).startsWith(oldHistory));assertEquals(oldRows,retained(db));assertTrue(sequence(db)>=oldSequence);
        try(var c=source(db).getConnection();var s=c.createStatement()) {
            assertEquals(canonical,MigrationSchema.normalizedSignature(c));
            if(permission)try(var r=s.executeQuery("SELECT count(*) FROM role_permissions rp JOIN permissions p USING(permission_id) WHERE p.code='vocabulary:learn:self'")){r.next();assertEquals(0,r.getInt(1));}
        }
        String migrated=snapshot(db);run(db,new MockEnvironment().withProperty("app.migrations.mode","validate")).validate();assertEquals(migrated,snapshot(db));
    }
    @ParameterizedTest @ValueSource(strings={"checksum36","unknown36","mixed37","mixed38","checksum-bridge","missing-bridge","column-drift","fk-drift","request-drift","permission-drift"})
    void unknownHistoryAndVocabularyDriftFailBeforeMutation(String kind)throws Exception {
        String db=database(null);
        if(Set.of("checksum36","unknown36","mixed37","mixed38","checksum-bridge").contains(kind)) {
            Path dir=Files.createTempDirectory("toeic-v36-negative-");
            for(var path:Files.list(Path.of("src/main/resources/db/history/v36/vocabulary")).toList()) {
                String name=path.getFileName().toString(),text=Files.readString(path);
                if(kind.equals("checksum36") && name.startsWith("V36__"))text+="\n-- invalid checksum fixture\n";
                if(kind.equals("unknown36") && name.startsWith("V36__"))name="V36__unknown_identity.sql";
                if(kind.equals("mixed37") && name.startsWith("V37__")){name="V37__complete_rag.sql";text=Files.readString(Path.of("src/main/resources/db/history/v36/completion",name));}
                if(kind.equals("mixed38") && name.startsWith("V38__")){name="V38__complete_moderation.sql";text=Files.readString(Path.of("src/main/resources/db/history/v36/completion",name));}
                if(kind.equals("checksum-bridge") && name.startsWith("V38_1__"))text+="\n-- invalid bridge checksum fixture\n";
                Files.writeString(dir.resolve(name),text);
            }
            Flyway.configure().dataSource(source(db)).locations("classpath:db/migration","classpath:db/history/rag","classpath:db/history/v35/rag","filesystem:"+dir)
                .target(kind.equals("mixed37")?"37":kind.equals("mixed38")?"38":kind.equals("checksum-bridge")?"38.1":"36").cleanDisabled(true).load().migrate();
        } else {
            originalVocabulary(db,"38");
            switch(kind) {
                case "missing-bridge"->{
                    Path dir=Files.createTempDirectory("toeic-v36-missing-bridge-");
                    for(String name:List.of("V36__vocabulary_archive_and_catalog_deck_deletion.sql","V37__vocabulary_review_idempotency.sql","V38__vocabulary_learning_permission.sql"))
                        Files.copy(Path.of("src/main/resources/db/history/v36/vocabulary",name),dir.resolve(name));
                    Flyway.configure().dataSource(source(db)).locations("classpath:db/migration","classpath:db/history/rag","classpath:db/history/v35/rag","filesystem:"+dir)
                        .target("40").cleanDisabled(true).load().migrate();
                }
                case "column-drift"->sql(db,"ALTER TABLE vocabularies ALTER COLUMN archived_at TYPE timestamp");
                case "fk-drift"->sql(db,"ALTER TABLE vocabulary_catalog_enrollments DROP CONSTRAINT fk_catalog_enrollment_personal_deck; ALTER TABLE vocabulary_catalog_enrollments ADD CONSTRAINT fk_catalog_enrollment_personal_deck FOREIGN KEY(personal_deck_id) REFERENCES vocabulary_decks(deck_id)");
                case "request-drift"->sql(db,"ALTER TABLE vocabulary_review_requests ADD COLUMN unexpected integer");
                case "permission-drift"->sql(db,"DELETE FROM role_permissions WHERE permission_id=(SELECT permission_id FROM permissions WHERE code='vocabulary:learn:self'); DELETE FROM permissions WHERE code='vocabulary:learn:self'");
                default->throw new AssertionError(kind);
            }
        }
        String before=snapshot(db);assertThrows(Exception.class,()->run(db,new MockEnvironment()));assertEquals(before,snapshot(db));
    }
    @Test void completion42CloneUpgradesWithoutInsertingOlderBridge()throws Exception {
        String source=database(null);Flyway.configure().dataSource(source(source)).locations(locations("completion")).target("42").cleanDisabled(true).load().migrate();
        String db=database(source),old=history(db);run(db,new MockEnvironment()).validate();assertTrue(history(db).startsWith(old));
        try(var c=source(db).getConnection();var s=c.createStatement();var r=s.executeQuery("SELECT count(*) FROM flyway_schema_history WHERE version='38.1'")){r.next();assertEquals(0,r.getInt(1));}
    }
    @Test void twoCoordinatorsCompleteOnlyOneBridge()throws Exception {
        String db=database(null);originalVocabulary(db,"38");
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->run(db,new MockEnvironment()));var b=pool.submit(()->run(db,new MockEnvironment()));
            a.get(60,TimeUnit.SECONDS).validate();b.get(60,TimeUnit.SECONDS).validate();
        }
        try(var c=source(db).getConnection();var s=c.createStatement();var r=s.executeQuery("SELECT count(*) FROM flyway_schema_history WHERE version IN ('38.1','43')")){r.next();assertEquals(2,r.getInt(1));}
    }
}
