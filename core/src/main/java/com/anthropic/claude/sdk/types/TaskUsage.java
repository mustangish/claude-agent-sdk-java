package com.anthropic.claude.sdk.types;

/** Usage statistics reported in task progress/notification messages. */
public record TaskUsage(int totalTokens, int toolUses, int durationMs) {}
