# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Java port of the [Claude Agent SDK](../claude-agent-sdk-python). Drives [Claude Code CLI](https://docs.anthropic.com/en/docs/claude-code) as a subprocess and exchanges line-delimited JSON over stdin/stdout. The CLI binary is the system under control — this SDK is the typed I/O layer around it.

## Build & test

Maven multi-module project. Java 21 LTS only. Always pass `-B -ntp` for batch + non-interactive.

```bash
# Compile + run all tests + install to local Maven repo
mvn -B -ntp clean install

# Tests only
mvn -B -ntp clean verify
mvn -B -ntp test                            # tests without install

# Single test class
mvn -B -ntp -pl claude-agent-sdk-core test -Dtest=SubprocessCliTransportTest

# Single test method
mvn -B -ntp -pl claude-agent-sdk-core test -Dtest=SubprocessCliTransportTest#connectsAndReadsCannedMessages

# Single module
mvn -B -ntp -pl claude-agent-sdk-core test
mvn -B -ntp -pl examples/quickstart -am test

# Bundle the CLI binary JAR for the current OS into the artifact
mvn -B -ntp install -P bundled-cli          # auto-detect
mvn -B -ntp install -P bundled-cli,bundled-cli-linux-x86_64  # explicit
./scripts/download-cli.sh                   # populate src/main/resources-binary/ first
mvn -B -ntp package -pl claude-agent-sdk-core -P bundled-cli

# Publish to Maven Central (requires Sonatype token + GPG key)
mvn -B -ntp clean deploy -P release
```

Tests use fake `claude` shell scripts in `claude-agent-sdk-core/src/test/resources/` (`fake-claude.sh`, `eager-claude.sh`, `slow-claude.sh`, `error-claude.sh`) — no real CLI required to run the test suite.

CI matrix (`.github/workflows/ci.yml`): Ubuntu/macOS/Windows × Java 21, runs `mvn -B -ntp clean verify`. Release on `v*` tag push runs `mvn -B -ntp clean deploy -P release` (requires `MAVEN_USERNAME`, `MAVEN_PASSWORD`, `MAVEN_GPG_PRIVATE_KEY`, `MAVEN_GPG_PASSPHRASE` secrets).

## Module layout

```
claude-agent-sdk-java/                    # parent POM (com.anthropic:claude-agent-sdk-java)
├── claude-agent-sdk-core/                # the SDK
│   └── src/main/java/com/anthropic/claude/sdk/
│       ├── ClaudeAgentSdk.java           # public facade (static query/import/session methods)
│       ├── client/ClaudeSdkClient.java   # interactive multi-turn client (AutoCloseable)
│       ├── query/QuerySession.java       # one-shot Iterable< Message> (try-with-resources)
│       ├── transport/Transport.java      # I/O abstraction; default = SubprocessCliTransport
│       ├── internal/                     # InternalQuery, MessageParser, control/ — never import from user code
│       ├── mcp/                          # in-process SDK MCP servers (SdkMcpServer, Tool annotation, SdkMcpRegistry)
│       ├── session/                      # SessionStore impls + listing/mutations/import/resume
│       ├── types/                        # sealed hierarchies + records + enums (ClaudeAgentOptions, Message, …)
│       └── errors/                       # ClaudeSdkError + 5 subtypes
├── claude-agent-sdk-testing/             # SessionStore conformance harness (depends on core)
└── examples/quickstart/                  # 16 runnable examples mirroring Python SDK
```

## Architecture

The SDK has four layers, top-down. **A change in a lower layer should be invisible to higher layers.**

1. **Public API** — `ClaudeAgentSdk` (static facade), `ClaudeSdkClient` (multi-turn), `QuerySession` (one-shot). Both clients implement `AutoCloseable`; always use try-with-resources.
2. **Control protocol** — `internal/control/ControlProtocol.java` defines the wire records (sealed `ControlRequest` union: `can_use_tool`, `hook_callback`, `interrupt`; success/error response records). `internal/control/PendingControlRequests.java` correlates frames by `request_id` via `ConcurrentHashMap<String, CompletableFuture<JsonNode>>`.
3. **InternalQuery** — `internal/InternalQuery.java` is the bidirectional orchestrator. One virtual thread runs the read loop, dispatching each raw JSON node to either `dataQueue` (for the client) or the pending-request map (control responses). Owns the initialize handshake, hook callbacks, `canUseTool` dispatch, and in-process MCP message handling.
4. **Transport** — `transport/Transport.java` interface (`connect` / `write` / `readMessages` / `endInput` / `isReady` / `close`). `SubprocessCliTransport` is the default: spawns `claude --output-format stream-json --verbose --input-format stream-json …`, parses line-delimited JSON, buffers to a 100-element `LinkedBlockingDeque`, and uses a 5s/5s/SIGKILL shutdown ladder. **Custom transports are the supported extension point** (SSH, remote daemon, etc.) — implement `Transport` and pass to `ClaudeAgentSdk.queryWith(...)` or the `ClaudeSdkClient` constructor.

