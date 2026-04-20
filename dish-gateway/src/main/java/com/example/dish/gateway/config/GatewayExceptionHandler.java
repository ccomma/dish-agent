package com.example.dish.gateway.config;

import com.example.dish.gateway.dto.GatewayResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 网关统一异常处理。
 */
@RestControllerAdvice
public class GatewayExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public GatewayResponse handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("gateway request validation failed: {}", ex.getMessage());

        GatewayResponse response = new GatewayResponse();
        response.setSuccess(false);
        response.setContent(ex.getMessage());
        response.setAgentName("Gateway");
        return response;
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public GatewayResponse handleException(Exception ex) {
        log.error("gateway process failed", ex);

        GatewayResponse response = new GatewayResponse();
        response.setSuccess(false);
        response.setContent("系统繁忙，请稍后重试");
        response.setAgentName("Gateway");
        return response;
    }
}
