# Releasing

Two release paths: **automatic** (triggered by a CLI version bump) and **manual** (via GitHub Actions UI).

Both flows call `build-and-publish.yml`, which builds platform-specific wheels on 5 OS targets,
publishes to PyPI… wait, this is Java. We use **Maven Central via Sonatype Central** instead.

## Wheel / JAR targets

| Runner | Platform tag |
|---|---|
| `ubuntu-latest` | `linux-x86_64` |
| `ubuntu-24.04-arm` | `linux-aarch64` |
| `macos-latest` | `osx-aarch64` |
| `macos-15-intel` | `osx-x86_64` |
| `windows-latest` | `windows-x86_64` |

For Java, the platform JAR classifier only matters for the bundled CLI binary artifact.
The main `claude-agent-sdk-core` JAR is platform-independent.

## Versioning

The project tracks two separate version numbers:

- **SDK version** — in `pyproject.toml` and `src/claude_agent_sdk/_version.py`
  (Java equivalent: `<version>` in `pom.xml` and `Version.java`)
- **Bundled CLI version** — in `src/claude_agent_sdk/_cli_version.py`
  (Java equivalent: `CliVersion.java`)

Both follow semver (`MAJOR.MINOR.PATCH`). Git tags use the format `vX.Y.Z`.

## Automatic Release (CLI Version Bump)

This is the most common release path. Every CLI version bump automatically produces a new SDK patch release.

**Flow:**

1. A commit with message `chore: bump bundled CLI version to X.Y.Z` is pushed to `main`,
   updating `CliVersion.java`.
2. The `Test` workflow runs on that push.
3. On successful completion, `auto-release.yml` fires via `workflow_run`.
4. It verifies the trigger commit message and that `CliVersion.java` changed.
5. It reads the current SDK version from `pom.xml` and increments the patch number.
6. It calls `release.yml`, which builds, publishes, pushes, tags, and creates a GitHub Release.

## Manual Release

Use this when you need to release with a specific version number (e.g. for minor/major bumps or non-CLI-bump changes).

**Via GitHub Actions UI:**

1. Go to Actions → Release to Maven Central → Run workflow
2. Enter the version (e.g. `0.1.0`)
3. Click "Run workflow"

## Publish to Maven Central locally

Requires:
- Sonatype OSSRH account + token
- GPG key configured locally

```bash
mvn -B -ntp clean deploy -P release
```

You'll be prompted for the GPG passphrase and Maven Central credentials.

## Required secrets (in GitHub repo settings)

| Secret | Purpose |
|---|---|
| `MAVEN_USERNAME` | Sonatype Central username |
| `MAVEN_PASSWORD` | Sonatype Central token |
| `MAVEN_GPG_PRIVATE_KEY` | GPG signing key (armored ASCII) |
| `MAVEN_GPG_PASSPHRASE` | GPG key passphrase |
