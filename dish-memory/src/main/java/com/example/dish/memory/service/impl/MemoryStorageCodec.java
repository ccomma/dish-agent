package com.example.dish.memory.service.impl;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

final class MemoryStorageCodec {

    private MemoryStorageCodec() {
    }

    static String encode(MemoryReadServiceImpl.MemoryEntry entry) {
        Map<String, String> fields = new LinkedHashMap<>();
        put(fields, "entryId", entry.entryId());
        put(fields, "memoryType", entry.memoryType());
        put(fields, "content", entry.content());
        put(fields, "traceId", entry.traceId());
        put(fields, "createdAt", entry.createdAt() != null ? entry.createdAt().toString() : null);
        put(fields, "sequence", String.valueOf(entry.sequence()));
        put(fields, "metadata", encodeMetadata(entry.metadata()));

        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> field : fields.entrySet()) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(field.getKey()).append('=').append(escape(field.getValue()));
        }
        return builder.toString();
    }

    static MemoryReadServiceImpl.MemoryEntry decode(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }

        Map<String, String> fields = new LinkedHashMap<>();
        for (String line : payload.split("\\n")) {
            int idx = line.indexOf('=');
            if (idx <= 0) {
                continue;
            }
            fields.put(line.substring(0, idx), unescape(line.substring(idx + 1)));
        }

        String memoryType = fields.get("memoryType");
        String content = fields.get("content");
        if (memoryType == null || content == null) {
            return null;
        }

        return new MemoryReadServiceImpl.MemoryEntry(
                fields.get("entryId"),
                memoryType,
                content,
                decodeMetadata(fields.get("metadata")),
                fields.get("traceId"),
                parseInstant(fields.get("createdAt")),
                parseLong(fields.get("sequence"))
        );
    }

    private static void put(Map<String, String> fields, String key, String value) {
        if (value != null) {
            fields.put(key, value);
        }
    }

    private static String encodeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            if (builder.length() > 0) {
                builder.append('|');
            }
            builder.append(escape(entry.getKey()))
                    .append(':')
                    .append(typePrefix(entry.getValue()))
                    .append(escape(String.valueOf(entry.getValue())));
        }
        return builder.toString();
    }

    private static Map<String, Object> decodeMetadata(String payload) {
        if (payload == null || payload.isBlank()) {
            return Map.of();
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        StringBuilder token = new StringBuilder();
        boolean escaping = false;
        for (int i = 0; i < payload.length(); i++) {
            char current = payload.charAt(i);
            if (escaping) {
                token.append(current == 'n' ? '\n' : current);
                escaping = false;
                continue;
            }
            if (current == '\\') {
                escaping = true;
                continue;
            }
            if (current == '|') {
                decodeMetadataToken(token.toString(), metadata);
                token.setLength(0);
                continue;
            }
            token.append(current);
        }
        decodeMetadataToken(token.toString(), metadata);
        return metadata;
    }

    private static void decodeMetadataToken(String token, Map<String, Object> metadata) {
        if (token == null || token.isBlank()) {
            return;
        }
        int idx = token.indexOf(':');
        if (idx <= 0 || idx + 2 > token.length()) {
            return;
        }
        String key = token.substring(0, idx);
        String typedValue = token.substring(idx + 1);
        char type = typedValue.charAt(0);
        String raw = typedValue.substring(1);
        metadata.put(unescape(key), parseTypedValue(type, unescape(raw)));
    }

    private static char typePrefix(Object value) {
        if (value instanceof Boolean) {
            return 'b';
        }
        if (value instanceof Integer || value instanceof Long) {
            return 'l';
        }
        if (value instanceof Float || value instanceof Double) {
            return 'd';
        }
        return 's';
    }

    private static Object parseTypedValue(char type, String value) {
        return switch (type) {
            case 'b' -> Boolean.parseBoolean(value);
            case 'l' -> parseLong(value);
            case 'd' -> Double.parseDouble(value);
            default -> value;
        };
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return Instant.now();
        }
        return Instant.parse(value);
    }

    private static long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        return Long.parseLong(value);
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\n", "\\n").replace("|", "\\|").replace(":", "\\:").replace("=", "\\=");
    }

    private static String unescape(String value) {
        StringBuilder builder = new StringBuilder();
        boolean escaping = false;
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (escaping) {
                builder.append(current == 'n' ? '\n' : current);
                escaping = false;
                continue;
            }
            if (current == '\\') {
                escaping = true;
                continue;
            }
            builder.append(current);
        }
        return builder.toString();
    }
}
