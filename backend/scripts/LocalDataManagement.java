import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Diễn tập PostgreSQL local: backup snapshot nhất quán, restore DB mới, so hash dữ liệu. */
class LocalDataManagement {
    static final String HOST="127.0.0.1",PORT="55459";
    static Path root,bin,archive,manifest;
    static String user,source,target;
    static Connection connect(String db) throws Exception {
        var properties=new Properties();properties.setProperty("user",user);
        properties.setProperty("password",System.getenv().getOrDefault("PGPASSWORD",""));
        properties.setProperty("connectTimeout","3");properties.setProperty("socketTimeout","30");
        properties.setProperty("ApplicationName","toeic-admin06-drill");
        return DriverManager.getConnection("jdbc:postgresql://"+HOST+":"+PORT+"/"+db,properties);
    }
    static String quote(String identifier) {return "\""+identifier.replace("\"","\"\"")+"\"";}
    static void server() throws Exception {
        try(var c=connect("postgres");var s=c.createStatement();var r=s.executeQuery("SELECT inet_server_port(),current_setting('data_directory')")) {
            if(!r.next() || r.getInt(1)!=55459 || !Path.of(r.getString(2)).toRealPath().equals(root.resolve("backend/target/q03-main-postgres").toRealPath()))throw new IllegalStateException("Unexpected local PostgreSQL server; operation refused");
        }
    }
    static void readonly(Connection c) throws Exception {
        c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);c.setReadOnly(true);c.setAutoCommit(false);
        try(var s=c.createStatement()){s.execute("SET TIME ZONE 'UTC'");}
    }
    static List<String> names(Connection c,String kind) throws Exception {
        var names=new ArrayList<String>();
        try(var s=c.prepareStatement("SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relkind::text=? ORDER BY c.relname")) {
            s.setString(1,kind);try(var r=s.executeQuery()){while(r.next())names.add(r.getString(1));}
        }
        return names;
    }
    static String hex(byte[] bytes){return HexFormat.of().formatHex(bytes);}
    static String digest(Path file) throws Exception {
        var digest=MessageDigest.getInstance("SHA-256");try(var input=Files.newInputStream(file)){byte[] buffer=new byte[65536];int count;while((count=input.read(buffer))!=-1)digest.update(buffer,0,count);}return hex(digest.digest());
    }
    static Properties inventory(Connection c) throws Exception {
        var result=new Properties();
        try(var s=c.createStatement();var rows=s.executeQuery("SELECT nspname FROM pg_namespace WHERE nspname !~ '^pg_' AND nspname<>'information_schema'")) {
            while(rows.next())if(!rows.getString(1).equals("public"))throw new IllegalStateException("Drill supports public-only application databases");
        }
        try(var s=c.createStatement();var rows=s.executeQuery("SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relkind IN ('p','f','m')")) {
            rows.next();if(rows.getLong(1)>0)throw new IllegalStateException("Partitioned/foreign/materialized tables require a separate inventory policy");
        }
        var tables=names(c,"r");result.setProperty("tableCount",Integer.toString(tables.size()));long total=0;
        for(String table:tables) {
            var hash=MessageDigest.getInstance("SHA-256");long count=0;
            try(var s=c.createStatement()){s.setFetchSize(200);s.setQueryTimeout(30);
                try(var rows=s.executeQuery("SELECT to_jsonb(t)::text AS row FROM public."+quote(table)+" t ORDER BY to_jsonb(t)::text COLLATE \"C\"")) {
                    while(rows.next()){hash.update(rows.getString(1).getBytes(StandardCharsets.UTF_8));hash.update((byte)'\n');count++;}
                }
            }
            result.setProperty("table."+table+".rows",Long.toString(count));result.setProperty("table."+table+".sha256",hex(hash.digest()));total+=count;
        }
        result.setProperty("totalRows",Long.toString(total));
        for(String sequence:names(c,"S"))try(var s=c.createStatement();var rows=s.executeQuery("SELECT last_value,is_called FROM public."+quote(sequence))) {
            rows.next();result.setProperty("sequence."+sequence+".value",rows.getString(1));result.setProperty("sequence."+sequence+".called",rows.getString(2));
        }
        return result;
    }
    static void process(String executable,List<String> arguments) throws Exception {
        var command=new ArrayList<String>();command.add(bin.resolve(executable+".exe").toString());command.addAll(arguments);
        var log=archive.resolveSibling(archive.getFileName()+"."+executable+".log");
        var process=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        long deadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(2);
        while(!process.waitFor(10,TimeUnit.SECONDS)) {
            System.out.println(executable+" still running; diagnostics retained in local backup folder");
            if(System.nanoTime()>deadline){process.destroy();if(!process.waitFor(3,TimeUnit.SECONDS))process.destroyForcibly();throw new IllegalStateException(executable+" deadline exceeded; no success reported");}
        }
        if(process.exitValue()!=0)throw new IllegalStateException(executable+" failed; inspect its local log. Source database was not restored or reset");
    }
    static List<String> arguments(String database){return new ArrayList<>(List.of("-h",HOST,"-p",PORT,"-U",user,"-w","-d",database));}
    static void backup() throws Exception {
        if(Files.exists(archive) || Files.exists(manifest))throw new IllegalStateException("Backup destination exists; overwrite refused");
        var evidence=new Properties();
        try(var c=connect(source)) {
            readonly(c);String snapshot;
            try(var s=c.createStatement();var rows=s.executeQuery("SELECT pg_export_snapshot()")){rows.next();snapshot=rows.getString(1);}
            evidence.putAll(inventory(c));
            var args=arguments(source);args.addAll(List.of("-Fc","--no-owner","--no-privileges","--snapshot="+snapshot,"-f",archive.toString()));
            process("pg_dump",args);c.commit();
        }
        evidence.setProperty("format","toeic-admin06-v1");evidence.setProperty("sourceDatabase",source);evidence.setProperty("createdAt",Instant.now().toString());evidence.setProperty("archiveSha256",digest(archive));
        try(var output=Files.newOutputStream(manifest,StandardOpenOption.CREATE_NEW)){evidence.store(output,"Local demo/test inventory: hashes/counts only; archive contains application data");}
        System.out.println("BACKUP VERIFIED: archive SHA-256 and consistent snapshot inventory saved; tables="+evidence.getProperty("tableCount")+", rows="+evidence.getProperty("totalRows"));
    }
    static Properties evidence() throws Exception {
        if(!Files.isRegularFile(archive) || Files.isSymbolicLink(archive) || !Files.isRegularFile(manifest) || Files.isSymbolicLink(manifest))throw new IllegalStateException("Archive/manifest missing or symbolic link refused");
        var evidence=new Properties();try(var input=Files.newInputStream(manifest)){evidence.load(input);}
        if(!"toeic-admin06-v1".equals(evidence.getProperty("format")) || !source.equals(evidence.getProperty("sourceDatabase")) || !digest(archive).equals(evidence.getProperty("archiveSha256")))throw new IllegalStateException("Backup identity/checksum mismatch; restore refused");
        return evidence;
    }
    static void verify(Properties expected) throws Exception {
        Properties actual;try(var c=connect(target)){readonly(c);actual=inventory(c);c.commit();}
        var dataKeys=new TreeSet<String>();for(String key:actual.stringPropertyNames())if(!key.startsWith("sequence."))dataKeys.add(key);
        var expectedKeys=new TreeSet<String>();for(String key:expected.stringPropertyNames())if(key.startsWith("table.") || key.equals("tableCount") || key.equals("totalRows"))expectedKeys.add(key);
        if(!dataKeys.equals(expectedKeys))throw new IllegalStateException("Restored table inventory differs");
        for(String key:dataKeys)if(!actual.getProperty(key).equals(expected.getProperty(key)))throw new IllegalStateException("Restored row count/hash differs; inspect local evidence");
        var sequenceKeys=new TreeSet<String>();for(String key:expected.stringPropertyNames())if(key.startsWith("sequence."))sequenceKeys.add(key);
        var actualSequences=new TreeSet<String>();for(String key:actual.stringPropertyNames())if(key.startsWith("sequence."))actualSequences.add(key);
        if(!sequenceKeys.equals(actualSequences))throw new IllegalStateException("Restored sequence inventory differs");
        for(String key:sequenceKeys)if(key.endsWith(".value")) {
            long value=Long.parseLong(actual.getProperty(key)),floor=Long.parseLong(expected.getProperty(key));
            String called=key.substring(0,key.length()-6)+".called";
            if(value<floor || value==floor && Set.of("t","true").contains(expected.getProperty(called)) && !Set.of("t","true").contains(actual.getProperty(called)))throw new IllegalStateException("Restored sequence moved backwards");
        }
        System.out.println("RESTORE DATA VERIFIED: every public table row hash/count matches exported source snapshot; sequences did not move backwards; tables="+actual.getProperty("tableCount")+", rows="+actual.getProperty("totalRows"));
    }
    static void restore() throws Exception {
        var expected=evidence();
        try(var c=connect("postgres");var check=c.prepareStatement("SELECT 1 FROM pg_database WHERE datname=?")) {
            check.setString(1,target);try(var rows=check.executeQuery()){if(rows.next())throw new IllegalStateException("Target database already exists; overwrite/reset refused");}
            try(var s=c.createStatement()){s.execute("CREATE DATABASE "+quote(target));}
        }
        var args=arguments(target);args.addAll(List.of("--no-owner","--no-privileges","--exit-on-error","--single-transaction",archive.toString()));
        process("pg_restore",args);verify(expected);
    }
    public static void main(String[] args) {
        try {
            if(args.length!=7)throw new IllegalArgumentException("Use the local PowerShell wrapper");
            String mode=args[0];root=Path.of(args[1]).toRealPath();bin=Path.of(args[2]).toRealPath();user=args[3];source=args[4];target=args[5];archive=Path.of(args[6]).toAbsolutePath().normalize();manifest=archive.resolveSibling(archive.getFileName()+".manifest.properties");
            var directory=root.resolve("backups.local");
            if(Files.isSymbolicLink(directory) || !archive.getParent().toRealPath().equals(directory.toRealPath()) || !archive.getFileName().toString().matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,120}\\.backup"))throw new IllegalArgumentException("Backup must be a regular file directly inside backups.local");
            if(!user.matches("[A-Za-z_][A-Za-z0-9_]{0,62}") || !source.matches("toeic_learning_demo|toeic_admin06_(test|regression)"))throw new IllegalArgumentException("Only named ADMIN06 local demo/test sources are allowed");
            if(!mode.equals("Backup") && (!target.matches("toeic_admin06_restore_[a-z0-9_]{1,40}") || target.equals(source)))throw new IllegalArgumentException("Restore target must be a separate named ADMIN06 restore database");
            server();switch(mode){case "Backup" -> backup();case "Restore" -> restore();case "Verify" -> verify(evidence());default -> throw new IllegalArgumentException("Unsupported operation");}
        } catch(Exception exception){System.err.println("Local data operation failed: "+(exception instanceof SQLException sql?"database unavailable/invalid operation, SQLSTATE="+sql.getSQLState()+"; no credentials printed":exception.getMessage()));System.exit(1);}
    }
}
