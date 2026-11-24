package com.toeic.backend.system.infrastructure.migration;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

/** Đối chiếu metadata với schema chuẩn của SQL phát hành; không lấy chuẩn từ DB đích. */
public final class MigrationSchema {
    private MigrationSchema() {}
    private static final Set<String> RAG = Set.of("ai_indexed_versions", "ai_chat_requests", "ai_citation_snapshots");
    private static final Set<String> MODERATION = Set.of("moderation_versions", "moderation_contributors", "moderation_decisions");
    private static final Set<String> PASSWORD_RESET_GRANT = Set.of(
            "column|password_reset_otps|reset_token_hash|character varying(255)|false|||",
            "column|password_reset_otps|reset_token_expires_at|timestamp with time zone|false|||");

    public static void verifyEmpty(Connection connection) throws SQLException {
        if (!signature(connection).equals("extension|plpgsql|1.0|pg_catalog\n"))
            throw new IllegalStateException("Unexpected metadata in empty database");
    }

    public static void verifyHistoryTable(Connection connection) throws SQLException {
        try (var input=MigrationSchema.class.getResourceAsStream("/db/compatibility/history-table.txt")) {
            if (input==null || !historySignature(connection).equals(new String(input.readAllBytes(),StandardCharsets.UTF_8).replace("\r\n","\n")))
                throw new IllegalStateException("Unexpected history table schema");
        } catch (java.io.IOException e) { throw new IllegalStateException("Cannot read history schema evidence",e); }
    }

    public static String historySignature(Connection connection) throws SQLException {
        var output=new ArrayList<String>();
        String query="""
            SELECT 'relation|'||c.relkind::text||'|'||c.relpersistence::text FROM pg_class c WHERE c.oid='public.flyway_schema_history'::regclass
            UNION ALL
            SELECT 'column|'||a.attname||'|'||format_type(a.atttypid,a.atttypmod)||'|'||a.attnotnull||'|'||coalesce(pg_get_expr(d.adbin,d.adrelid),'')
            FROM pg_attribute a LEFT JOIN pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum
            WHERE a.attrelid='public.flyway_schema_history'::regclass AND a.attnum>0 AND NOT a.attisdropped
            UNION ALL
            SELECT 'constraint|'||conname||'|'||pg_get_constraintdef(oid)||'|'||convalidated FROM pg_constraint WHERE conrelid='public.flyway_schema_history'::regclass
            UNION ALL
            SELECT 'index|'||pg_get_indexdef(indexrelid)||'|'||indisvalid FROM pg_index WHERE indrelid='public.flyway_schema_history'::regclass
            """;
        try (var statement=connection.createStatement();var rows=statement.executeQuery(query)) {while(rows.next())output.add(rows.getString(1));}
        Collections.sort(output);return String.join("\n",output)+"\n";
    }

    public static void verify(Connection connection, boolean settings, boolean rag, boolean moderation, boolean softDelete, boolean examDrafts, boolean fullMock, boolean expiryWorker,
            boolean vocabularyArchive,boolean vocabularyReviews,boolean vocabularyPermission, boolean passwordResetGrant) throws SQLException {
        String expected;
        try (var input = MigrationSchema.class.getResourceAsStream("/db/compatibility/"+(expiryWorker?"schema-v42.txt":fullMock?"schema-v41.txt":examDrafts?"schema-v40.txt":"schema-v39.txt"))) {
            if (input == null) throw new IllegalStateException("Missing release schema evidence");
            expected = new String(input.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n","\n");
        } catch (java.io.IOException e) { throw new IllegalStateException("Cannot read release schema evidence", e); }
        expected=vocabularyEvidence(expected,vocabularyArchive,vocabularyReviews);
        var filtered = new StringBuilder();
        for (String line : expected.split("\n")) {
            String[] parts = line.split("\\|", -1);
            if ((!rag && RAG.contains(parts[1])) || (!moderation && MODERATION.contains(parts[1]))) continue;
            if (!settings && (line.startsWith("column|system_settings|revision|")
                    || line.startsWith("constraint|system_settings|ck_system_settings_revision|"))) continue;
            if (!softDelete && (line.startsWith("column|ai_conversations|deleted_at|")
                    || line.startsWith("index|ai_conversations|idx_ai_conversations_active_user|"))) continue;
            filtered.append(line).append('\n');
        }
        if (passwordResetGrant) {
            // V44 adds two nullable columns without changing any earlier migration evidence.
            var lines = new TreeSet<>(Arrays.asList(filtered.toString().split("\n")));
            lines.addAll(PASSWORD_RESET_GRANT);
            filtered = new StringBuilder(String.join("\n", lines)).append('\n');
        }
        if (!filtered.toString().equals(normalizedSignature(connection,fullMock))) throw new IllegalStateException("Schema differs from release migration evidence");
        // An existing feature's grants can have been revoked; never restore them.
        String codes = "'system:settings:manage','ai:chat:self','ai:knowledge:manage','content:review','content:publish','question:review','question:publish','test:manage:self','test:manage:any','test:review','test:publish','vocabulary:learn:self'";
        var actual = new HashSet<String>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT code FROM public.permissions WHERE code IN (" + codes + ")")) {
            while (rows.next()) actual.add(rows.getString(1));
        }
        var required = new HashSet<String>();
        if (settings) required.add("system:settings:manage");
        if (rag) required.addAll(Set.of("ai:chat:self", "ai:knowledge:manage"));
        if (moderation) required.addAll(Set.of("content:review","content:publish","question:review","question:publish"));
        if (examDrafts) required.addAll(Set.of("test:manage:self","test:manage:any"));
        if(fullMock)required.addAll(Set.of("test:review","test:publish"));
        if(vocabularyPermission)required.add("vocabulary:learn:self");
        if (!actual.equals(required)) throw new IllegalStateException("Partial feature permission catalog");
    }

