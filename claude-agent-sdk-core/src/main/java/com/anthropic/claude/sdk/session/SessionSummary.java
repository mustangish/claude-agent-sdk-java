package com.anthropic.claude.sdk.session;

import com.anthropic.claude.sdk.types.SessionStore;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Incrementally-maintained session summaries.
 *
 * <p>Mirrors Python SDK's {@code _internal/session_summary.py}. Folds each new transcript
 * entry into a per-session summary record that can be persisted to a {@link SessionStore}
 * alongside the entries themselves for fast metadata-only listing.
 */
public final class SessionSummary {

    private SessionSummary() {}

    /** Fold a new entry into the previous summary, returning an updated map. */
    public static Map<String, Object> foldSessionSummary(
        Map<String, Object> prev,
        Map<String, Object> entry
    ) {
        Map<String, Object> out = prev != null ? new LinkedHashMap<>(prev) : new LinkedHashMap<>();
        if (entry == null) return out;

        // First user prompt
        if (!out.containsKey("firstPrompt")) {
            String firstPrompt = extractFirstPrompt(entry);
            if (firstPrompt != null && !firstPrompt.isBlank()) {
                out.put("firstPrompt", truncate(firstPrompt, 200));
            }
        }

        // Last summary / result text
        String type = (String) entry.get("type");
        if ("summary".equals(type)) {
            String summary = (String) entry.get("summary");
            if (summary != null) out.put("summary", truncate(summary, 200));
        } else if ("result".equals(type)) {
            String result = (String) entry.get("result");
            if (result != null && !result.isBlank()) {
                out.put("summary", truncate(result, 200));
            }
            Object cost = entry.get("total_cost_usd");
            if (cost instanceof Number) out.put("totalCostUsd", ((Number) cost).doubleValue());
        }

        // Git branch / cwd snapshot (first occurrence)
        if (!out.containsKey("gitBranch")) {
            Object gb = entry.get("gitBranch");
            if (gb instanceof String && !((String) gb).isBlank()) out.put("gitBranch", gb);
        }
        if (!out.containsKey("cwd")) {
            Object cwd = entry.get("cwd");
            if (cwd instanceof String && !((String) cwd).isBlank()) out.put("cwd", cwd);
        }

        // Last activity timestamp
        Object ts = entry.get("timestamp");
        if (ts instanceof String) out.put("lastActivityAt", ts);

        return out;
    }

    /** Convert a summary map to an {@link SDKSessionInfo}. */
    public static SDKSessionInfo summaryEntryToSdkInfo(String sessionId, long mtime,
                                                     Map<String, Object> summary) {
        if (summary == null) {
            return new SDKSessionInfo(sessionId, "", mtime, null,
                null, null, null, null, null, mtime);
        }
        return new SDKSessionInfo(
            sessionId,
            (String) summary.getOrDefault("summary", ""),
            mtime,
            null,
            null,
            (String) summary.get("firstPrompt"),
            (String) summary.get("gitBranch"),
            (String) summary.get("cwd"),
            null,
            mtime
        );
    }

    private static String extractFirstPrompt(Map<String, Object> entry) {
        Object message = entry.get("message");
        if (!(message instanceof Map)) return null;
        Object content = ((Map<?, ?>) message).get("content");
        if (content instanceof String) return (String) content;
        if (content instanceof java.util.List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            if (first instanceof Map<?, ?>) {
                Object text = ((Map<?, ?>) first).get("text");
                return text instanceof String ? (String) text : null;
            }
        }
        return null;
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
