package com.example.dish.memory.service.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class MemoryVectorSupport {

    private MemoryVectorSupport() {
    }

    static double[] embed(String text, int dimensions) {
        int dim = Math.max(dimensions, 16);
        double[] vector = new double[dim];
        List<String> tokens = tokenize(text);
        if (tokens.isEmpty()) {
            return vector;
        }

        for (String token : tokens) {
            int hash = Math.abs(token.hashCode());
            int primary = hash % dim;
            int secondary = (hash / dim + token.length()) % dim;
            vector[primary] += 1.0;
            vector[secondary] += 0.35;
        }
        normalize(vector);
        return vector;
    }

    static double cosine(double[] left, double[] right) {
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

    static String serialize(double[] vector) {
        return Arrays.toString(vector);
    }

    static double[] deserialize(String payload) {
        if (payload == null || payload.isBlank()) {
            return new double[0];
        }
        String normalized = payload.trim();
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        if (normalized.isBlank()) {
            return new double[0];
        }
        String[] parts = normalized.split(",");
        double[] vector = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vector[i] = Double.parseDouble(parts[i].trim());
        }
        return vector;
    }

    static float[] toFloatVector(double[] vector) {
        if (vector == null || vector.length == 0) {
            return new float[0];
        }
        float[] result = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            result[i] = (float) vector[i];
        }
        return result;
    }

    static double keywordOverlap(String query, String content) {
        Set<String> queryTokens = new LinkedHashSet<>(tokenize(query));
        Set<String> contentTokens = new LinkedHashSet<>(tokenize(content));
        if (queryTokens.isEmpty() || contentTokens.isEmpty()) {
            return 0;
        }
        long hits = queryTokens.stream().filter(contentTokens::contains).count();
        return (double) hits / queryTokens.size();
    }

    private static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String normalized = text.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}\\p{IsIdeographic}]+", " ")
                .trim();
        if (normalized.isBlank()) {
            return List.of();
        }

        List<String> tokens = new ArrayList<>();
        for (String part : normalized.split("\\s+")) {
            if (!part.isBlank()) {
                tokens.add(part);
            }
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
