package com.toeic.backend.ai;

import com.sun.net.httpserver.HttpServer;
import com.toeic.backend.ai.infrastructure.RagClient;
import com.toeic.backend.ai.application.ChatFailure;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class RagClientTest {
    @Test void usesSeparateInternalKeysAndReadsPythonCamelCaseContract() throws Exception {
        var json=JsonMapper.builder().build();UUID version=UUID.randomUUID(),request=UUID.randomUUID(),chunk=UUID.randomUUID();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/internal/v1/documents",exchange->{
            assertEquals("HTTP/1.1",exchange.getProtocol());
            assertEquals("PUT",exchange.getRequestMethod());assertEquals("test-admin",exchange.getRequestHeaders().getFirst("X-API-Key"));
            var body=json.readTree(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            assertEquals(version.toString(),body.get("documentId").asString());
            exchange.sendResponseHeaders(200,2);exchange.getResponseBody().write("{}".getBytes());exchange.close();
        });
        server.createContext("/internal/v1/tutor/ask",exchange->{
            assertEquals("HTTP/1.1",exchange.getProtocol());
            assertEquals("test-service",exchange.getRequestHeaders().getFirst("X-API-Key"));
            assertEquals(request.toString(),exchange.getRequestHeaders().getFirst("X-Request-ID"));
            var body=json.readTree(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            assertEquals(version.toString(),body.get("allowedDocumentIds").get(0).asString());
            byte[] response=json.writeValueAsString(Map.of("answer","Answer","grounded",true,"citations",List.of(Map.of(
                    "documentId",version,"chunkId",chunk,"title","Grammar","excerpt","Evidence","similarity",0.8)),
                    "exercises",List.of(),"traceId",request,"usage",Map.of("provider","google","chatModel","gemini-test","embeddingModel","embed-test","inputTokens",1,"outputTokens",2,"totalTokens",3,"latencyMs",10))).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,response.length);exchange.getResponseBody().write(response);exchange.close();
        });server.start();
        try {
            var client=new RagClient(json,"http://127.0.0.1:"+server.getAddress().getPort(),"test-service","test-admin");
            client.index(version,"Grammar","Reference");var answer=client.ask("Question",List.of(version),List.of(),request);
            assertTrue(answer.grounded());assertEquals(chunk,answer.citations().getFirst().chunkId());assertEquals(3,answer.usage().totalTokens());
        } finally { server.stop(0); }
    }
    @Test void masksUpstreamBodyAndRejectsMissingConfiguration() throws Exception {
        var json=JsonMapper.builder().build();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{byte[] body="PRIVATE_PROVIDER_DETAILS".getBytes();exchange.sendResponseHeaders(500,body.length);exchange.getResponseBody().write(body);exchange.close();});server.start();
        try {
            var client=new RagClient(json,"http://127.0.0.1:"+server.getAddress().getPort(),"test-service","");
            var error=assertThrows(ChatFailure.class,()->client.ask("Question",List.of(),List.of(),UUID.randomUUID()));
            assertEquals(503,error.status);assertFalse(error.getMessage().contains("PRIVATE"));
            assertEquals("AI_NOT_CONFIGURED",assertThrows(ChatFailure.class,()->client.index(UUID.randomUUID(),"Title","Text")).code);
        } finally { server.stop(0); }
    }
}
