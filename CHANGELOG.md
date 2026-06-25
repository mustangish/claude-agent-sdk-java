# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.0-SNAPSHOT] - 2026-06-26

### Added

- **Maven multi-module project** with parent POM, `claude-agent-sdk-core`, `claude-agent-sdk-testing`,
  and `examples/quickstart` modules. Java 21 LTS, virtual threads, Jackson, JUnit 5, AssertJ.
- **Transport abstraction** (`Transport` interface) — pluggable I/O backend.
  Default impl: `SubprocessCliTransport` with line-buffered JSON over stdin/stdout,
  5s/5s/SIGKILL shutdown ladder, `atexit`-style JVM shutdown hook for leaked processes.
- **`ClaudeAgentSdk.query()` facade** + `QuerySession` (try-with-resources Iterable) for one-shot prompts.
- **`ClaudeSdkClient`** (interactive, implements `AutoCloseable`):
  `connect`, `query`, `receiveMessages`, `receiveResponse`, `interrupt`,
  `setPermissionMode`, `setModel`, `rewindFiles`, `reconnectMcpServer`,
  `toggleMcpServer`, `stopTask`, `getMcpStatus`, `getContextUsage`, `getServerInfo`, `disconnect`.
- **Message parser** (`MessageParser`) dispatches on `type` field to sealed `Message` hierarchy
  (UserMessage, AssistantMessage, SystemMessage + 7 subclasses, ResultMessage, StreamEvent, RateLimitEvent).
- **Control protocol**:
  - `ControlProtocol` types (control_request/response, sealed `ControlRequest`).
  - `PendingControlRequests` request_id correlation (CompletableFuture-based).
  - `InternalQuery` orchestrates: initialize handshake, read loop, can_use_tool, hook_callback,
    mcp_message dispatch, interrupt, set_permission_mode, set_model, rewind_files,
    mcp_reconnect, mcp_toggle, stop_task, mcp_status, get_context_usage.
- **Hooks** (`HookEvent`, `HookMatcher`, `HookCallback`, `HookInput` sealed with 10 variants,
  `HookSpecificOutput` sealed with 8 variants, `HookJSONOutput`, `AsyncHookJSONOutput`).
- **Permission callbacks** (`CanUseTool` functional interface, `PermissionResult` sealed,
  `ToolPermissionContext`, `PermissionUpdate`, `PermissionRuleValue`).
- **In-process SDK MCP servers** (`SdkMcpServer.builder`, `@Tool` annotation, `ToolResult`,
  `SdkMcpRegistry`, `InternalQuery.dispatchMcpMessage` handling `initialize`/`tools/list`/`tools/call`/`notifications/initialized`).
- **External stdio MCP servers** (`ExternalStdioMcpServer`).
- **Session store** (`SessionStore` interface with default `UnsupportedOperationException`,
  `InMemorySessionStore` thread-safe impl, `TranscriptMirrorBatcher` with retry/backoff,
  `SessionResume.materialize`/`materializeFromStore`, `SessionListing`, `SessionMutations`
  rename/tag/delete/fork, `SessionImport`, `SessionSummary`).
- **MCP types** (`McpServerInfo`, `McpServerStatus`, `McpServerStatusConfig`, `McpStatusResponse`,
  `McpToolInfo`, `McpToolAnnotations`, `McpClaudeAIProxyServerConfig`, `McpSdkServerConfigStatus`).
- **Context usage** (`ContextUsageResponse`, `ContextUsageCategory`).
- **Shared Jackson configuration** (`JacksonSupport` — single `ObjectMapper` with `SNAKE_CASE`
  strategy, used by all 7 `new ObjectMapper()` sites).
- **`MirrorErrorMessage`** top-level record for `SystemMessage` mirror errors.
- **ClaudeAgentOptions** with 30+ fields, builder API, auto-injected skills.
- **Bundled CLI packaging** — Maven classifier JARs for 5 platforms, `os-maven-plugin` for auto-detection.
- **Publishing** — `central-publishing-maven-plugin` profile with GPG signing, source/javadoc jars.
- **CI workflows**:
  - `ci.yml` — Ubuntu/macOS/Windows × Java 21, `mvn -B -ntp clean verify` gate.
  - `release.yml` — Maven Central publish on `v*` tag push.
  - `rewrite-check.yml` — Python/Java divergence check (weekly + on PR touching core).
- **Project docs** — `CLAUDE.md`, `CONTRIBUTING.md`, `RELEASING.md`, `REWRITE_CHECK.md`,
  `scripts/rewrite-check.sh` driving the loop.
- **113 tests** covering types round-trip, message parsing, transport, control protocol, MCP, sessions,
  mirror batcher, hooks, fake-CLI shell scripts in `claude-agent-sdk-core/src/test/resources/`.
- **16 examples** in `examples/quickstart/` mirroring Python SDK's examples directory.

### Changed

- **Module layout renamed**: `core/` → `claude-agent-sdk-core/`, `testing/` → `claude-agent-sdk-testing/`.
- **In-process MCP merged into core**: the separate `mcp/` module (`com.anthropic:claude-agent-sdk-mcp`)
  is gone; `SdkMcpServer`, `SdkMcpRegistry`, `@Tool`, `ToolResult`, and all MCP support types
  now ship in `com.anthropic:claude-agent-sdk-core` under `com.anthropic.claude.sdk.mcp.*`.

### Notes

- This is the first release; not yet published to Maven Central.
- Wire-format compatibility with Python SDK v0.2.110 verified for all 30 areas in `REWRITE_CHECK.md`
  (transport, control protocol, hooks, can_use_tool, in-process MCP, session store mirror format, …).
- Bundled CLI binary JAR not yet published (requires running `scripts/download-cli.sh` first).
