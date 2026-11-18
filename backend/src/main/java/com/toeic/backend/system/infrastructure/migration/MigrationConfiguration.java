package com.toeic.backend.system.infrastructure.migration;

import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationInitializer;
import org.springframework.context.annotation.*;
import org.springframework.core.env.*;

/** Điều phối chung cho Boot, test và CLI: kiểm config/history/schema dưới khóa trước migrate. */
@Configuration(proxyBeanMethods=false)
public class MigrationConfiguration {
    public static final long LOCK_KEY=34173637L;

    @Bean static BeanFactoryPostProcessor migrationSafety(Environment environment) {
        checkPolicy(environment);
        return factory -> { };
    }

    public static void checkPolicy(Environment environment) {
        if (!environment.getProperty("spring.flyway.enabled",Boolean.class,true)
                || !environment.getProperty("spring.flyway.locations","classpath:db/migration").equals("classpath:db/migration")
                || !environment.getProperty("spring.jpa.hibernate.ddl-auto","validate").equals("validate")
                || !environment.getProperty("spring.sql.init.mode","never").equals("never"))
            throw new IllegalStateException("Migration safety configuration cannot be overridden");
        Set<String> allowed=Set.of("springflywayenabled","springflywaylocations");
        if (environment instanceof ConfigurableEnvironment configurable) {
            for (var source:configurable.getPropertySources()) if (source instanceof EnumerablePropertySource<?> enumerable) {
                for (String name:enumerable.getPropertyNames()) {
                    String normalized=name.toLowerCase(Locale.ROOT).replaceAll("[._-]", "");
                    if ((normalized.startsWith("springflyway") && !allowed.contains(normalized))
                            || normalized.contains("connectioninitsql") || normalized.equals("springjpapropertieshibernatehbm2ddlauto")
                            || normalized.contains("schemagenerationdatabaseaction"))
                        throw new IllegalStateException("Unsupported migration configuration property: "+name);
                }
            }
        }
        String mode=environment.getProperty("app.migrations.mode","migrate");
        if (!Set.of("migrate","validate").contains(mode)) throw new IllegalStateException("Only migrate and validate are supported");
        long timeout=environment.getProperty("app.migrations.lock-timeout-ms",Long.class,30000L);
        if (timeout<100 || timeout>60000) throw new IllegalStateException("Migration lock timeout must be 100..60000 ms");
    }

    @Bean Flyway flyway(DataSource dataSource,Environment environment) throws SQLException {
        checkPolicy(environment);
        try (var connection=dataSource.getConnection()) {
            long timeout=environment.getProperty("app.migrations.lock-timeout-ms",Long.class,30000L);
            acquire(connection,timeout);
            try {
                var selection=MigrationHistory.inspect(connection);
                var flyway=Flyway.configure().dataSource(dataSource)
                        .locations("classpath:db/migration","classpath:db/history/"+selection.lineage())
                        .schemas("public").defaultSchema("public").table("flyway_schema_history")
                        .baselineOnMigrate(false).outOfOrder(false).cleanDisabled(true).createSchemas(false)
                        .validateOnMigrate(true).validateMigrationNaming(true).ignoreMigrationPatterns(new String[0]).load();
                MigrationHistory.verify(connection,selection,flyway);
                if (environment.getProperty("app.migrations.mode","migrate").equals("migrate")) flyway.migrate();
                flyway.validate();
                MigrationHistory.verify(connection,MigrationHistory.inspect(connection),flyway);
                return flyway;
            } finally {
                try (var statement=connection.createStatement()) { statement.execute("SELECT pg_advisory_unlock("+LOCK_KEY+")"); }
            }
        }
    }

    // Migration already completed in the bean factory under the coordinator lock.
    // Register the standard initializer so Boot orders JPA after this final validation.
    @Bean FlywayMigrationInitializer flywayInitializer(Flyway flyway) {
        return new FlywayMigrationInitializer(flyway,Flyway::validate);
    }

    private static void acquire(Connection connection,long timeout) throws SQLException {
        long deadline=System.nanoTime()+timeout*1_000_000;
        try (var statement=connection.createStatement()) {
            do {
                try (var rows=statement.executeQuery("SELECT pg_try_advisory_lock("+LOCK_KEY+")")) { rows.next();if (rows.getBoolean(1)) return; }
                try { Thread.sleep(50); }
                catch (InterruptedException e) { Thread.currentThread().interrupt();throw new IllegalStateException("Migration lock interrupted",e); }
            } while (System.nanoTime()<deadline);
        }
        throw new IllegalStateException("Migration lock timeout; no migration executed");
    }
}
