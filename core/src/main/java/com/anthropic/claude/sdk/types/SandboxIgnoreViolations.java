package com.anthropic.claude.sdk.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** Violations to ignore in sandbox. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SandboxIgnoreViolations(List<String> file, List<String> network) {}
