package com.toeic.backend.system.infrastructure.migration;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.*;

/** CLI chỉ migrate/validate bằng cùng coordinator; không hỗ trợ repair/clean/baseline hay chọn location. */
public final class MigrationCli {
    private MigrationCli() {}
    public static void main(String[] args) {
        try (var context=new SpringApplicationBuilder(CliConfiguration.class).profiles("migration-cli")
                .web(WebApplicationType.NONE).run(args)) {
            System.out.println("Migration "+context.getEnvironment().getProperty("app.migrations.mode","migrate")+" completed");
        }
    }
    @Configuration(proxyBeanMethods=false)
    @Profile("migration-cli")
    @ImportAutoConfiguration(DataSourceAutoConfiguration.class)
    @Import(MigrationConfiguration.class)
    public static class CliConfiguration { }
}
