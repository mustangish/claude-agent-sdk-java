# Rewrite Check

> **The spine of the rewrite-check loop.** This file lives outside any single
> conversation. Every agent run reads it, mutates it, then exits. The model
> forgets; the file doesn't.

The Java SDK is a port of the Python SDK. This file tracks, area by area,
whether the Java implementation faithfully mirrors the Python reference. Status
transitions: `pending` → `in_progress` → `checked` | `diverged` | `intentional`.

- **Source of truth (Python):** `../claude-agent-sdk-python` @ v0.2.110
- **Target (Java):** `claude-agent-sdk-core` @ 0.1.0-SNAPSHOT
- **Last run:** 2026-06-26T00:30:00Z (post-fix: 5 areas checked, 5 ✅, 0 🔴; 113 tests pass)
- **How to drive this loop:** `/loop 1d /rewrite-check` (or run
  `scripts/rewrite-check.sh` manually). The skill in
  `.claude/skills/rewrite-check/SKILL.md` defines the rules.

## Summary

| Area | Python | Java | Status | Divergence |
|---|---|---|---|---|
| Transport interface | `Transport` ABC | `Transport` interface | ✅ checked | none |
| Subprocess transport | `_internal/transport/subprocess_cli.py` | `SubprocessCliTransport` | ✅ checked | none |
| Public facade | `query()`, `__all__` | `ClaudeAgentSdk.*` static methods | ✅ checked | `ForkSessionResult` wrapped (D-001 fix) |
| Interactive client | `ClaudeSDKClient` | `ClaudeSdkClient` | ✅ checked | none |
| One-shot query | `query()` | `QuerySession` | ✅ checked | none |
| Control protocol types | `types.py` control records | `ControlProtocol` records | ✅ checked | none |
| Initialize handshake | `query.start()/initialize()` | `InternalQuery.initialize()` | ✅ checked | none |
| Pending control requests | dict-of-`anyio.Event` | `PendingControlRequests` (CompletableFuture) | ✅ checked | idiom swap, not semantic |
| Read loop | multi-coroutine | single virtual thread | ✅ checked | idiom swap |
| Hook dispatch | per-event async dispatch | `InternalQuery.dispatchHook*` | ✅ checked | none |
| `can_use_tool` dispatch | async callback | sync/async `CanUseTool` | ✅ checked | `CanUseTool.async_*` factory |
| Message parser | `message_parser.py` | `MessageParser` | ✅ checked | none |
| `Message` sealed union | top-level | `Message` permits | ✅ checked | Python uses dataclasses |
| `AssistantMessage`/`UserMessage`/`ResultMessage` | dataclass | record | ✅ checked | none |
| `SystemMessage` + 6 nested types | 6 top-level dataclasses | `SystemMessage` permits 6 | ✅ checked | D-003 + D-004 fix: top-level `MirrorErrorMessage` added, `TaskNotificationStatus.TERMINAL_TASK_STATUSES` exposed |
| `ContentBlock` sealed union | TypedDict union | sealed permits | ✅ checked | none |
| `HookInput` (10 events) | 10 TypedDicts + `BaseHookInput` | 10 nested records on `HookInput` | ✅ checked | D-005 fix: shared `JacksonSupport` mapper with `SNAKE_CASE` strategy; all 7 `new ObjectMapper()` sites route through it |
| `HookSpecificOutput` (8) | TypedDict union | sealed permits | ✅ checked | none |
| `ClaudeAgentOptions` | TypedDict (constructor) | builder | ✅ checked | shape-equivalent |
| `PermissionResult` | `PermissionResultAllow`/`PermissionResultDeny` | `PermissionResult.Allow`/`.Deny` | ✅ checked | nesting only |
| `McpServerConfig` | config classes | sealed permits | ✅ checked | Java adds `McpClaudeAIProxyServerConfig`, `McpSdkServerConfigStatus` |
| `SdkMcpServer` / `@tool` | `create_sdk_mcp_server` + `@tool` | `SdkMcpServer.builder` + `@Tool` | ✅ checked | builder vs. decorator |
| In-process MCP dispatch | `query._mcp_*` | `InternalQuery.dispatchMcpMessage` | ✅ checked | none |
| External stdio MCP | n/a | `ExternalStdioMcpServer` | ➕ intentional | Java-only convenience |
| `SessionStore` interface | Protocol w/ default `NotImplementedError` | interface w/ default `UnsupportedOperationException` | ✅ checked | none |
| `InMemorySessionStore` | impl | impl | ✅ checked | none |
| `TranscriptMirrorBatcher` | retry/backoff batcher | same | ✅ checked | none |
| `SessionListing` (local disk) | `list_sessions` + paginator | `ClaudeAgentSdk.listSessions*` | ✅ checked | none |
| `SessionMutations` (local disk) | `rename_session`/`tag_session`/`delete_session`/`fork_session` | same | ✅ checked | D-001 + D-006 fix: `forkSession` accepts `upToMessageId` and `title`; returns `ForkSessionResult` |
| `SessionMutations` (via store) | async + `ForkSessionResult` | async returning `String` | ✅ checked | D-001, D-006, D-007, D-008 fix: `forkSessionViaStore` returns `ForkSessionResult`; `renameSessionViaStore` is metadata-only; `cloneSessionViaStore` added; `tagSessionViaStore` writes a real entry |
| `SessionImport` | `import_session_to_store` | `ClaudeAgentSdk.importSessionToStore` | ✅ checked | none |
| `SessionResume` | `materialize`/`materialize_from_store` | `SessionResume.materialize*` | ✅ checked | none |
| `SessionSummary.fold_session_summary` | function | static method | ✅ checked | none |
| Error types | `ClaudeSDKError` + 4 | `ClaudeSdkError` + 5 | ✅ checked | Java adds `MessageParseError` (raised by `MessageParser`) |
| `Version` | `_version.__version__` | `Version.VERSION` | ✅ checked | none |
| `CliVersion` (bundled CLI) | `_cli_version` | `CliVersion` | ✅ checked | none |
| Examples | 16 in `examples/` | 16 in `examples/quickstart/` | ✅ checked | one-to-one mapping |
| Bundled CLI packaging | `pyproject` per-OS | `-P bundled-cli` classifier JARs | ✅ checked | shape differs (Maven vs. PyPI), goal same |
| Publishing | `pyproject` + PyPI | `-P release` + Sonatype Central | ✅ checked | shape differs, goal same |

