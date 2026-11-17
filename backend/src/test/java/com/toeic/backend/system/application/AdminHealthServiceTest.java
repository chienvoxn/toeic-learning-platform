package com.toeic.backend.system.application;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.*;
import java.util.concurrent.CountDownLatch;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminHealthServiceTest {
    private DataSource database() throws Exception {
        var ds=mock(DataSource.class);var connection=mock(Connection.class);var stmt=mock(PreparedStatement.class);var result=mock(ResultSet.class);
        when(ds.getConnection()).thenReturn(connection);when(connection.prepareStatement("SELECT 1")).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(result);when(result.next()).thenReturn(true);when(result.getInt(1)).thenReturn(1);return ds;
    }
    private AdminHealthService service(DataSource ds,String url) {return new AdminHealthService(ds,Clock.fixed(Instant.parse("2026-11-13T00:00:00Z"),ZoneOffset.UTC),url,JsonMapper.builder().build());}
    @Test void healthyDbAndUnconfiguredAiDoNotExposeConfiguration() throws Exception {
        var ds=database();try(ProbeScope close=new ProbeScope(service(ds,""))) {
            var result=close.service.health();assertEquals("UP",result.components().get("database").status());assertEquals("NOT_CONFIGURED",result.components().get("ai").status());
            var connection=ds.getConnection();verify(connection.prepareStatement("SELECT 1")).setQueryTimeout(1);
            assertEquals(ZoneOffset.UTC,result.checkedAt().getOffset());
        }
    }
    private record ProbeScope(AdminHealthService service) implements AutoCloseable {public void close(){service.close();}}
    @Test void dbErrorsAndBorrowTimeoutAreBoundedWithoutLeakingException() throws Exception {
        var ds=mock(DataSource.class);when(ds.getConnection()).thenThrow(new SQLException("password=private-token jdbc:secret"));
        try(ProbeScope close=new ProbeScope(service(ds,""))) {
            var result=close.service.health();assertEquals("DOWN",result.components().get("database").status());
            assertFalse(JsonMapper.builder().build().writeValueAsString(result).contains("private-token"));
        }
        var blocked=mock(DataSource.class);var entered=new CountDownLatch(1);
        when(blocked.getConnection()).thenAnswer(call -> {entered.countDown();new CountDownLatch(1).await();return null;});
        try(ProbeScope close=new ProbeScope(service(blocked,""))) {
            var start=System.nanoTime();var result=close.service.health();assertEquals(0,entered.getCount());
            assertEquals("DOWN",result.components().get("database").status());assertTrue(System.nanoTime()-start<Duration.ofSeconds(4).toNanos());
        }
    }
    @Test void aiUsesRealHttpStatusPayloadAndDoesNotFollowRedirects() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/health",exchange -> {byte[] bytes="{\"status\":\"ok\",\"service\":\"toeic-ai\"}".getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});
        server.createContext("/down",exchange -> {exchange.sendResponseHeaders(503,-1);exchange.close();});
        server.createContext("/redirect",exchange -> {exchange.getResponseHeaders().set("Location","/health");exchange.sendResponseHeaders(302,-1);exchange.close();});
        server.start();
        try {
            String base="http://127.0.0.1:"+server.getAddress().getPort();
            for(String path:new String[]{"/health","/down","/redirect"})try(ProbeScope close=new ProbeScope(service(database(),base+path))) {
                assertEquals(path.equals("/health")?"UP":"DOWN",close.service.health().components().get("ai").status());
            }
            try(ProbeScope close=new ProbeScope(service(database(),"http://user:secret@127.0.0.1/health"))) {assertEquals("UNKNOWN",close.service.health().components().get("ai").status());}
        } finally {server.stop(0);}
    }
}
