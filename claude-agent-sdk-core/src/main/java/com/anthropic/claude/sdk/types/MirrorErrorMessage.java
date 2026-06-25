package com.anthropic.claude.sdk.types;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * System message emitted when a {@link SessionStore#append} call fails.
 *
 * <p>Non-fatal — the local-disk transcript is already durable, so the session
 * continues but the secondary mirror copy is incomplete. Mirrors the Python
 * SDK's {@code MirrorErrorMessage} dataclass.
 *
 * <p>This is the top-level convenience type. The Java side also has a
 * nested {@link SystemMessage.MirrorErrorMessage} (a record on the
 * sealed {@link SystemMessage} interface) that
 * {@link com.anthropic.claude.sdk.internal.message.MessageParser}
 * produces. Use {@link #from(SystemMessage.MirrorErrorMessage)} to
 * convert the parser's output to this top-level type, which carries
 * the same field set.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MirrorErrorMessage(
    String subtype,
    @JsonInclude(JsonInclude.Include.NON_NULL) SessionKey key,
    String error,
    Map<String, Object> data
) {
    /** Convenience factory that mirrors the nested type. */
    public static MirrorErrorMessage from(SystemMessage.MirrorErrorMessage nested) {
        return new MirrorErrorMessage(nested.subtype(), nested.key(), nested.error(), nested.data());
    }
}