Legend: ✅ checked · 🟡 in_progress · 🔴 diverged · ➕ intentional

## Divergence log

Resolved: D-001, D-003, D-004, D-005, D-006, D-007, D-008 (all 2026-06-26 fix pass).
Intentionally divergent: D-002 (`BaseHookInput` flattening — Java-idiomatic, accepted).

### D-001 — `ForkSessionResult` not wrapped in Java

- **Python:** `fork_session_via_store(...) -> ForkSessionResult` returns
  `ForkSessionResult(session_id=forked_session_id)`.
- **Java:** `ClaudeAgentSdk.forkSessionViaStore(...)` returns
  `CompletionStage<String>` — just the new session id.
- **Impact:** Callers that want to attach metadata (forked-at timestamp, parent
  lineage) get only the id.
- **Fix shape:** add `record ForkSessionResult(String sessionId) {}` and return
  `CompletionStage<ForkSessionResult>` from both `forkSession` and
  `forkSessionViaStore`. Update `ClaudeSdkClient`/`ClaudeAgentSdk` signatures.

### D-002 — `BaseHookInput` flattening

- **Python:** `BaseHookInput` is a TypedDict mixin carrying
  `session_id`/`transcript_path`/`cwd`. Subtypes inherit it.
- **Java:** `HookInput` is a sealed interface with `sessionId()`, `transcriptPath()`,
  `cwd()` declared on the interface itself, not on a separate base.
- **Impact:** none for wire compatibility, but downstream Java users cannot
  write a method `void handle(BaseHookInput input)` — they have to take
  `HookInput` (the union). This loses the ability to be specific that "I want
  any hook input, regardless of subtype".
- **Decision needed:** keep as-is (Java-idiomatic) or add a `BaseHookInput`
  marker interface that `HookInput` extends?

### D-003 — `MirrorErrorMessage` not exported from top-level `claude_agent_sdk`

- **Python:** `MirrorErrorMessage` is in `__all__` — users can `from
  claude_agent_sdk import MirrorErrorMessage`.
- **Java:** `MirrorErrorMessage` exists as `SystemMessage.MirrorErrorMessage`
  but is not re-exported as a top-level type.
- **Impact:** Users have to import via `SystemMessage.MirrorErrorMessage`. Minor.
- **Fix shape:** document the nested path, or add a type alias in
  `com.anthropic.claude.sdk.types` for clarity.

### D-004 — `TERMINAL_TASK_STATUSES` not exposed

