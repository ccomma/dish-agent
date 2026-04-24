package com.example.dish.memory.support;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * memory 模块本地向量化支撑。
 * 主要用于没有外部 embedding provider 时的 fallback 向量计算，
 * 以及短期召回时的关键词/向量混合打分辅助。
 */
public final class MemoryVectorSupport {

    private MemoryVectorSupport() {
    }

    /**
     * 使用轻量 hash 方式把文本映射成固定维度向量。
     * 这里不是语义最强的 embedding，而是为了本地 fallback 和演示链路准备的稳定近似实现。
     */
    public static double[] embed(String text, int dimensions) {
        int dim = Math.max(dimensions, 16);
        double[] vector = new double[dim];
        List<String> tokens = tokenize(text);
        if (tokens.isEmpty()) {
            return vector;
        }

        for (String token : tokens) {
            // 1. primary 槽位承接 token 的主哈希命中，保留最直接的区分度。
            int hash = Math.abs(token.hashCode());
            int primary = hash % dim;
            // 2. secondary 槽位引入第二落点，减少纯单槽位映射时的哈希碰撞。
            int secondary = (hash / dim + token.length()) % dim;
            vector[primary] += 1.0;
            vector[secondary] += 0.35;
        }
        // 3. 归一化后再参与余弦计算，避免长文本仅因 token 更多就天然得分更高。
        normalize(vector);
        return vector;
    }

    public static double cosine(double[] left, double[] right) {
        if (left == null || right == null || left.length == 0 || right.length == 0) {
            return 0;
        }
        int len = Math.min(left.length, right.length);
        double sum = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int i = 0; i < len; i++) {
            sum += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return 0;
        }
        return sum / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    public static String serialize(double[] vector) {
        return Arrays.toString(vector);
    }

    public static double[] deserialize(String payload) {
        if (StringUtils.isBlank(payload)) {
            return new double[0];
        }
        String normalized = payload.trim();
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        if (StringUtils.isBlank(normalized)) {
            return new double[0];
        }
        String[] parts = normalized.split(",");
        double[] vector = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vector[i] = Double.parseDouble(parts[i].trim());
        }
        return vector;
    }

    public static float[] toFloatVector(double[] vector) {
        if (vector == null || vector.length == 0) {
            return new float[0];
        }
        float[] result = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            result[i] = (float) vector[i];
        }
        return result;
    }

    public static double keywordOverlap(String query, String content) {
        Set<String> queryTokens = new LinkedHashSet<>(tokenize(query));
        Set<String> contentTokens = new LinkedHashSet<>(tokenize(content));
        if (queryTokens.isEmpty() || contentTokens.isEmpty()) {
            return 0;
        }
        long hits = queryTokens.stream().filter(contentTokens::contains).count();
        return (double) hits / queryTokens.size();
    }

    private static List<String> tokenize(String text) {
        if (StringUtils.isBlank(text)) {
            return List.of();
        }
        // 1. 统一做小写化和字符清洗，把英文、数字、中文都压成可切分的文本流。
        String normalized = text.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}\\p{IsIdeographic}]+", " ")
                .trim();
        if (StringUtils.isBlank(normalized)) {
            return List.of();
        }

        List<String> tokens = new ArrayList<>();
        for (String part : normalized.split("\\s+")) {
            if (StringUtils.isNotBlank(part)) {
                tokens.add(part);
            }
            // 2. 中文额外补充双字切分，提升短中文问句的关键词命中率。
            if (part.length() > 1 && containsCjk(part)) {
                for (int i = 0; i < part.length() - 1; i++) {
                    tokens.add(part.substring(i, i + 2));
                }
            }
        }
        return tokens;
    }

    private static boolean containsCjk(String text) {
        return text.codePoints().anyMatch(codePoint ->
                Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN);
    }

    private static void normalize(double[] vector) {
        double sum = 0;
        for (double value : vector) {
            sum += value * value;
        }
        if (sum == 0) {
            return;
        }
        double norm = Math.sqrt(sum);
        for (int i = 0; i < vector.length; i++) {
            vector[i] = vector[i] / norm;
        }
    }
}