    /** Ghép đúng delta metadata từ SQL gốc; không nới so sánh schema hoặc chuẩn hóa tùy ý. */
    private static String vocabularyEvidence(String expected,boolean archive,boolean reviews) {
        if(!archive && !reviews)return expected;
        try(var input=MigrationSchema.class.getResourceAsStream("/db/compatibility/vocabulary-schema.txt")) {
            if(input==null)throw new IllegalStateException("Missing vocabulary schema evidence");
            var lines=new TreeSet<>(Arrays.asList(expected.split("\n")));
            for(String line:new String(input.readAllBytes(),StandardCharsets.UTF_8).replace("\r\n","\n").split("\n")) {
                if(line.isEmpty())continue;
                String[] parts=line.split("\\|",-1);
                if(parts.length<3)throw new IllegalStateException("Invalid vocabulary schema evidence");
                if(parts[1].equals("vocabulary_review_requests")) {if(reviews)lines.add(line);}
                else if(line.startsWith("column|vocabularies|archived_at|")) {if(archive)lines.add(line);}
                else if(line.startsWith("constraint|vocabulary_catalog_enrollments|fk_catalog_enrollment_personal_deck|")) {
                    if(archive){lines.removeIf(x->x.startsWith("constraint|vocabulary_catalog_enrollments|fk_catalog_enrollment_personal_deck|"));lines.add(line);}
                } else throw new IllegalStateException("Unexpected vocabulary schema evidence entry");
            }
            lines.remove("");return String.join("\n",lines)+"\n";
        }catch(java.io.IOException e){throw new IllegalStateException("Cannot read vocabulary schema evidence",e);}
    }

