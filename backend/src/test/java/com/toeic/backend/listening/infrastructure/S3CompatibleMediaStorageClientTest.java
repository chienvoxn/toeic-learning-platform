package com.toeic.backend.listening.infrastructure;

import com.sun.net.httpserver.HttpServer;
import com.toeic.backend.listening.domain.MediaObjectSpec;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class S3CompatibleMediaStorageClientTest {
    private static final Instant FIXED_NOW = Instant.parse("2026-11-21T02:00:00Z");

    @Test
    void presignGeneratesDeterministicSigV4UrlAndSanitizesInternalStoragePaths() {
        S3StorageProperties props = new S3StorageProperties();
        props.setEndpoint("http://127.0.0.1:9000");
        props.setPublicEndpoint("http://localhost:9000");
        props.setRegion("us-east-1");
        props.setBucket("toeic-media");
        props.setAccessKey("minioadmin");
        props.setSecretKey("minioadmin123");
        props.setPresignTtlSeconds(1800);

        S3CompatibleMediaStorageClient client = new S3CompatibleMediaStorageClient(props);
        UUID resourceId = UUID.fromString("b3000000-0000-0000-0000-000000000001");

        // Internal fixture path must never leak into the object key or pre-signed URL
        var internalPresigned = client.presignGetObject(resourceId, "internal://Q03_LEAK_STORAGE_1", FIXED_NOW);
        assertFalse(internalPresigned.url().contains("Q03_LEAK_STORAGE"));
        assertEquals("media/" + resourceId, internalPresigned.objectKey());
        assertTrue(internalPresigned.url().startsWith("http://localhost:9000/toeic-media/media/" + resourceId + "?"));
        assertTrue(internalPresigned.url().contains("X-Amz-Algorithm=AWS4-HMAC-SHA256"));
        assertTrue(internalPresigned.url().contains("X-Amz-Expires=1800"));
        assertTrue(internalPresigned.url().contains("X-Amz-SignedHeaders=host"));
        assertTrue(internalPresigned.url().contains("X-Amz-Signature="));
        assertEquals(FIXED_NOW.plusSeconds(1800), internalPresigned.expiresAt().toInstant());

        // Normal MinIO object key
        var audioPresigned = client.presignGetObject(resourceId, "audio/demo-part1-q1.wav", FIXED_NOW);
        assertEquals("audio/demo-part1-q1.wav", audioPresigned.objectKey());
        assertTrue(audioPresigned.url().startsWith("http://localhost:9000/toeic-media/audio/demo-part1-q1.wav?"));
        // Deterministic signature check for identical timestamp
        var audioPresignedAgain = client.presignGetObject(resourceId, "s3://toeic-media/audio/demo-part1-q1.wav", FIXED_NOW);
        assertEquals(audioPresigned.url(), audioPresignedAgain.url());
    }

    @Test
    void putObjectAndLoadObjectCommunicateWithS3CompatibleServerUsingSigV4() throws Exception {
        AtomicReference<String> lastPutAuth = new AtomicReference<>();
        AtomicReference<String> lastPutPath = new AtomicReference<>();
        AtomicReference<byte[]> storedBytes = new AtomicReference<>(new byte[0]);

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            if ("PUT".equals(method)) {
                byte[] body = exchange.getRequestBody().readAllBytes();
                if (path.endsWith(".wav")) {
                    lastPutAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
                    lastPutPath.set(path);
                    storedBytes.set(body);
                }
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
                return;
            }
            if ("GET".equals(method)) {
                byte[] body = storedBytes.get();
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
                return;
            }
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            S3StorageProperties props = new S3StorageProperties();
            props.setEndpoint("http://127.0.0.1:" + port);
            props.setPublicEndpoint("http://127.0.0.1:" + port);
            props.setBucket("toeic-media");
            props.setAccessKey("testkey");
            props.setSecretKey("testsecret");

            S3CompatibleMediaStorageClient client = new S3CompatibleMediaStorageClient(props);
            byte[] wavPayload = MediaObjectSpec.generateDemoWav(2, 440);
            client.putObject("audio/part1-test.wav", "audio/wav", wavPayload, FIXED_NOW);

            assertEquals("/toeic-media/audio/part1-test.wav", lastPutPath.get());
            assertNotNull(lastPutAuth.get());
            assertTrue(lastPutAuth.get().startsWith("AWS4-HMAC-SHA256 Credential=testkey/20261121/us-east-1/s3/aws4_request"));

            UUID resourceId = UUID.randomUUID();
            byte[] downloaded = client.loadObjectOrDemoFallback(resourceId, "audio/part1-test.wav", "AUDIO", 2, FIXED_NOW);
            assertArrayEquals(wavPayload, downloaded);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void loadObjectFallsBackToValidDemoWavAndSvgWhenMinioIsOffline() {
        S3StorageProperties props = new S3StorageProperties();
        props.setEndpoint("http://127.0.0.1:1"); // unreachable port
        S3CompatibleMediaStorageClient client = new S3CompatibleMediaStorageClient(props);

        UUID id = UUID.randomUUID();
        byte[] wav = client.loadObjectOrDemoFallback(id, "audio/demo-part1-q1.wav", "AUDIO", 3, FIXED_NOW);
        assertTrue(wav.length > 44);
        assertEquals("RIFF", new String(wav, 0, 4, StandardCharsets.US_ASCII));

        byte[] svg = client.loadObjectOrDemoFallback(id, "image/demo-part1-q1.svg", "IMAGE", null, FIXED_NOW);
        String svgText = new String(svg, StandardCharsets.UTF_8);
        assertTrue(svgText.contains("<svg"));
        assertTrue(svgText.contains("TOEIC Listening Visual"));
    }
}
