package com.anthropic.claude.sdk.types;

/** Thinking content block (extended reasoning). */
public record ThinkingBlock(String thinking, String signature) implements ContentBlock {}
