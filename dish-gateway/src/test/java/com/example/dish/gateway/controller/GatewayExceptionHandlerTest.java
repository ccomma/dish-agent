package com.example.dish.gateway.controller;

import com.example.dish.gateway.config.GatewayExceptionHandler;
import com.example.dish.gateway.dto.GatewayResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class GatewayExceptionHandlerTest {

    private final GatewayExceptionHandler handler = new GatewayExceptionHandler();

    @Test
    void shouldReturnBadRequestResponseForIllegalArgumentException() {
        GatewayResponse response = handler.handleIllegalArgumentException(new IllegalArgumentException("参数错误"));

        Assertions.assertFalse(response.isSuccess());
        Assertions.assertEquals("参数错误", response.getContent());
        Assertions.assertEquals("Gateway", response.getAgentName());
    }

    @Test
    void shouldReturnGenericFailureResponseForUnexpectedException() {
        GatewayResponse response = handler.handleException(new RuntimeException("boom"));

        Assertions.assertFalse(response.isSuccess());
        Assertions.assertEquals("系统繁忙，请稍后重试", response.getContent());
        Assertions.assertEquals("Gateway", response.getAgentName());
    }
}
