package com.example.dish.gateway.dto;

import com.example.dish.common.classifier.IntentType;

/**
 * LLM 抽取的数据结构
 */
public record ExtractedData(
        IntentType intent,
        String dishName,
        String orderId,
        String refundReason,
        boolean extractionFailed
) {}
