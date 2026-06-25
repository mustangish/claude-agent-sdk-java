package com.anthropic.claude.sdk.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** Network configuration for sandbox. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SandboxNetworkConfig(
    List<String> allowedDomains,
    List<String> deniedDomains,
    Boolean allowManagedDomainsOnly,
    List<String> allowUnixSockets,
    Boolean allowAllUnixSockets,
    Boolean allowLocalBinding,
    List<String> allowMachLookup,
    Integer httpProxyPort,
    Integer socksProxyPort
) {}
