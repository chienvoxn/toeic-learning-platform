package com.toeic.backend.listening.infrastructure;

import com.toeic.backend.listening.domain.MediaObjectSpec;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Client tương thích chuẩn S3 / MinIO sử dụng AWS Signature Version 4 (AWS4-HMAC-SHA256)
 * để upload file audio/image lên MinIO và sinh Pre-signed GET URL có thời hạn.
 */
@Component
@EnableConfigurationProperties(S3StorageProperties.class)
public class S3CompatibleMediaStorageClient {
    private static final DateTimeFormatter AMZ_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    public record PresignedObject(String objectKey, String url, OffsetDateTime expiresAt) {
    }

    private final S3StorageProperties properties;
    private volatile HttpClient httpClient;

    public S3CompatibleMediaStorageClient(S3StorageProperties properties) {
        this.properties = properties;
    }

    private HttpClient client() {
        HttpClient current = this.httpClient;
        if (current == null) {
            synchronized (this) {
                current = this.httpClient;
                if (current == null) {
                    current = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(3))
                            .build();
                    this.httpClient = current;
                }
            }
        }
        return current;
    }

    /** Chuẩn hóa object key từ cột storage_url, tuyệt đối không rò rỉ đường dẫn nội bộ internal://. */
    public String resolveObjectKey(UUID resourceId, String rawStorageUrl) {
        if (rawStorageUrl == null || rawStorageUrl.isBlank() || rawStorageUrl.startsWith("internal://")) {
            return "media/" + resourceId;
        }
        String clean = rawStorageUrl.trim();
        for (String prefix : new String[]{"s3://", "minio://"}) {
            if (clean.startsWith(prefix)) {
                clean = clean.substring(prefix.length());
            }
        }
        while (clean.startsWith("/")) {
            clean = clean.substring(1);
        }
        String bucketPrefix = properties.getBucket() + "/";
        if (clean.startsWith(bucketPrefix)) {
            clean = clean.substring(bucketPrefix.length());
        }
        if (clean.isBlank() || clean.contains("..")) {
            return "media/" + resourceId;
        }
        return clean;
    }

    /** Sinh Pre-signed GET URL theo chuẩn AWS S3 SigV4 (AWS4-HMAC-SHA256) có thời hạn. */
    public PresignedObject presignGetObject(UUID resourceId, String rawStorageUrl, Instant now) {
        String objectKey = resolveObjectKey(resourceId, rawStorageUrl);
        int ttl = properties.getPresignTtlSeconds();
        URI baseUri = URI.create(properties.getPublicEndpoint());
        String hostHeader = hostHeader(baseUri);
        String amzDate = AMZ_DATE.format(now);
        String dateStamp = DATE_STAMP.format(now);
        String region = properties.getRegion();
        String credentialScope = dateStamp + "/" + region + "/s3/aws4_request";
        String credential = properties.getAccessKey() + "/" + credentialScope;

        String canonicalUri = "/" + urlEncodePath(properties.getBucket() + "/" + objectKey);
        String canonicalQuery = "X-Amz-Algorithm=AWS4-HMAC-SHA256"
                + "&X-Amz-Credential=" + urlEncode(credential)
                + "&X-Amz-Date=" + urlEncode(amzDate)
                + "&X-Amz-Expires=" + ttl
                + "&X-Amz-SignedHeaders=host";

        String canonicalHeaders = "host:" + hostHeader + "\n";
        String canonicalRequest = "GET\n"
                + canonicalUri + "\n"
                + canonicalQuery + "\n"
                + canonicalHeaders + "\n"
                + "host\n"
                + "UNSIGNED-PAYLOAD";

        String stringToSign = "AWS4-HMAC-SHA256\n"
                + amzDate + "\n"
                + credentialScope + "\n"
                + sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));

        byte[] signingKey = getSignatureKey(properties.getSecretKey(), dateStamp, region, "s3");
        String signature = HexFormat.of().formatHex(hmacSha256(signingKey, stringToSign));

        String fullUrl = properties.getPublicEndpoint() + canonicalUri + "?" + canonicalQuery + "&X-Amz-Signature=" + signature;
        OffsetDateTime expiresAt = OffsetDateTime.ofInstant(now.plusSeconds(ttl), ZoneOffset.UTC);
        return new PresignedObject(objectKey, fullUrl, expiresAt);
    }

    /** Upload dữ liệu nhị phân lên MinIO/S3 qua HTTP PUT có ký AWS4-HMAC-SHA256. */
    public void putObject(String objectKey, String mimeType, byte[] content, Instant now) {
        ensureBucketQuietly(now);
        URI baseUri = URI.create(properties.getEndpoint());
        String hostHeader = hostHeader(baseUri);
        String amzDate = AMZ_DATE.format(now);
        String dateStamp = DATE_STAMP.format(now);
        String region = properties.getRegion();
        String credentialScope = dateStamp + "/" + region + "/s3/aws4_request";
        String payloadHash = sha256Hex(content);
        String canonicalUri = "/" + urlEncodePath(properties.getBucket() + "/" + objectKey);

        String canonicalHeaders = "host:" + hostHeader + "\n"
                + "x-amz-content-sha256:" + payloadHash + "\n"
                + "x-amz-date:" + amzDate + "\n";
        String signedHeaders = "host;x-amz-content-sha256;x-amz-date";
        String canonicalRequest = "PUT\n"
                + canonicalUri + "\n\n"
                + canonicalHeaders + "\n"
                + signedHeaders + "\n"
                + payloadHash;

        String stringToSign = "AWS4-HMAC-SHA256\n"
                + amzDate + "\n"
                + credentialScope + "\n"
                + sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));

        byte[] signingKey = getSignatureKey(properties.getSecretKey(), dateStamp, region, "s3");
        String signature = HexFormat.of().formatHex(hmacSha256(signingKey, stringToSign));
        String authorization = "AWS4-HMAC-SHA256 Credential=" + properties.getAccessKey() + "/" + credentialScope
                + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.getEndpoint() + canonicalUri))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", mimeType)
                .header("x-amz-content-sha256", payloadHash)
                .header("x-amz-date", amzDate)
                .header("Authorization", authorization)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(content))
                .build();
        try {
            HttpResponse<byte[]> response = client().send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("MinIO/S3 PUT returned HTTP " + response.statusCode());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("MinIO/S3 upload interrupted", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("MinIO/S3 storage endpoint unavailable", ex);
        }
    }

    /** Tải nội dung media từ MinIO/S3, hoặc sinh WAV/SVG mẫu cho dữ liệu seed/demo khi chạy local chưa bật MinIO. */
    public byte[] loadObjectOrDemoFallback(UUID resourceId, String rawStorageUrl, String resourceType, Integer durationSeconds, Instant now) {
        String objectKey = resolveObjectKey(resourceId, rawStorageUrl);
        try {
            PresignedObject presigned = presignGetObjectOnEndpoint(properties.getEndpoint(), objectKey, now);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(presigned.url()))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = client().send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 200 && response.statusCode() < 300 && response.body() != null && response.body().length > 0) {
                return response.body();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ignored) {
            // Fallback to deterministic demo WAV/SVG so local demo works even if MinIO Docker container is stopped.
        }
        if ("IMAGE".equalsIgnoreCase(resourceType)) {
            return MediaObjectSpec.generateDemoSvg("TOEIC Listening Visual - " + objectKey);
        }
        int duration = durationSeconds != null && durationSeconds > 0 ? durationSeconds : 4;
        int freq = 320 + Math.abs(resourceId.hashCode() % 220);
        return MediaObjectSpec.generateDemoWav(duration, freq);
    }

    private PresignedObject presignGetObjectOnEndpoint(String baseEndpoint, String objectKey, Instant now) {
        int ttl = 120;
        URI baseUri = URI.create(baseEndpoint);
        String hostHeader = hostHeader(baseUri);
        String amzDate = AMZ_DATE.format(now);
        String dateStamp = DATE_STAMP.format(now);
        String region = properties.getRegion();
        String credentialScope = dateStamp + "/" + region + "/s3/aws4_request";
        String credential = properties.getAccessKey() + "/" + credentialScope;
        String canonicalUri = "/" + urlEncodePath(properties.getBucket() + "/" + objectKey);
        String canonicalQuery = "X-Amz-Algorithm=AWS4-HMAC-SHA256"
                + "&X-Amz-Credential=" + urlEncode(credential)
                + "&X-Amz-Date=" + urlEncode(amzDate)
                + "&X-Amz-Expires=" + ttl
                + "&X-Amz-SignedHeaders=host";
        String canonicalRequest = "GET\n" + canonicalUri + "\n" + canonicalQuery + "\nhost:" + hostHeader + "\n\nhost\nUNSIGNED-PAYLOAD";
        String stringToSign = "AWS4-HMAC-SHA256\n" + amzDate + "\n" + credentialScope + "\n" + sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));
        byte[] signingKey = getSignatureKey(properties.getSecretKey(), dateStamp, region, "s3");
        String signature = HexFormat.of().formatHex(hmacSha256(signingKey, stringToSign));
        return new PresignedObject(objectKey, baseEndpoint + canonicalUri + "?" + canonicalQuery + "&X-Amz-Signature=" + signature,
                OffsetDateTime.ofInstant(now.plusSeconds(ttl), ZoneOffset.UTC));
    }

    private void ensureBucketQuietly(Instant now) {
        try {
            URI baseUri = URI.create(properties.getEndpoint());
            String hostHeader = hostHeader(baseUri);
            String amzDate = AMZ_DATE.format(now);
            String dateStamp = DATE_STAMP.format(now);
            String region = properties.getRegion();
            String credentialScope = dateStamp + "/" + region + "/s3/aws4_request";
            String emptyHash = sha256Hex(new byte[0]);
            String canonicalUri = "/" + urlEncodePath(properties.getBucket());
            String canonicalHeaders = "host:" + hostHeader + "\n"
                    + "x-amz-content-sha256:" + emptyHash + "\n"
                    + "x-amz-date:" + amzDate + "\n";
            String signedHeaders = "host;x-amz-content-sha256;x-amz-date";
            String canonicalRequest = "PUT\n" + canonicalUri + "\n\n" + canonicalHeaders + "\n" + signedHeaders + "\n" + emptyHash;
            String stringToSign = "AWS4-HMAC-SHA256\n" + amzDate + "\n" + credentialScope + "\n" + sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));
            byte[] signingKey = getSignatureKey(properties.getSecretKey(), dateStamp, region, "s3");
            String signature = HexFormat.of().formatHex(hmacSha256(signingKey, stringToSign));
            String authorization = "AWS4-HMAC-SHA256 Credential=" + properties.getAccessKey() + "/" + credentialScope
                    + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getEndpoint() + canonicalUri))
                    .timeout(Duration.ofSeconds(3))
                    .header("x-amz-content-sha256", emptyHash)
                    .header("x-amz-date", amzDate)
                    .header("Authorization", authorization)
                    .PUT(HttpRequest.BodyPublishers.noBody())
                    .build();
            client().send(request, HttpResponse.BodyHandlers.discarding());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ignored) {
            // If bucket already exists or server handles PUT directly, proceed to object PUT.
        }
    }

    private static String hostHeader(URI uri) {
        String host = uri.getHost() == null ? "127.0.0.1" : uri.getHost();
        int port = uri.getPort();
        if (port > 0 && !(("http".equalsIgnoreCase(uri.getScheme()) && port == 80) || ("https".equalsIgnoreCase(uri.getScheme()) && port == 443))) {
            return host + ":" + port;
        }
        return host;
    }

    private static String urlEncodePath(String path) {
        String[] segments = path.split("/");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                sb.append('/');
            }
            sb.append(urlEncode(segments[i]));
        }
        return sb.toString();
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(data));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static byte[] hmacSha256(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("HmacSHA256 unavailable", ex);
        }
    }

    private static byte[] getSignatureKey(String secretKey, String dateStamp, String regionName, String serviceName) {
        byte[] kSecret = ("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8);
        byte[] kDate = hmacSha256(kSecret, dateStamp);
        byte[] kRegion = hmacSha256(kDate, regionName);
        byte[] kService = hmacSha256(kRegion, serviceName);
        return hmacSha256(kService, "aws4_request");
    }
}
