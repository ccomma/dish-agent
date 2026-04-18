package com.example.dish.gateway.service.impl;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SessionServiceImplTest {

    @Test
    void shouldBindAndReuseStoreIdForSameSession() {
        SessionServiceImpl service = new SessionServiceImpl();

        String firstStore = service.resolveStoreId("SESSION_001", "STORE_009");
        String secondStore = service.resolveStoreId("SESSION_001", null);
        String conflictingStore = service.resolveStoreId("SESSION_001", "STORE_010");

        Assertions.assertEquals("STORE_009", firstStore);
        Assertions.assertEquals("STORE_009", secondStore);
        Assertions.assertEquals("STORE_009", conflictingStore);
    }

    @Test
    void shouldUseDefaultStoreWhenNoHintAndNoSessionValue() {
        SessionServiceImpl service = new SessionServiceImpl();

        String storeId = service.resolveStoreId("SESSION_ABC", null);

        Assertions.assertEquals("STORE_001", storeId);
    }
}
