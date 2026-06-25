package com.anthropic.claude.sdk.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/** Rate limit status payload. */
public record RateLimitInfo(
    RateLimitStatus status,
    @JsonInclude(JsonInclude.Include.NON_NULL) Long resetsAt,
    @JsonInclude(JsonInclude.Include.NON_NULL) RateLimitType rateLimitType,
    @JsonInclude(JsonInclude.Include.NON_NULL) Double utilization,
    @JsonInclude(JsonInclude.Include.NON_NULL) RateLimitStatus overageStatus,
    @JsonInclude(JsonInclude.Include.NON_NULL) Long overageResetsAt,
    @JsonInclude(JsonInclude.Include.NON_NULL) String overageDisabledReason,
    @JsonInclude(JsonInclude.Include.NON_DEFAULT) Map<String, Object> raw
) {
    public RateLimitInfo(RateLimitStatus status) {
        this(status, null, null, null, null, null, null, Map.of());
    }
}
