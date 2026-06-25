package com.anthropic.claude.sdk;

/**
 * Bundled Claude Code CLI version.
 *
 * <p>Tracked independently from the SDK version. A new SDK patch is released every
 * time this bumps (mirrors the Python SDK's {@code _cli_version.py}).
 */
public final class CliVersion {
    public static final String VERSION = "2.1.191";

    private CliVersion() {}
}
