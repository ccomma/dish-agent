package com.example.dish.memory.support;

import com.example.dish.control.memory.model.MemoryLayer;
import com.example.dish.memory.model.MemoryEntry;
import org.apache.commons.lang3.StringUtils;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 记忆时间线 entry 的稳定文本编解码器。
 */
public final class MemoryStorageCodec {

    private MemoryStorageCodec() {
    }

    /**
     * 把时间线 entry 编码成稳定的文本格式，避免直接依赖 JSON 库版本差异。
     */
    public static String encode(MemoryEntry entry) {
        // 1. 先把固定字段按稳定顺序展开，保证编码结果可读且可预期。
        Map<String, String> fields = new LinkedHashMap<>();
        put(fields, "entryId", entry.entryId());
        put(fields, "memoryLayer", entry.memoryLayer() != null ? entry.memoryLayer().name() : null);
        put(fields, "memoryType", entry.memoryType());
        put(fields, "content", entry.content());
        put(fields, "traceId", entry.traceId());
        put(fields, "createdAt", entry.createdAt() != null ? entry.createdAt().toString() : null);
        put(fields, "sequence", String.valueOf(entry.sequence()));
        put(fields, "storageSource", entry.storageSource());
        put(fields, "metadata", encodeMetadata(entry.metadata()));

        // 2. 再把字段逐行拼接成 `key=value` 文本，便于 Redis 和日志调试。
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> field : fields.entrySet()) {
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append(field.getKey()).append('=').append(escape(field.getValue()));
        }
        return builder.toString();
    }

    /**
     * 从稳定文本格式还原时间线 entry。
     */
    public static MemoryEntry decode(String payload) {
        if (StringUtils.isBlank(payload)) {
            return null;
        }

        // 1. 先按行解析基础字段，并执行转义恢复。
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

        // 2. 再把 metadata、时间和枚举字段还原为内部模型。
        return new MemoryEntry(
                fields.get("entryId"),
                parseLayer(fields.get("memoryLayer")),
                memoryType,
                content,
                decodeMetadata(fields.get("metadata")),
                fields.get("traceId"),
                parseInstant(fields.get("createdAt")),
                parseLong(fields.get("sequence")),
                fields.get("storageSource")
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
        // 1. metadata 采用 `key:typeValue` + `|` 分隔，兼顾可读性和基础类型保真。
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
        if (StringUtils.isBlank(payload)) {
            return Map.of();
        }
        // 1. 逐字符恢复 metadata token，避免被转义后的 `|`、`\n`、`:` 误切分。
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
        // 2. 把最后一个 token 也补充解码，保持和循环内相同行为。
        decodeMetadataToken(token.toString(), metadata);
        return metadata;
    }

    private static void decodeMetadataToken(String token, Map<String, Object> metadata) {
        if (StringUtils.isBlank(token)) {
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
        if (StringUtils.isBlank(value)) {
            return Instant.now();
        }
        return Instant.parse(value);
    }

    private static MemoryLayer parseLayer(String value) {
        if (StringUtils.isBlank(value)) {
            return MemoryLayer.SHORT_TERM_SESSION;
        }
        return MemoryLayer.valueOf(value);
    }

    private static long parseLong(String value) {
        if (StringUtils.isBlank(value)) {
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