### Wire protocol (line-delimited JSON over stdin/stdout)

- **stdin → CLI**: `{"type":"user",…}` messages + control requests (initialize, can_use_tool response, hook_callback response, mcp_message, interrupt, set_permission_mode, set_model, …).
- **stdout → SDK**: assistant messages + control requests from the CLI + control responses (correlated by `request_id`).
- All request/response records are `@JsonInclude(NON_NULL)` records in `ControlProtocol.java`. New control requests follow the recipe in `CONTRIBUTING.md` (request record → `InternalQuery` method → `ClaudeSdkClient` wrapper → test → example).

### Types

`types/` is the source of truth for both the public API and the JSON wire format. Discriminated unions are sealed interfaces with `@JsonTypeInfo(property = "type"|"subtype")` + `@JsonSubTypes` — e.g. `Message` permits `UserMessage`/`AssistantMessage`/`SystemMessage`/`ResultMessage`/`StreamEvent`/`RateLimitEvent`; `ContentBlock` permits text/thinking/tool_use/tool_result/server_tool_use/server_tool_result. New message types follow the recipe in `CONTRIBUTING.md` (record → add to sealed permits → add to `@JsonSubTypes` → add parser case → round-trip test in `types/MessageRoundTripTest.java`).

`ClaudeAgentOptions` is the central config record (~30+ fields, builder API). `Transport`-side serialization decisions live in `SubprocessCliTransport.buildCommand(...)`, which is the only place that maps option fields to CLI flags.

### Session store

`SessionStore` (`types/SessionStore.java`) is an interface with default `UnsupportedOperationException` bodies — implementers override only what they support. `InMemorySessionStore` is the reference impl. `TranscriptMirrorBatcher` batches writes with retry/backoff. The subprocess still writes to local disk; the adapter receives a secondary mirror copy via the `--session-mirror` flag. `session/` package also contains `SessionListing`, `SessionMutations` (rename/tag/delete/fork), `SessionImport`, `SessionResume`, `SessionSummary`, `Sessions` (project-key derivation).

## Conventions

(From `CONTRIBUTING.md` — non-obvious project rules.)

- **Java 21 LTS only**, no preview features. Uses virtual threads (`Thread.ofVirtual()`) for I/O, record patterns, pattern-matching switch.
- **Sealed hierarchies** for union types (`Message`, `ContentBlock`, `HookInput`, `HookSpecificOutput`, `PermissionResult`, `ControlRequest`, `ClaudeAgentOptions.SystemPrompt`, `McpServerConfig`, `ThinkingConfig`, `SystemMessage`, `ResultMessage`).
- **Records** for value types. **Builders** for classes with >5 fields.
- **Jackson** for JSON; wire-format field names via `@JsonProperty("snake_case")`; `@JsonInclude(NON_NULL)` on every record that crosses the wire.
- **AssertJ** for assertions, never JUnit's `assertEquals`. Mockito for mocks.
- **try-with-resources** on every `AutoCloseable` (Transport, Client, QuerySession, InternalQuery).
- **Public-facing changes require a new entry in `CHANGELOG.md`** under the unreleased version.

## Common pitfalls

- `InternalQuery` is internal — never expose its types in `ClaudeAgentSdk`/`ClaudeSdkClient` public signatures.
- `SubprocessCliTransport` requires the `claude` binary. Resolution: `ClaudeAgentOptions.cliPath` → well-known paths (`~/.local/bin/claude`, `/usr/local/bin/claude`, …) → `$PATH`. If not found, throws `CliNotFoundError` with install instructions. To bundle the binary: run `./scripts/download-cli.sh` (downloads to `claude-agent-sdk-core/src/main/resources-binary/<classifier>/`, which is gitignored), then build with `-P bundled-cli`.
- `mvn -B -ntp clean verify` is the CI gate. PRs must pass it on Ubuntu + macOS + Windows (Java 21).
- Versioning follows semver: patch (bugfix/no API change), minor (backward-compatible features), major (breaking). Git tags are `vX.Y.Z`.
- `examples/quickstart/` should mirror the Python SDK's examples directory — when adding a new user-facing feature, add a corresponding example.
