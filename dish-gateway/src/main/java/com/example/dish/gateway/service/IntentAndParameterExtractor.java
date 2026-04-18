package com.example.dish.gateway.service;

import com.example.dish.gateway.dto.ExtractedData;

/**
 * 意图识别 + 参数抽取器实现
 *
 * 使用 LLM 同时识别意图和抽取关键参数
 */
public interface IntentAndParameterExtractor {

    ExtractedData extract(String userInput);
}