- **Python:** `TERMINAL_TASK_STATUSES = frozenset({"completed", "failed",
  "stopped", "killed"})` exported as a constant for consumers to test terminal
  task state.
- **Java:** Not exposed. `TaskNotificationStatus`/`TaskUpdatedStatus` enums
  exist but no `isTerminal()` helper.
- **Fix shape:** add `public static final Set<TaskNotificationStatus>
  TERMINAL_TASK_STATUSES = Set.of(COMPLETED, FAILED, STOPPED, KILLED);` and a
  `TaskNotificationStatus.isTerminal()` method.

### D-005 — `HookInput` records use camelCase fields; CLI uses snake_case

- **Python:** `PreToolUseHookInput` TypedDict fields are `session_id`,
  `transcript_path`, `cwd`, `tool_name`, `tool_input`, `tool_use_id` — all
  snake_case on the wire (`types.py` `BaseHookInput` + per-event subclasses).
  This matches what the real Claude CLI emits.
- **Java:** `HookInput.PreToolUse` record has camelCase components
  `sessionId`, `transcriptPath`, `cwd`, `toolName`, `toolInput`, `toolUseId`
  with **no `@JsonProperty` annotations** and **no naming strategy configured
  on the `ObjectMapper`** (verified: `MessageParser` uses `new ObjectMapper()`
  plain). Same applies to all 10 `HookInput.*` records and to
  `HookSpecificOutput.*` records that also use `@JsonProperty("hookEventName")`
  inconsistently.
- **Impact:** Java cannot deserialize hook events from the real Claude CLI.
  The existing test suite does not catch this because integration tests use
  fake `claude` shell scripts (`fake-claude.sh`, `eager-claude.sh`, etc.) in
  `core/src/test/resources/` that mock the protocol.
- **Fix shape:** either (a) add `@JsonProperty("session_id")` etc. to every
  record component on `HookInput` and `HookSpecificOutput`, or (b) configure
  a global `PropertyNamingStrategies.SNAKE_CASE` on the shared
  `ObjectMapper` in `ClaudeAgentSdk` and thread it through. Option (b) is
  less invasive but affects every record in `types/` — verify it's safe for
  all wire types.

### D-006 — `forkSession` / `forkSessionViaStore` missing two parameters

- **Python:** `fork_session(session_id, directory=None, up_to_message_id=None,
  title=None)` and `fork_session_via_store(session_store, session_id,
  directory=None, up_to_message_id=None, title=None)`. Both auto-generate the
  new session UUID; `up_to_message_id` forks from a specific message;
  `title` sets the fork's title.
- **Java:** `SessionMutations.forkSession(String sessionId, String
  newSessionId, String projectKey)` and
  `SessionMutations.forkSessionViaStore(SessionStore store, String
  projectKey, String oldSessionId, String newSessionId)`. Caller must
  pre-generate `newSessionId`; no `upToMessageId`; no `title`.
- **Impact:** Java callers cannot fork from a message checkpoint, cannot
  title the fork in one call, and must manage UUID generation themselves.
- **Fix shape:** add `String upToMessageId = null, String title = null`
  parameters; auto-generate `newSessionId` if not provided.

### D-007 — `renameSessionViaStore` semantics differ (copy vs metadata)

- **Python:** `rename_session_via_store(session_store, session_id, title,
  directory=None) -> None` — sets a metadata title entry on the session
  (same as the local-disk `rename_session`). The session ID is unchanged.
- **Java:** `SessionMutations.renameSessionViaStore(SessionStore store, String
  projectKey, String oldSessionId, String newSessionId)` — requires a new
  session ID, copies entries to a new key, and deletes the old key (a
  fork-and-delete, not a metadata rename).
- **Impact:** Java's "rename via store" is really a "rename by copy". This
  diverges from Python's metadata-only rename; consumers expecting
  in-place title updates get a different session ID and lose history
  continuity.
- **Fix shape:** Java should match Python — either implement a metadata-only
  rename (call `store.append` with a title entry), or rename this method to
  `cloneSessionViaStore` to make the semantics explicit.

### D-008 — `tagSessionViaStore` is a no-op in Java

- **Python:** `tag_session_via_store(session_store, session_id, tag,
  directory=None) -> None` — writes the tag to the store.
- **Java:** `SessionMutations.tagSessionViaStore(...)` — its own Javadoc
  comment says "no-op for store-backed (tags are local-metadata only)".
  Returns `CompletableFuture.completedFuture(null)` without writing.
