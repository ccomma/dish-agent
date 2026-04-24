package com.example.dish.memory.support;

import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Base64;

/**
 * execution runtime 快照编解码器。
 */
public final class ExecutionRuntimeStorageCodec {

    private ExecutionRuntimeStorageCodec() {
    }

    /**
     * 把运行态快照编码成 Base64 文本，便于作为 Redis value 存储。
     */
    public static String encode(Object value) {
        if (value == null) {
            return null;
        }
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
                objectOutputStream.writeObject(value);
            }
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (IOException ex) {
            throw new IllegalStateException("failed to encode execution runtime payload", ex);
        }
    }

    @SuppressWarnings("unchecked")
    /**
     * 从 Base64 文本还原运行态快照。
     */
    public static <T> T decode(String payload, Class<T> type) {
        if (StringUtils.isBlank(payload)) {
            return null;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(payload);
            try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
                Object value = objectInputStream.readObject();
                return type.cast(value);
            }
        } catch (IOException | ClassNotFoundException ex) {
            throw new IllegalStateException("failed to decode execution runtime payload", ex);
        }
    }
}
