package com.toeic.backend.system.infrastructure.migration;

import java.sql.*;
import java.util.*;
import org.flywaydb.core.Flyway;

/** Nhận diện prefix/history thực tế, chọn một V34 và kiểm checksum trước mọi ghi migration. */
public final class MigrationHistory {
    private MigrationHistory() {}
    public static final List<String> VERSIONS = List.of("1","2","3","4","5","6","7","8","9","10","11","12","13","14","15","16","17","18","19","20","21","22","23","24","25","26","27","27.1","28","29","30","31","32","33","34","35","36","37");
    public record Row(int rank, String version, String script, String description, String type, Integer checksum, boolean success) {}
    public record Selection(String lineage, List<Row> rows) {}

    public static Selection inspect(Connection connection) throws SQLException {
        if (!"public".equals(connection.getSchema())) throw new IllegalStateException("Only public migration schema is supported");
        if (exists(connection, "SELECT EXISTS(SELECT FROM pg_namespace WHERE nspname !~ '^pg_' AND nspname NOT IN ('public','information_schema'))"))
            throw new IllegalStateException("Unknown application schema");
        boolean history = exists(connection, "SELECT to_regclass('public.flyway_schema_history') IS NOT NULL");
        var rows = new ArrayList<Row>();
        if (history) {
            MigrationSchema.verifyHistoryTable(connection);
            try (var statement = connection.createStatement(); var result = statement.executeQuery("SELECT installed_rank,version,script,description,type,checksum,success FROM public.flyway_schema_history ORDER BY installed_rank")) {
                while (result.next()) rows.add(new Row(result.getInt(1),result.getString(2),result.getString(3),result.getString(4),result.getString(5),(Integer)result.getObject(6),result.getBoolean(7)));
            }
        }
        if (rows.isEmpty()) {
            if (exists(connection,"SELECT EXISTS(SELECT FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relname<>'flyway_schema_history' AND c.relkind IN ('r','p','v','m','S','f'))"))
                throw new IllegalStateException("Nonempty database without migration history");
            MigrationSchema.verifyEmpty(connection);
            return new Selection("rag", List.of());
        }
        verifyPrefix(rows);
        String lineage="rag";
        var v34=rows.stream().filter(r->r.version().equals("34")).findFirst();
        if (v34.isPresent()) {
            var row=v34.get();
            if (row.script().equals("V34__managed_system_defaults.sql") && row.checksum()==1299604777 && row.description().equals("managed system defaults")) lineage="settings";
            else if (!(row.script().equals("V34__rag_chat_runtime.sql") && row.checksum()==2001005912 && row.description().equals("rag chat runtime")))
                throw new IllegalStateException("Unknown V34 identity");
        } else if (!rows.getLast().version().equals("33")) {
            throw new IllegalStateException("Only empty, complete V33 or recognized V34+ histories are supported");
        }
        return new Selection(lineage, List.copyOf(rows));
    }

    static void verifyPrefix(List<Row> rows) {
        if (rows.size()>VERSIONS.size()) throw new IllegalStateException("Unknown or duplicate history version");
        for (int i=0;i<rows.size();i++) {
            var row=rows.get(i);
            if (row.rank()!=i+1 || !VERSIONS.get(i).equals(row.version()) || !row.success() || !"SQL".equals(row.type()) || row.checksum()==null)
                throw new IllegalStateException("Failed, gapped, duplicate or unknown migration history");
        }
    }

    public static void verify(Connection connection, Selection selection, Flyway flyway) throws SQLException {
        var resolved=new LinkedHashMap<String,org.flywaydb.core.api.MigrationInfo>();
        for (var migration:flyway.info().all()) {
            if (migration.getVersion()==null || resolved.put(migration.getVersion().toString(),migration)!=null)
                throw new IllegalStateException("Unknown or duplicate packaged migration");
        }
        if (!new ArrayList<>(resolved.keySet()).equals(VERSIONS)) throw new IllegalStateException("Packaged migration set differs from release");
        for (var row:selection.rows()) {
            var migration=resolved.get(row.version());
            String location=migration.getPhysicalLocation();
            String script=location==null?"":location.substring(Math.max(location.lastIndexOf('/'),location.lastIndexOf('\\'))+1);
            String description=script.contains("__") && script.endsWith(".sql")?script.substring(script.indexOf("__")+2,script.length()-4).replace('_',' '):"";
            if (!Objects.equals(row.checksum(),migration.getResolvedChecksum()) || !row.description().equals(description)
                    || !script.endsWith(".sql") || !row.script().equals(script))
                throw new IllegalStateException("Applied migration does not match packaged SQL at version "+row.version());
        }
        if (!selection.rows().isEmpty()) {
            Set<String> versions=new HashSet<>();selection.rows().forEach(r->versions.add(r.version()));
            boolean settings=(selection.lineage().equals("settings") && versions.contains("34")) || versions.contains("36");
            boolean rag=(selection.lineage().equals("rag") && versions.contains("34")) || versions.contains("37");
            MigrationSchema.verify(connection,settings,rag,versions.contains("35"));
        }
    }
    private static boolean exists(Connection connection,String query) throws SQLException {
        try (var statement=connection.createStatement(); var rows=statement.executeQuery(query)) { rows.next();return rows.getBoolean(1); }
    }
}
