package com.toeic.backend.system.infrastructure.migration;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

/** DB mới riêng cho mỗi ca; SQL gốc tạo history, không UPDATE/DELETE/INSERT history thủ công. */
@EnabledIfEnvironmentVariable(named="SPRING_DATASOURCE_URL", matches="jdbc:postgresql:.*")
class MigrationCompatibilityIntegrationTest {
    static String v33,settings,rag;
    static final List<String> DATABASES=new ArrayList<>();

    static PGSimpleDataSource source(String database) {
        var source=new PGSimpleDataSource();source.setUrl(System.getenv("SPRING_DATASOURCE_URL"));
        source.setUser(System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME","postgres"));
        source.setPassword(System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD",System.getenv().getOrDefault("DB_PASSWORD","")));
        if(database!=null)source.setDatabaseName(database);
        return source;
    }
    static String database(String template) throws Exception {
        String name="toeic_migration_it_"+UUID.randomUUID().toString().replace("-","");
        try(var c=source(null).getConnection();var s=c.createStatement()) {
            s.execute("CREATE DATABASE "+name+(template==null?"":" TEMPLATE "+template));
        }
        DATABASES.add(name);return name;
    }
    static Flyway original(String database,String lineage,String target) {
        return Flyway.configure().dataSource(source(database)).locations("classpath:db/migration","classpath:db/history/"+lineage,"classpath:db/history/v35/admin03")
                .target(target).cleanDisabled(true).load();
    }
    @BeforeAll static void seedOriginalHistories() throws Exception {
        v33=database(null);original(v33,"rag","33").migrate();
        settings=database(null);original(settings,"settings","34").migrate();
        rag=database(null);original(rag,"rag","34").migrate();
    }
    static Flyway run(String database,MockEnvironment environment) throws Exception {
        return new MigrationConfiguration().flyway(source(database),environment);
    }
    static void sql(String database,String query) throws Exception {
        try(var c=source(database).getConnection();var s=c.createStatement()){s.execute(query);}
    }
    static String snapshot(String database) throws Exception {
        var all=new StringBuilder();
        try(var c=source(database).getConnection();var s=c.createStatement()) {
            s.execute("SET TIME ZONE 'UTC'");
            all.append(MigrationSchema.signature(c));
            var names=new ArrayList<String>();
            try(var rows=s.executeQuery("SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename")){while(rows.next())names.add(rows.getString(1));}
            for(String name:names)try(var rows=s.executeQuery("SELECT to_jsonb(t)::text FROM public.\""+name.replace("\"","\"\"")+"\" t ORDER BY to_jsonb(t)::text COLLATE \"C\"")) {
                all.append(name).append('\n');while(rows.next())all.append(rows.getString(1)).append('\n');
            }
        }
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(all.toString().getBytes(StandardCharsets.UTF_8)));
    }
    @ParameterizedTest @ValueSource(strings={"empty","v33","settings","rag","settings-admin35","rag-admin35","rag-rag35","settings37","rag37","rag-rag36","rag-rag37","rag-rag38"})
    void historiesConvergeAndRestartWithoutRewritingOldRows(String kind) throws Exception {
        String db=database(kind.equals("empty")?null:kind.equals("v33")?v33:kind.startsWith("settings")?settings:rag);
        if(kind.endsWith("-admin35") || kind.endsWith("37") || kind.startsWith("rag-rag")) {
            String target=kind.endsWith("-admin35")?"35":kind.startsWith("rag-rag")?kind.substring(7):"37";
            Flyway.configure().dataSource(source(db)).locations("classpath:db/migration",
                "classpath:db/history/"+(kind.startsWith("settings")?"settings":"rag"),
                "classpath:db/history/v35/"+(kind.startsWith("rag-rag")?"rag":"admin03")).target(target).cleanDisabled(true).load().migrate();
        }
        String oldHistory="";
        if(!kind.equals("empty")) {
            sql(db,"INSERT INTO system_settings(setting_key,setting_value) VALUES('compatibility.retained','42')");
            if(kind.startsWith("settings"))sql(db,"UPDATE system_settings SET revision=7 WHERE setting_key='compatibility.retained'; DELETE FROM role_permissions WHERE permission_id=(SELECT permission_id FROM permissions WHERE code='system:settings:manage')");
            oldHistory=history(db);
        }
        var flyway=run(db,new MockEnvironment());flyway.validate();
        assertEquals("39",flyway.info().current().getVersion().toString());
        assertEquals(1,Arrays.stream(flyway.info().all()).filter(m->m.getVersion().toString().equals("34")).count());
        assertEquals(1,Arrays.stream(flyway.info().all()).filter(m->m.getVersion().toString().equals("35")).count());
        if(!kind.equals("empty")) {
            assertTrue(history(db).startsWith(oldHistory));
            try(var c=source(db).getConnection();var s=c.createStatement();var r=s.executeQuery("SELECT setting_value,revision FROM system_settings WHERE setting_key='compatibility.retained'")) {
                assertTrue(r.next());assertEquals("42",r.getString(1));assertEquals(kind.startsWith("settings")?7:0,r.getLong(2));
            }
        }
        String before=snapshot(db);run(db,new MockEnvironment()).validate();assertEquals(before,snapshot(db));
    }
    static String history(String database) throws Exception {
        var out=new StringBuilder();
        try(var c=source(database).getConnection();var s=c.createStatement();var r=s.executeQuery("SELECT to_jsonb(h)::text FROM flyway_schema_history h ORDER BY installed_rank")) {
            while(r.next())out.append(r.getString(1)).append('\n');
        }
        return out.toString();
    }
    @ParameterizedTest @ValueSource(strings={"spring.flyway.locations","spring.flyway.enabled","spring.flyway.baseline-on-migrate","spring.flyway.out-of-order","spring.flyway.validate-on-migrate","spring.flyway.ignore-migration-patterns","spring.flyway.schemas","spring.flyway.table","spring.jpa.hibernate.ddl-auto","spring.sql.init.mode","spring.datasource.hikari.connection-init-sql"})
    void configurationIsRejectedBeforeMutation(String property) throws Exception {
        String db=database(v33),before=snapshot(db);
        String value=switch(property){case "spring.flyway.enabled","spring.flyway.validate-on-migrate"->"false";case "spring.jpa.hibernate.ddl-auto"->"update";case "spring.sql.init.mode"->"always";default->"unsafe";};
        assertThrows(Exception.class,()->run(db,new MockEnvironment().withProperty(property,value)));
        assertEquals(before,snapshot(db));
    }
    @ParameterizedTest @ValueSource(strings={"base-column","changed-check","rag-index","extra-schema","partial-settings","unknown-version","failed-history","gapped-history","wrong-checksum","empty-domain","history-column"})
    void unknownHistoriesAndSchemaDriftAreRejectedBeforeMutation(String kind) throws Exception {
        String db;
        if(Set.of("failed-history","gapped-history","wrong-checksum").contains(kind)) {
            db=database(null);Path directory=Files.createTempDirectory("toeic-migration-negative-");
            if(kind.equals("wrong-checksum")) { db=database(v33);Files.writeString(directory.resolve("V34__rag_chat_runtime.sql"),"SELECT 1; -- deliberately unknown checksum in a new test database\n"); }
            else if(kind.equals("gapped-history")) {Files.writeString(directory.resolve("V1__a.sql"),"SELECT 1;");Files.writeString(directory.resolve("V3__b.sql"),"SELECT 1;");}
            else {
                Files.writeString(directory.resolve("V1__failed.sql"),"CREATE TABLE incomplete_marker(id integer); SELECT 1/0;");
                Files.writeString(directory.resolve("V1__failed.sql.conf"),"executeInTransaction=false\n");
            }
            var fixture=Flyway.configure().dataSource(source(db)).locations("classpath:db/migration","filesystem:"+directory).target(kind.equals("wrong-checksum")?"34":"latest").cleanDisabled(true);
            if(!kind.equals("wrong-checksum"))fixture.locations("filesystem:"+directory);
            var original=fixture.load();
            if(kind.equals("failed-history"))assertThrows(Exception.class,original::migrate);else original.migrate();
        } else {
            db=database(kind.equals("empty-domain")?null:kind.equals("rag-index")?rag:v33);
            switch(kind) {
                case "base-column"->sql(db,"ALTER TABLE users ADD COLUMN unexpected int");
                case "changed-check"->sql(db,"ALTER TABLE learning_contents DROP CONSTRAINT chk_content_visibility; ALTER TABLE learning_contents ADD CONSTRAINT chk_content_visibility CHECK(visibility IN ('PUBLIC','AUTHENTICATED','PRIVATE','UNSAFE'))");
                case "rag-index"->sql(db,"DROP INDEX idx_ai_chat_requests_user_time");
                case "extra-schema"->sql(db,"CREATE SCHEMA unexpected");
                case "partial-settings"->sql(db,"ALTER TABLE system_settings ADD COLUMN revision bigint NOT NULL DEFAULT 0");
                case "empty-domain"->sql(db,"CREATE DOMAIN unexpected AS integer");
                case "history-column"->sql(db,"ALTER TABLE flyway_schema_history ADD COLUMN unexpected int");
                case "unknown-version"->{Path directory=Files.createTempDirectory("toeic-migration-future-");Files.writeString(directory.resolve("V99__unknown.sql"),"SELECT 1;");Flyway.configure().dataSource(source(db)).locations("classpath:db/migration","classpath:db/history/rag","classpath:db/history/v35/admin03","filesystem:"+directory).cleanDisabled(true).load().migrate();}
                default->throw new AssertionError(kind);
            }
        }
        String database=db,before=snapshot(db);
        assertThrows(Exception.class,()->run(database,new MockEnvironment()));assertEquals(before,snapshot(db));
    }
    @ParameterizedTest @ValueSource(strings={"settings-rag35","soft-column-only","soft-index-drift","moderation-drift","v35-checksum","v35-script"})
    void v35MismatchesAreRejectedBeforeMutation(String kind) throws Exception {
        String db=database(kind.equals("settings-rag35")?settings:rag);
        String v35=kind.equals("soft-column-only")||kind.equals("moderation-drift")?"admin03":"rag";
        if(kind.equals("v35-checksum") || kind.equals("v35-script")) {
            Path directory=Files.createTempDirectory("toeic-v35-negative-");
            String sql=Files.readString(Path.of("src/main/resources/db/history/v35/rag/V35__ai_conversation_delete_cascade.sql"));
            Files.writeString(directory.resolve(kind.equals("v35-script")?"V35__unknown_identity.sql":"V35__ai_conversation_delete_cascade.sql"),sql+(kind.equals("v35-checksum")?"\n-- deliberately invalid checksum fixture\n":""));
            Flyway.configure().dataSource(source(db)).locations("classpath:db/migration","classpath:db/history/rag","filesystem:"+directory).target("35").cleanDisabled(true).load().migrate();
        } else {
            Flyway.configure().dataSource(source(db)).locations("classpath:db/migration","classpath:db/history/"+(kind.equals("settings-rag35")?"settings":"rag"),
                "classpath:db/history/v35/"+v35).target("35").cleanDisabled(true).load().migrate();
            switch(kind) {
                case "soft-column-only"->sql(db,"ALTER TABLE ai_conversations ADD COLUMN deleted_at timestamptz");
                case "soft-index-drift"->sql(db,"DROP INDEX idx_ai_conversations_active_user; CREATE INDEX idx_ai_conversations_active_user ON ai_conversations(user_id,updated_at DESC)");
                case "moderation-drift"->sql(db,"ALTER TABLE moderation_versions ADD COLUMN unexpected int");
            }
        }
        String before=snapshot(db);
        assertThrows(Exception.class,()->run(db,new MockEnvironment()));assertEquals(before,snapshot(db));
    }
    @Test void concurrentStartupRunsOnlyOneV34() throws Exception {
        String db=database(v33);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(()->run(db,new MockEnvironment()));var second=pool.submit(()->run(db,new MockEnvironment()));
            first.get(60,TimeUnit.SECONDS).validate();second.get(60,TimeUnit.SECONDS).validate();
        }
        try(var c=source(db).getConnection();var s=c.createStatement();var r=s.executeQuery("SELECT count(*) FROM flyway_schema_history WHERE version='34'")){r.next();assertEquals(1,r.getInt(1));}
    }
    @Test void lockTimeoutDoesNotMutate() throws Exception {
        String db=database(v33),before=snapshot(db);
        try(var c=source(db).getConnection();var s=c.createStatement()) {
            s.execute("SELECT pg_advisory_lock("+MigrationConfiguration.LOCK_KEY+")");
            assertThrows(Exception.class,()->run(db,new MockEnvironment().withProperty("app.migrations.lock-timeout-ms","100")));
            s.execute("SELECT pg_advisory_unlock("+MigrationConfiguration.LOCK_KEY+")");
        }
        assertEquals(before,snapshot(db));
    }
    @AfterAll static void reportDatabaseNames() {System.out.println("Migration test DBs retained for evidence: "+String.join(",",DATABASES));}
}
