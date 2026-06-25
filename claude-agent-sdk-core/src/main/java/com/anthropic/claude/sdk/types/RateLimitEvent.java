package com.anthropic.claude.sdk.types;

/** Emitted when rate-limit status changes. */
public record RateLimitEvent(RateLimitInfo rateLimitInfo, String uuid, String sessionId) implements Message {}
