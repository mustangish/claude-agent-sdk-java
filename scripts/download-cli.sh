#!/usr/bin/env bash
# Download the Claude Code CLI binary for the current platform.
#
# Usage:
#   ./scripts/download-cli.sh           # download for current platform
#   ./scripts/download-cli.sh linux     # linux x86_64
#   ./scripts/download-cli.sh macos-arm # macOS Apple Silicon
#
# Output goes to: src/main/resources-binary/<classifier>/claude[.exe]

set -euo pipefail

PLATFORM="${1:-$(uname | tr '[:upper:]' '[:lower:]')}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"

case "$PLATFORM" in
    linux)
        CLASSIFIER="linux-x86_64"
        ;;
    linux-arm|linux-aarch64)
        CLASSIFIER="linux-aarch64"
        ;;
    macos-arm|darwin-arm|osx-aarch64)
        CLASSIFIER="osx-aarch64"
        ;;
    macos|darwin|osx)
        CLASSIFIER="osx-x86_64"
        ;;
    windows|windows-x86_64|win)
        CLASSIFIER="windows-x86_64"
        ;;
    *)
        echo "Unknown platform: $PLATFORM" >&2
        echo "Valid: linux, linux-arm, macos, macos-arm, windows" >&2
        exit 1
        ;;
esac

DEST_DIR="$ROOT_DIR/core/src/main/resources-binary/$CLASSIFIER"
DEST_FILE="$DEST_DIR/claude"
if [ "$CLASSIFIER" = "windows-x86_64" ]; then
    DEST_FILE="$DEST_DIR/claude.exe"
fi

mkdir -p "$DEST_DIR"

# Download via the official installer
echo "Downloading Claude Code CLI for $CLASSIFIER..."
if [ "$PLATFORM" = "windows" ] || [ "$PLATFORM" = "win" ]; then
    TMP_INSTALL=$(mktemp -d)
    curl -fsSL https://claude.ai/install.ps1 -o "$TMP_INSTALL/install.ps1"
    echo "Windows installer downloaded. Run manually to obtain claude.exe and copy to: $DEST_FILE"
    exit 0
fi

# Linux/macOS: install to a temp directory, then move the binary
TMP_INSTALL=$(mktemp -d)
curl -fsSL https://claude.ai/install.sh | CLAUDE_INSTALL_DIR="$TMP_INSTALL" bash >/dev/null

if [ -f "$TMP_INSTALL/claude" ]; then
    mv "$TMP_INSTALL/claude" "$DEST_FILE"
    chmod +x "$DEST_FILE"
    echo "Downloaded to: $DEST_FILE"
    "$DEST_FILE" -v
else
    echo "ERROR: claude binary not found in install output" >&2
    exit 1
fi
