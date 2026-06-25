package com.anthropic.claude.sdk.session;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Session metadata returned by {@code listSessions}.
 *
 * <p>Mirrors the Python SDK's {@code SDKSessionInfo} dataclass.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SDKSessionInfo(
    String sessionId,
    String summary,
    long lastModified,
    @JsonInclude(JsonInclude.Include.NON_NULL) Long fileSize,
    @JsonInclude(JsonInclude.Include.NON_NULL) String customTitle,
    @JsonInclude(JsonInclude.Include.NON_NULL) String firstPrompt,
    @JsonInclude(JsonInclude.Include.NON_NULL) String gitBranch,
    @JsonInclude(JsonInclude.Include.NON_NULL) String cwd,
    @JsonInclude(JsonInclude.Include.NON_NULL) String tag,
    @JsonInclude(JsonInclude.Include.NON_NULL) Long createdAt
) {}