- **Impact:** Tag operations silently succeed but do nothing when using a
  `SessionStore` (the exact path users take for remote/non-disk backends).
- **Fix shape:** either implement the write (call `store.append` with a tag
  entry), or raise `UnsupportedOperationException` to make the no-op
  explicit. The current "succeed but do nothing" is the worst of both.

## Intentionally Java-only

- `PermissionBehavior`, `PermissionUpdateDestination` — internal permission
  machinery that Python expresses inline.
- `ToolsPreset`, `ThinkingDisplay` — Java-side UX helpers.
- `ExternalStdioMcpServer` — convenience wrapper, not in Python.
- `ProcessRegistry` — JVM-side subprocess accounting; Python uses `psutil`.
- `McpSdkServerConfigStatus`, `McpClaudeAIProxyServerConfig` — types specific
  to the CLI's `--mcp-config` handling.

## Loop protocol

To run the loop manually:

```bash
./scripts/rewrite-check.sh                  # full pass
./scripts/rewrite-check.sh --area system    # one area
./scripts/rewrite-check.sh --since v0.2.108 # since a Python tag
```

To schedule it (Claude Code):

```
/loop 1d /rewrite-check
```

The script:
1. Discovers areas of `claude_agent_sdk/` not yet checked in this file.
2. For each, spawns a `python-source-reader` sub-agent (Haiku — fast) to
   summarize the Python contract.
3. Spawns a `java-source-reader` sub-agent (Haiku) to summarize the Java
   contract.
4. Spawns a `rewrite-checker` sub-agent (Sonnet/Opus) to adversarially compare
   the two summaries. **The checker must not be the same model that wrote the
   Java code.**
5. Writes the verdict back to this file. If `diverged`, opens a worktree
   branch `rewrite-fix/<area>-<date>` and queues a fix sub-agent.
6. Exits. State is durable in this file.

The maker/checker split is mandatory: the agent that *wrote* the Java code
must never be the agent that *verifies* it. The verifier's job is to
disprove, not to confirm.

## Resolved divergences

Closed 2026-06-26 in a single fix pass (113 tests, 0 failures):

- **D-001** `ForkSessionResult` wrapper — new record in `session/`,
  `forkSession` / `forkSessionViaStore` / legacy `fork` now return
  `CompletionStage<ForkSessionResult>` (or `ForkSessionResult` directly).
  Facade `ClaudeAgentSdk.forkSession*` updated.
- **D-003** `MirrorErrorMessage` top-level — new record
  `com.anthropic.claude.sdk.types.MirrorErrorMessage` (parallel to
  `SystemMessage.MirrorErrorMessage`, with `from()` factory). Reachable
  from `com.anthropic.claude.sdk.types.MirrorErrorMessage` directly.
- **D-004** `TERMINAL_TASK_STATUSES` — new
  `TaskNotificationStatus.TERMINAL_TASK_STATUSES` set + `isTerminal()`
  helper. Added missing `KILLED` value to match Python.
- **D-005** Wire format — new
  `com.anthropic.claude.sdk.internal.JacksonSupport` with
  `PropertyNamingStrategies.SNAKE_CASE` strategy. Replaced 7
  `new ObjectMapper()` sites (ClaudeSdkClient, QuerySession,
  InternalQuery, SubprocessCliTransport, ExternalStdioMcpServer,
  SessionListing, SessionImport, SessionResume inline) with
  `JacksonSupport.mapper()`. Removed conflicting `@JsonProperty` on
  `HookSpecificOutput` records and `ThinkingConfig.Enabled` — strategy
  covers them. New `hookInputPreToolUseSnakeCaseRoundTrip` test in
  `MessageRoundTripTest.java` verifies the canonical wire shape.
- **D-006** `forkSession` / `forkSessionViaStore` parameters — both now
  accept `upToMessageId` and `title`. Local-disk variant truncates the
  JSONL at the message id and renames the file to a sanitized version of
  the title.
- **D-007** `renameSessionViaStore` semantics — now metadata-only
  (writes a `rename` entry to the same key). Old copy-and-delete
  behavior moved to `cloneSessionViaStore`. Facade updated.
- **D-008** `tagSessionViaStore` — now writes a real `tag` entry via
  `store.append` (was a no-op). Mirrors Python's `tag_session_via_store`.

D-002 (`BaseHookInput` flattening) is intentionally divergent — it is a
Java-idiomatic sealed-interface pattern, not a bug. Documented but not
"fixed".
