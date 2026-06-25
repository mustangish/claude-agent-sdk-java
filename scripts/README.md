# Scripts

## `download-cli.sh`

Downloads the Claude Code CLI binary for the current platform (or a specified one) and
places it under `core/src/main/resources-binary/<classifier>/` so it gets bundled into
the appropriate classifier JAR at build time.

Usage:

```bash
./scripts/download-cli.sh                  # auto-detect current platform
./scripts/download-cli.sh linux            # Linux x86_64
./scripts/download-cli.sh macos-arm        # macOS Apple Silicon
./scripts/download-cli.sh windows          # Windows (prints instructions)
```

After downloading, rebuild the classifier JAR with:

```bash
mvn -B -ntp clean package -pl core -P bundled-cli
```

Platform → classifier mapping:

| Platform | Classifier |
|---|---|
| Linux x86_64 | `linux-x86_64` |
| Linux ARM64 | `linux-aarch64` |
| macOS x86_64 | `osx-x86_64` |
| macOS ARM64 | `osx-aarch64` |
| Windows x86_64 | `windows-x86_64` |
