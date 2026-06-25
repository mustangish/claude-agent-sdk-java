package com.anthropic.claude.sdk.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Which rate limit window applies. */
public enum RateLimitType {
    FIVE_HOUR("five_hour"),
    SEVEN_DAY("seven_day"),
    SEVEN_DAY_OPUS("seven_day_opus"),
    SEVEN_DAY_SONNET("seven_day_sonnet"),
    OVERAGE("overage");

    private final String wireValue;

    RateLimitType(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static RateLimitType fromWire(String value) {
        for (RateLimitType t : values()) {
            if (t.wireValue.equals(value)) return t;
        }
        throw new IllegalArgumentException("Unknown RateLimitType: " + value);
    }
}