    public static String signature(Connection connection) throws SQLException {
        String sql = """
            WITH relations AS (
                SELECT c.* FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
                WHERE n.nspname='public' AND c.relname<>'flyway_schema_history' AND c.relkind IN ('r','p','v','m','S','f')
            )
            SELECT 'relation|'||c.relname||'|'||c.relkind::text||'|'||c.relpersistence::text||'|'||c.relrowsecurity||'|'||c.relforcerowsecurity FROM relations c
            UNION ALL
            SELECT 'column|'||c.relname||'|'||a.attname||'|'||format_type(a.atttypid,a.atttypmod)||'|'||a.attnotnull||'|'||coalesce(pg_get_expr(d.adbin,d.adrelid),'')||'|'||a.attidentity::text||'|'||a.attgenerated::text
            FROM relations c JOIN pg_attribute a ON a.attrelid=c.oid AND a.attnum>0 AND NOT a.attisdropped
            LEFT JOIN pg_attrdef d ON d.adrelid=c.oid AND d.adnum=a.attnum WHERE c.relkind<>'S'
            UNION ALL
            SELECT 'constraint|'||c.relname||'|'||x.conname||'|'||pg_get_constraintdef(x.oid)||'|'||x.convalidated||'|'||x.condeferrable||'|'||x.condeferred
            FROM relations c JOIN pg_constraint x ON x.conrelid=c.oid
            UNION ALL
            SELECT 'index|'||c.relname||'|'||i.relname||'|'||pg_get_indexdef(x.indexrelid)||'|'||x.indisvalid||'|'||x.indisready
            FROM relations c JOIN pg_index x ON x.indrelid=c.oid JOIN pg_class i ON i.oid=x.indexrelid
            UNION ALL
            SELECT 'sequence|'||c.relname||'|'||format_type(s.seqtypid,NULL)||'|'||s.seqstart||'|'||s.seqincrement||'|'||s.seqmax||'|'||s.seqmin||'|'||s.seqcache||'|'||s.seqcycle
            FROM relations c JOIN pg_sequence s ON s.seqrelid=c.oid
            UNION ALL
            SELECT 'trigger|'||c.relname||'|'||t.tgname||'|'||pg_get_triggerdef(t.oid)||'|'||t.tgenabled::text
            FROM relations c JOIN pg_trigger t ON t.tgrelid=c.oid WHERE NOT t.tgisinternal
            UNION ALL
            SELECT 'function|'||p.proname||'|'||pg_get_function_identity_arguments(p.oid)||'|'||md5(pg_get_functiondef(p.oid))
            FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public' AND p.prokind IN ('f','p')
            UNION ALL
            SELECT 'extension|'||e.extname||'|'||e.extversion||'|'||n.nspname FROM pg_extension e JOIN pg_namespace n ON n.oid=e.extnamespace
            UNION ALL
            SELECT 'policy|'||c.relname||'|'||p.polname||'|'||coalesce(pg_get_expr(p.polqual,p.polrelid),'')||'|'||coalesce(pg_get_expr(p.polwithcheck,p.polrelid),'')
            FROM relations c JOIN pg_policy p ON p.polrelid=c.oid
            UNION ALL
            SELECT 'type|'||t.typname||'|'||t.typtype::text FROM pg_type t JOIN pg_namespace n ON n.oid=t.typnamespace
            WHERE n.nspname='public' AND t.typtype IN ('d','e')
            """;
        var result = new ArrayList<String>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            while (rows.next()) result.add(rows.getString(1));
        }
        Collections.sort(result);
        return String.join("\n", result) + "\n";
    }

    public static String normalizedSignature(Connection connection) throws SQLException {
        boolean full=false;try(var st=connection.createStatement();var rs=st.executeQuery("SELECT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='attempts' AND column_name='test_blueprint_snapshot')")){rs.next();full=rs.getBoolean(1);}
        return normalizedSignature(connection,full);
    }
    private static String normalizedSignature(Connection connection,boolean fullMock) throws SQLException {
        // pg_dump/restore reparses six CHECK expressions with different placement of
        // varchar[] -> text[] casts. These exact alternatives were reproduced by
        // round-tripping the canonical SQL schema, independently of upgrade targets.
        // No general stripping of casts, constraints or validation flags is allowed.
        try (var canonical=MigrationSchema.class.getResourceAsStream("/db/compatibility/"+(fullMock?"schema-v41.txt":"schema-v39.txt"));
             var restored=MigrationSchema.class.getResourceAsStream("/db/compatibility/"+(fullMock?"restored-constraints-v41.txt":"restored-constraints.txt"));
             var restoredIndexes=fullMock?MigrationSchema.class.getResourceAsStream("/db/compatibility/restored-indexes-v41.txt"):null) {
            if (canonical==null || restored==null) throw new IllegalStateException("Missing restore equivalence evidence");
            String canonicalText=new String(canonical.readAllBytes(),StandardCharsets.UTF_8).replace("\r\n","\n");
            Map<String,String> originals=new HashMap<>();
            for(String line:canonicalText.split("\n"))
                if(line.startsWith("constraint|"))originals.put(constraintKey(line),line);
            Map<String,String> alternatives=new HashMap<>();
            for(String line:new String(restored.readAllBytes(),StandardCharsets.UTF_8).replace("\r\n","\n").split("\n")) {
                String original=originals.get(constraintKey(line));
                if(original==null)throw new IllegalStateException("Invalid restore equivalence evidence");
                alternatives.put(line,original);
            }
            if(fullMock){
                if(restoredIndexes==null)throw new IllegalStateException("Missing V41 index restore evidence");
                var indexes=new HashMap<String,String>();
                for(String line:canonicalText.split("\n"))if(line.startsWith("index|"))indexes.put(indexKey(line),line);
                for(String line:new String(restoredIndexes.readAllBytes(),StandardCharsets.UTF_8).replace("\r\n","\n").split("\n")){
                    String original=indexes.get(indexKey(line));if(original==null)throw new IllegalStateException("Invalid index restore evidence");
                    alternatives.put(line,original);
                }
            }
            var normalized=new ArrayList<String>();
            for(String line:signature(connection).split("\n"))normalized.add(alternatives.getOrDefault(line,line));
            Collections.sort(normalized);return String.join("\n",normalized)+"\n";
        } catch(java.io.IOException e) { throw new IllegalStateException("Cannot read restore equivalence evidence",e); }
    }
    private static String constraintKey(String line) {
        String[] parts=line.split("\\|",4);
        if(parts.length<4 || !parts[0].equals("constraint"))throw new IllegalStateException("Invalid constraint evidence");
        return parts[0]+"|"+parts[1]+"|"+parts[2];
    }
    private static String indexKey(String line){
        String[] parts=line.split("\\|",4);
        if(parts.length<4||!parts[0].equals("index"))throw new IllegalStateException("Invalid index evidence");
        return parts[0]+"|"+parts[1]+"|"+parts[2];
    }
}
