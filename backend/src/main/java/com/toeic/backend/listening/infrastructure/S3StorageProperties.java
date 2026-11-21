package com.toeic.backend.listening.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Cấu hình kết nối Object Storage tương thích S3 (MinIO Docker / AWS S3 / Cloudflare R2). */
@ConfigurationProperties(prefix = "app.storage.s3")
public class S3StorageProperties {
    private String endpoint = "http://127.0.0.1:9000";
    private String publicEndpoint = "http://127.0.0.1:9000";
    private String region = "us-east-1";
    private String bucket = "toeic-media";
    private String accessKey = "";
    private String secretKey = "";
    private int presignTtlSeconds = 3600;

    public String getEndpoint() {
        return endpoint == null || endpoint.isBlank() ? "http://127.0.0.1:9000" : stripTrailingSlash(endpoint.trim());
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getPublicEndpoint() {
        return publicEndpoint == null || publicEndpoint.isBlank() ? getEndpoint() : stripTrailingSlash(publicEndpoint.trim());
    }

    public void setPublicEndpoint(String publicEndpoint) {
        this.publicEndpoint = publicEndpoint;
    }

    public String getRegion() {
        return region == null || region.isBlank() ? "us-east-1" : region.trim();
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getBucket() {
        return bucket == null || bucket.isBlank() ? "toeic-media" : bucket.trim();
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getAccessKey() {
        return accessKey == null || accessKey.isBlank() ? "minio-local" : accessKey.trim();
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey == null || secretKey.isBlank() ? "minio-local-signing-key" : secretKey.trim();
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public int getPresignTtlSeconds() {
        return Math.max(60, Math.min(presignTtlSeconds, 604800));
    }

    public void setPresignTtlSeconds(int presignTtlSeconds) {
        this.presignTtlSeconds = presignTtlSeconds;
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
