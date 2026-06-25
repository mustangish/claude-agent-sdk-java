# Claude Agent SDK for Java

Java port of the [Claude Agent SDK](../claude-agent-sdk-python). Lets Java applications programmatically drive the [Claude Code CLI](https://docs.anthropic.com/en/docs/claude-code) as a subprocess, with typed messages, hooks, MCP servers, sessions, and resumable transcripts.

> **Status: WIP** — under active development. See `/Users/tanzi/.claude/plans/steady-hatching-thimble.md` for the full implementation plan.

## Requirements

- **Java 21 LTS** (uses virtual threads, record patterns, pattern-matching switch)
- Maven 3.9+

## Modules

| Module | Artifact | Purpose |
|---|---|---|
| `core` | `com.anthropic:claude-agent-sdk-core` | Subprocess transport, control protocol, message parsing, types |
| `mcp` | `com.anthropic:claude-agent-sdk-mcp` | In-process SDK MCP servers |
| `testing` | `com.anthropic:claude-agent-sdk-testing` | Conformance harness for `SessionStore` implementations |
| `examples/quickstart` | (not published) | Runnable examples |

## Quick start (placeholder — Phase 4)

```java
// coming in Phase 4
```

## Build

```bash
mvn -B clean install
```

## Run tests

```bash
mvn -B test
```

## License

MIT — see [LICENSE](LICENSE).
