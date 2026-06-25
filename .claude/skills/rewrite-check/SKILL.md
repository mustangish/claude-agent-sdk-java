---
name: rewrite-check
description: Compare the Java port of the Claude Agent SDK against the Python reference. Use when an area in REWRITE_CHECK.md is `pending` or `diverged`, or when the Python SDK has been updated. Codifies the equivalence rules so the loop does not re-derive them every cycle.
---

# rewrite-check

The Java SDK is a port of the Python SDK. They must be **wire-compatible** and
**API-shape-compatible** — not byte-identical implementations.

## Equivalence rules

These are the rules the verifier applies. They are not up for debate per area;
if an area breaks one of these, it is `diverged` unless the divergence is
intentional and documented in `REWRITE_CHECK.md` under "Intentionally Java-only"
or "Divergence log".

### 1. Wire format

The bytes that flow over stdin/stdout to the CLI must match Python exactly.
Verify by:

- Both projects emit the same JSON shape for a given option
  (`ClaudeAgentOptions` → CLI flags → on-wire JSON when applicable).
- Both projects parse the same set of incoming message types with the same
  field names (snake_case on the wire, Java fields are camelCase +
  `@JsonProperty("snake_case")`).
- The CLI command line built by `SubprocessCliTransport.buildCommand(...)`
  produces a flag set semantically equivalent to the Python
  `_build_command` / `build_args`.

Acceptable differences: flag ordering, whitespace, escaping strategy.

### 2. Type system translation

Python → Java mapping:

| Python | Java |
|---|---|
| `dataclass` (immutable) | `record` |
| `dataclass` (mutable) | `final class` with builder |
| `TypedDict` (duck-typed) | sealed interface with `@JsonTypeInfo` |
| `ABC` | `interface` |
| `Protocol` (structural) | `interface` (nominal) |
| `frozenset` constant | `public static final Set<...>` |
| `Optional[X]` | `@JsonInclude(NON_NULL)` field |
| `Union[A, B]` (with type) | sealed interface permits |
| `Union[A, B]` (duck-typed) | `@JsonTypeInfo` w/ `JsonNode` field |
| `async def` | `CompletionStage<T>` or virtual-thread method |
| `asyncio.CancelledError` | `InterruptedException` |
| `enum.Enum` | `enum` (Java) |
| `None` / `null` | `null` |

### 3. Naming

- Python `snake_case` public attribute → Java `camelCase` accessor.
- Python `PascalCase` class name → Java `PascalCase` class name. **Never
  rename during port** — even if Java idiom prefers a different name. Example:
  Python `can_use_tool` callback → Java `CanUseTool` (capital), kept verbatim.
- Python `__all__` exports → Java types in `com.anthropic.claude.sdk.types`
  (or `mcp` / `client` / `query` / `errors`). The set of *exported* types
  should match. Nesting is fine (Java nests `TaskStartedMessage` under
  `SystemMessage`); what matters is reachability.

### 4. Behavior, not just shape

Two implementations are NOT equivalent just because their types align. Verify:

- Default values match (e.g. `max_buffer_size`, `cwd`, `permission_mode`).
- The lifecycle of `close()` / `disconnect()` matches (Python uses
  `async with` / `__aexit__`; Java uses `try-with-resources`).
- Error types raised for the same condition match (Python
  `CLINotFoundError` ↔ Java `CliNotFoundError`).
- The set of CLI flags emitted for a given `ClaudeAgentOptions` is
  semantically equivalent, even if the literal argv differs.

### 5. The Intentional-differences list

Some Java types have no Python counterpart. Those are fine, but they must be
listed under "Intentionally Java-only" in `REWRITE_CHECK.md`. If you find a
Java-only type that is *not* listed, it is either:

- A bug (the Java code is doing something Python does differently), or
- A missing entry in the doc (add it to "Intentionally Java-only").

## Loop protocol

1. **Read** `REWRITE_CHECK.md` to find the next `pending` or `diverged` area.
2. **Spawn** a `python-source-reader` sub-agent to summarize the Python
   contract for that area. The summary must include: public types, public
   methods, default values, error conditions raised, and the wire format if
   applicable.
3. **Spawn** a `java-source-reader` sub-agent to do the same for the Java
   side.
4. **Spawn** a `rewrite-checker` sub-agent (a different model) to adversarially
   compare. The checker must:
   - Default to `diverged` if uncertain.
   - Cite the Python file/line and the Java file/line for any divergence.
   - Not assume the Java implementation is correct just because it has tests.
5. **Update** `REWRITE_CHECK.md`: set the row's Status, append a
   Divergence-log entry if `diverged`. **Never** delete a row — only update
   its status.
6. **Exit.** Do not chain into fixing the divergence in the same run; the
   fix loop is a separate concern. The state file is durable.

## When to invoke this skill

- A row in `REWRITE_CHECK.md` is `pending` or `diverged`.
- The Python SDK at `../claude-agent-sdk-python` has changed (git log shows
  new commits, or `pyproject.toml` version bumped).
- A new module is added to the Java SDK (`git diff main` shows new files
  under `claude-agent-sdk-core/src/main/java/`).
- A user runs `/rewrite-check` or schedules `/loop 1d /rewrite-check`.

## Out of scope

- Implementing fixes. The fix loop is a separate skill
  (`rewrite-fix`, not yet written).
- Reviewing tests for *correctness* (the rewrite-check verifies that Java
  mirrors Python — not that either is right).
- Verifying examples mirror Python examples one-to-one (covered in the
  "Examples" row of the summary table).
