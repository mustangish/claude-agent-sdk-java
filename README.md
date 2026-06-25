# Claude Agent SDK for Java

[![Java 21](https://img.shields.io/badge/Java-21-blue)](https://openjdk.org/projects/jdk/21/)
[![Maven](https://img.shields.io/badge/Maven-3.9%2B-blue)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-green)](LICENSE)

Java port of the [Claude Agent SDK](../claude-agent-sdk-python). Drive [Claude Code CLI](https://docs.anthropic.com/en/docs/claude-code)
programmatically — spawn it as a subprocess, exchange line-delimited JSON over stdin/stdout, and add
typed tool/hook/MCP handlers in pure Java.

## Requirements

- **Java 21 LTS** (uses virtual threads, record patterns, pattern-matching switch)
- Maven 3.9+

## Installation

```xml
<dependency>
    <groupId>com.anthropic</groupId>
    <artifactId>claude-agent-sdk-core</artifactId>
    <version>0.1.0</version>
</dependency>
```

To bundle the Claude CLI binary with your app, enable the `bundled-cli` profile:

```bash
mvn -B -ntp install -P bundled-cli
```

## Quick start

### One-shot query

```java
import com.anthropic.claude.sdk.ClaudeAgentSdk;
import com.anthropic.claude.sdk.types.ClaudeAgentOptions;
import com.anthropic.claude.sdk.types.Message;
import com.anthropic.claude.sdk.query.QuerySession;

try (QuerySession session = ClaudeAgentSdk.query(
        "What is 2 + 2?",
        ClaudeAgentOptions.builder().build())) {
    for (Message m : session) {
        System.out.println(m);
    }
}
```

### Interactive client

```java
import com.anthropic.claude.sdk.client.ClaudeSdkClient;
import com.anthropic.claude.sdk.types.ClaudeAgentOptions;

try (ClaudeSdkClient client = new ClaudeSdkClient(
        ClaudeAgentOptions.builder().build())) {
    client.connect();
    client.query("Refactor main.java to use streams");

    Iterator<Message> iter = client.receiveResponse();
    while (iter.hasNext()) {
        Message m = iter.next();
        // ...
    }

    client.interrupt();           // stop the running turn
    client.setPermissionMode(PermissionMode.ACCEPT_EDITS);
    client.setModel("claude-opus-4-1");
}
```

### Hooks

```java
import com.anthropic.claude.sdk.types.HookMatcher;
import com.anthropic.claude.sdk.types.HookJSONOutput;
import com.anthropic.claude.sdk.types.HookEvent;
import com.fasterxml.jackson.databind.JsonNode;

HookMatcher matcher = new HookMatcher(
    "Bash|Edit",
    List.of((input, toolUseId, ctx) ->
        CompletableFuture.completedFuture(
            HookJSONOutput.Sync.block("Denied by hook"))));

ClaudeAgentOptions options = ClaudeAgentOptions.builder()
    .hooks(Map.of(HookEvent.PRE_TOOL_USE, List.of(matcher)))
    .build();
```

### Permission callback (`canUseTool`)

```java
import com.anthropic.claude.sdk.types.PermissionResult;
import com.anthropic.claude.sdk.types.CanUseTool;

CanUseTool handler = CanUseTool.sync(req -> {
    if (req.toolName().equals("Bash")) return PermissionResult.deny("bash disabled");
    return PermissionResult.allow();
});

ClaudeAgentOptions options = ClaudeAgentOptions.builder()
    .canUseTool(handler)
    .build();
```

### In-process MCP servers

```java
import com.anthropic.claude.sdk.mcp.SdkMcpServer;
import com.anthropic.claude.sdk.mcp.ToolResult;

SdkMcpServer calcServer = SdkMcpServer.builder("calc", "1.0.0")
    .tool("add", "Add two numbers", Map.class, args -> {
        Map<String, Object> m = (Map<String, Object>) args;
        double sum = ((Number) m.get("a")).doubleValue()
                   + ((Number) m.get("b")).doubleValue();
        return ToolResult.text(String.valueOf(sum));
    })
    .build();

ClaudeAgentOptions options = ClaudeAgentOptions.builder()
    .mcpServers(Map.of("calc", calcServer.toConfig()))
    .allowedTools(List.of("mcp__calc__add"))
    .build();
```

### Session store (mirror transcripts to external storage)

```java
import com.anthropic.claude.sdk.types.SessionStore;
import com.anthropic.claude.sdk.session.InMemorySessionStore;

SessionStore store = new InMemorySessionStore();  // or your custom S3/Redis/Postgres adapter

ClaudeAgentOptions options = ClaudeAgentOptions.builder()
    .sessionStore(store)
    .build();
```

## Architecture

| Module | Purpose |
|---|---|
| `core` | Subprocess transport, control protocol, message parsing, all types |
| `testing` | Conformance harness for third-party `SessionStore` implementations |
| `examples/quickstart` | Runnable examples |

### Wire protocol

The SDK spawns Claude Code CLI as a subprocess with `--input-format stream-json --output-format stream-json`. Communication happens via line-delimited JSON:

- **stdin → CLI**: user messages + control requests (initialize, can_use_tool, hook_callback, mcp_message, interrupt, set_permission_mode, set_model)
- **stdout → SDK**: assistant messages + control responses

Control frames are correlated by `request_id` via `PendingControlRequests`.

### Custom transports

Implement the `Transport` interface to plug in SSH-based remote CLIs or other backends:

```java
public class SshTransport implements Transport {
    public void connect() { /* ssh to remote, spawn claude */ }
    public void write(String data) { /* send over SSH */ }
    public Iterator<JsonNode> readMessages() { /* receive from SSH */ }
    public void close() { /* close SSH */ }
    // ...
}

try (QuerySession session = ClaudeAgentSdk.queryWith("Hi", new SshTransport("host"))) {
    for (Message m : session) { /* ... */ }
}
```

## Build

```bash
mvn -B -ntp clean install         # build + run tests + install to local repo
mvn -B -ntp clean package -P bundled-cli   # also bundle CLI binary
mvn -B -ntp clean deploy -P release        # publish to Maven Central (requires credentials)
```

## Testing

```bash
mvn -B -ntp test
```

Tests use fake `claude` shell scripts under `core/src/test/resources/` — no real CLI required.

## License

MIT — see [LICENSE](LICENSE).
