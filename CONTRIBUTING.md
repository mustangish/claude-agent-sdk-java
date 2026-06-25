# Contributing

## Development setup

```bash
# Install Maven 3.9+
brew install maven  # or download from https://maven.apache.org/download.cgi

# Verify
mvn -version
java -version  # must be 21+
```

## Build & test

```bash
# Compile + run all tests
mvn -B -ntp clean test

# Install to local repo
mvn -B -ntp clean install

# Build with bundled CLI binary (downloads to local cache)
./scripts/download-cli.sh
mvn -B -ntp clean install -P bundled-cli
```

## Code layout

```
claude-agent-sdk-java/
├── claude-agent-sdk-core/           # Main SDK
│   ├── src/main/java/com/anthropic/claude/sdk/
│   │   ├── ClaudeAgentSdk.java      # Public facade
│   │   ├── client/                  # ClaudeSdkClient
│   │   ├── query/                   # QuerySession
│   │   ├── transport/               # Transport + SubprocessCliTransport
│   │   ├── internal/                # InternalQuery, MessageParser, control/
│   │   ├── mcp/                     # In-process SDK MCP servers
│   │   ├── session/                 # SessionStore + InMemorySessionStore + mutations
│   │   ├── types/                   # Sealed hierarchies + records + enums
│   │   └── errors/                  # Exception types
│   └── src/test/java/...            # Tests (fake CLI shell scripts in resources/)
├── claude-agent-sdk-testing/        # SessionStore conformance harness
├── examples/quickstart/             # Runnable examples
└── scripts/                         # download-cli.sh etc.
```

## Conventions

- **Java 21 LTS only**. No preview features.
- **Sealed hierarchies** for union types (e.g. `Message`, `ContentBlock`).
- **Records** for value types (immutable, structural equality, `toString`).
- **Builders** for classes with >5 fields.
- **Jackson** for JSON serialization — wire-format field names via `@JsonProperty("snake_case")`.
- **`@JsonInclude(NON_NULL)`** on every record that crosses the wire.
- **Virtual threads** (`Thread.ofVirtual()`) for I/O, never platform threads.
- **`try-with-resources`** on every `AutoCloseable` (Transport, Client, QuerySession).
- **AssertJ** fluent assertions, never JUnit's `assertEquals`.

## Adding a new control request

1. Add a request record to `internal/control/ControlProtocol.java` (sealed if polymorphic).
2. Add a public method to `InternalQuery.java` that sends the control frame and awaits the response.
3. Add a thin wrapper to `ClaudeSdkClient.java` that calls the InternalQuery method.
4. Write at least one test in `core/src/test/java/.../internal/control/`.
5. Add an example in `examples/quickstart/` if it's user-facing.

## Adding a new Message type

1. Add a record to `types/Message.java` (or as a nested record of `SystemMessage`).
2. Add the `type` discriminator to `Message.java`'s `@JsonSubTypes`.
3. Add parsing to `internal/message/MessageParser.java`.
4. Round-trip test in `types/MessageRoundTripTest.java`.

## Pull requests

- All commits must have tests (or document why not).
- `mvn -B -ntp clean verify` must pass before merge.
- At least one reviewer approval required.
- Squash commits on merge.

## Versioning policy

- **Patch** (0.1.x): bug fixes, doc improvements, no API change.
- **Minor** (0.x.0): new features, backward-compatible.
- **Major** (x.0.0): breaking API changes.

## License

By contributing, you agree that your contributions will be licensed under the MIT License.
