package com.toeic.backend.listening.domain;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Quy tắc kiểm tra loại media (AUDIO/IMAGE), kích thước, thời lượng và sinh dữ liệu mẫu WAV/SVG cho Listening Part 1-4. */
public final class MediaObjectSpec {
    public static final long MAX_FILE_SIZE_BYTES = 25L * 1024L * 1024L;
    private static final Set<String> VISIBILITIES = Set.of("PUBLIC", "AUTHENTICATED", "PRIVATE");
    private static final Map<String, String> AUDIO_MIMES = Map.of(
            "audio/mpeg", "mp3",
            "audio/mp3", "mp3",
            "audio/mp4", "m4a",
            "audio/x-m4a", "m4a",
            "audio/wav", "wav",
            "audio/x-wav", "wav",
            "audio/ogg", "ogg",
            "audio/webm", "webm"
    );
    private static final Map<String, String> IMAGE_MIMES = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/svg+xml", "svg"
    );

    private MediaObjectSpec() {
    }

    public static String normalizeResourceType(String resourceType) {
        if (resourceType == null) {
            throw new IllegalArgumentException("resourceType is required");
        }
        String normalized = resourceType.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("AUDIO", "IMAGE").contains(normalized)) {
            throw new IllegalArgumentException("Unsupported resourceType");
        }
        return normalized;
    }

    public static String normalizeVisibility(String visibility) {
        if (visibility == null || visibility.isBlank()) {
            return "PUBLIC";
        }
        String normalized = visibility.trim().toUpperCase(Locale.ROOT);
        if (!VISIBILITIES.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported visibility");
        }
        return normalized;
    }

    public static String normalizeMimeType(String resourceType, String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            throw new IllegalArgumentException("mimeType is required");
        }
        String normalized = mimeType.trim().toLowerCase(Locale.ROOT);
        int semicolon = normalized.indexOf(';');
        if (semicolon > 0) {
            normalized = normalized.substring(0, semicolon).trim();
        }
        Map<String, String> allowed = "AUDIO".equals(resourceType) ? AUDIO_MIMES : IMAGE_MIMES;
        if (!allowed.containsKey(normalized)) {
            throw new IllegalArgumentException("Unsupported mimeType for " + resourceType);
        }
        if ("audio/mp3".equals(normalized)) {
            return "audio/mpeg";
        }
        if ("audio/x-wav".equals(normalized)) {
            return "audio/wav";
        }
        return normalized;
    }

    public static Integer validateDuration(String resourceType, Integer durationSeconds) {
        if ("IMAGE".equals(resourceType)) {
            if (durationSeconds != null) {
                throw new IllegalArgumentException("IMAGE cannot have durationSeconds");
            }
            return null;
        }
        if (durationSeconds == null) {
            return null;
        }
        if (durationSeconds < 1 || durationSeconds > 7200) {
            throw new IllegalArgumentException("durationSeconds must be between 1 and 7200");
        }
        return durationSeconds;
    }

    public static void validateSize(long size) {
        if (size <= 0 || size > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("File size must be between 1 byte and 25 MB");
        }
    }

    public static String buildObjectKey(String resourceType, String mimeType, UUID resourceId) {
        Map<String, String> map = "AUDIO".equals(resourceType) ? AUDIO_MIMES : IMAGE_MIMES;
        String ext = map.getOrDefault(mimeType, "bin");
        String folder = "AUDIO".equals(resourceType) ? "audio" : "image";
        return folder + "/" + resourceId + "." + ext;
    }

    /** Sinh file WAV PCM 16-bit chuẩn có giai điệu thông báo TOEIC để phát trực tiếp trên trình duyệt khi chạy demo. */
    public static byte[] generateDemoWav(int durationSeconds, int baseFreqHz) {
        int seconds = Math.max(2, Math.min(durationSeconds, 12));
        int sampleRate = 8000;
        int numSamples = seconds * sampleRate;
        int dataSize = numSamples * 2;
        ByteBuffer buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(36 + dataSize);
        buffer.put("WAVE".getBytes(StandardCharsets.US_ASCII));
        buffer.put("fmt ".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(16);
        buffer.putShort((short) 1); // PCM
        buffer.putShort((short) 1); // Mono
        buffer.putInt(sampleRate);
        buffer.putInt(sampleRate * 2);
        buffer.putShort((short) 2);
        buffer.putShort((short) 16);
        buffer.put("data".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(dataSize);

        double[] notes = {baseFreqHz, baseFreqHz * 1.25, baseFreqHz * 1.5, baseFreqHz * 1.25};
        for (int i = 0; i < numSamples; i++) {
            double t = (double) i / sampleRate;
            int step = ((int) (t * 2)) % notes.length;
            double freq = notes[step];
            double env = Math.min(1.0, (t * 2 - Math.floor(t * 2)) * 8.0) * Math.exp(-1.8 * (t * 2 - Math.floor(t * 2)));
            short sample = (short) (Math.sin(2.0 * Math.PI * freq * t) * 9000 * env);
            buffer.putShort(sample);
        }
        return buffer.array();
    }

    /** Sinh hình minh họa SVG trực quan cho câu hỏi Part 1 (Photographs) và bảng biểu Part 3-4. */
    public static byte[] generateDemoSvg(String label) {
        String safeLabel = (label == null || label.isBlank()) ? "TOEIC Listening Visual" : label.replaceAll("[^A-Za-z0-9 _:\\-.,/]", "");
        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 640 360" width="640" height="360">
                  <defs>
                    <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
                      <stop offset="0%" stop-color="#0f172a"/>
                      <stop offset="100%" stop-color="#1e3a8a"/>
                    </linearGradient>
                  </defs>
                  <rect width="640" height="360" rx="18" fill="url(#bg)"/>
                  <rect x="32" y="32" width="576" height="296" rx="12" fill="#f8fafc" opacity="0.96"/>
                  <rect x="64" y="64" width="220" height="150" rx="10" fill="#dbeafe" stroke="#2563eb" stroke-width="2"/>
                  <circle cx="130" cy="120" r="24" fill="#1d4ed8"/>
                  <rect x="96" y="152" width="68" height="44" rx="8" fill="#3b82f6"/>
                  <rect x="190" y="96" width="70" height="90" rx="6" fill="#93c5fd"/>
                  <rect x="312" y="68" width="256" height="28" rx="6" fill="#1e293b"/>
                  <rect x="312" y="112" width="220" height="18" rx="4" fill="#64748b"/>
                  <rect x="312" y="142" width="240" height="18" rx="4" fill="#94a3b8"/>
                  <rect x="312" y="172" width="180" height="18" rx="4" fill="#cbd5e1"/>
                  <rect x="64" y="242" width="512" height="54" rx="10" fill="#eff6ff" stroke="#bfdbfe"/>
                  <text x="84" y="275" font-family="Segoe UI, Arial, sans-serif" font-size="18" font-weight="700" fill="#1e3a8a">__LABEL__</text>
                </svg>
                """.replace("__LABEL__", safeLabel);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(svg.getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }
}
