package com.anthropic.claude.sdk.session;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SessionSummaryTest {

    @Test
    void firstPromptExtractedFromFirstUserMessage() {
        Map<String, Object> entry = Map.of(
            "type", "user",
            "message", Map.of("role", "user", "content", "Hello world")
        );
        Map<String, Object> summary = SessionSummary.foldSessionSummary(null, entry);
        assertThat(summary.get("firstPrompt")).isEqualTo("Hello world");
    }

    @Test
    void firstPromptNotOverwritten() {
        Map<String, Object> prev = Map.of("firstPrompt", "Original");
        Map<String, Object> entry = Map.of(
            "type", "user",
            "message", Map.of("role", "user", "content", "Second prompt")
        );
        Map<String, Object> summary = SessionSummary.foldSessionSummary(prev, entry);
        assertThat(summary.get("firstPrompt")).isEqualTo("Original");
    }

    @Test
    void firstPromptFromContentArray() {
        Map<String, Object> entry = Map.of(
            "type", "user",
            "message", Map.of("role", "user",
                "content", List.of(Map.of("type", "text", "text", "Block prompt")))
        );
        Map<String, Object> summary = SessionSummary.foldSessionSummary(null, entry);
        assertThat(summary.get("firstPrompt")).isEqualTo("Block prompt");
    }

    @Test
    void resultMessageUpdatesSummary() {
        Map<String, Object> entry = Map.of(
            "type", "result",
            "result", "The answer is 42",
            "total_cost_usd", 0.001234
        );
        Map<String, Object> summary = SessionSummary.foldSessionSummary(null, entry);
        assertThat(summary.get("summary")).isEqualTo("The answer is 42");
        assertThat(summary.get("totalCostUsd")).isEqualTo(0.001234);
    }

    @Test
    void summaryMessageUpdatesSummary() {
        Map<String, Object> entry = Map.of(
            "type", "summary",
            "summary", "User greeted Claude"
        );
        Map<String, Object> summary = SessionSummary.foldSessionSummary(null, entry);
        assertThat(summary.get("summary")).isEqualTo("User greeted Claude");
    }

    @Test
    void resultOverridesPreviousSummary() {
        Map<String, Object> prev = new HashMap<>();
        prev.put("summary", "old summary");
        Map<String, Object> entry = Map.of(
            "type", "result",
            "result", "new summary"
        );
        Map<String, Object> summary = SessionSummary.foldSessionSummary(prev, entry);
        assertThat(summary.get("summary")).isEqualTo("new summary");
    }

    @Test
    void gitBranchAndCwdCapturedOnce() {
        Map<String, Object> entry1 = Map.of("type", "user", "gitBranch", "main", "cwd", "/tmp");
        Map<String, Object> entry2 = Map.of("type", "user", "gitBranch", "feature", "cwd", "/home");

        Map<String, Object> s = SessionSummary.foldSessionSummary(null, entry1);
        s = SessionSummary.foldSessionSummary(s, entry2);

        assertThat(s.get("gitBranch")).isEqualTo("main");  // first wins
        assertThat(s.get("cwd")).isEqualTo("/tmp");
    }

    @Test
    void summaryEntryToSdkInfo() {
        Map<String, Object> summary = Map.of(
            "summary", "Test session",
            "firstPrompt", "Hello",
            "gitBranch", "main",
            "cwd", "/tmp"
        );
        SDKSessionInfo info = SessionSummary.summaryEntryToSdkInfo("sess-1", 12345L, summary);
        assertThat(info.sessionId()).isEqualTo("sess-1");
        assertThat(info.summary()).isEqualTo("Test session");
        assertThat(info.firstPrompt()).isEqualTo("Hello");
        assertThat(info.lastModified()).isEqualTo(12345L);
    }

    @Test
    void summaryEntryToSdkInfoWithNullSummary() {
        SDKSessionInfo info = SessionSummary.summaryEntryToSdkInfo("sess-1", 100L, null);
        assertThat(info.sessionId()).isEqualTo("sess-1");
        assertThat(info.summary()).isEmpty();
    }

    @Test
    void longFirstPromptIsTruncated() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 500; i++) sb.append("a");
        Map<String, Object> entry = Map.of(
            "type", "user",
            "message", Map.of("role", "user", "content", sb.toString())
        );
        Map<String, Object> summary = SessionSummary.foldSessionSummary(null, entry);
        String firstPrompt = (String) summary.get("firstPrompt");
        assertThat(firstPrompt.length()).isLessThanOrEqualTo(203);  // 200 + "..."
        assertThat(firstPrompt).endsWith("...");
    }
}
