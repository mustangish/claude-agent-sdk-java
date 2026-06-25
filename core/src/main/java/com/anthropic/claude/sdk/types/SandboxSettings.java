package com.anthropic.claude.sdk.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Sandbox settings for bash command isolation.
 *
 * <p>Filesystem and network restrictions are configured via permission rules (Read/Edit for
 * filesystem, WebFetch for network), NOT via these sandbox settings.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SandboxSettings(
    Boolean enabled,
    Boolean autoAllowBashIfSandboxed,
    List<String> excludedCommands,
    Boolean allowUnsandboxedCommands,
    SandboxNetworkConfig network,
    SandboxIgnoreViolations ignoreViolations,
    Boolean enableWeakerNestedSandbox
) {
    public static SandboxSettings ofEnabled() {
        return new SandboxSettings(true, true, List.of(), true, null, null, false);
    }

    public static SandboxSettings ofDisabled() {
        return new SandboxSettings(false, true, List.of(), true, null, null, false);
    }
}
