package com.example.dish.memory.service.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Base64;

final class ExecutionRuntimeStorageCodec {

    private ExecutionRuntimeStorageCodec() {
    }

    static String encode(Object value) {
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
    static <T> T decode(String payload, Class<T> type) {
        if (payload == null || payload.isBlank()) {
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
