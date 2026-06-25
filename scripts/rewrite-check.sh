#!/usr/bin/env bash
# rewrite-check.sh — heartbeat of the rewrite-check loop.
#
# Discovers pending/diverged areas in REWRITE_CHECK.md, spawns the
# python-source-reader / java-source-reader / rewrite-checker sub-agents in
# sequence, writes the verdict back, and exits. State persists in
# REWRITE_CHECK.md; this script does not chain into fixing.
#
# Usage:
#   ./scripts/rewrite-check.sh                  # full pass
#   ./scripts/rewrite-check.sh --area system    # one area (substring match)
#   ./scripts/rewrite-check.sh --since v0.2.108 # areas with diverged status
#                                                # introduced since a Python tag
#   ./scripts/rewrite-check.sh --dry-run        # print areas, do nothing
#
# Required:
#   - claude CLI on PATH (the script drives sub-agents via `claude -p`)
#   - ../claude-agent-sdk-python exists as a sibling checkout
#
# Optional env:
#   READER_MODEL=haiku-sonnet-4-5      # model for the reader sub-agents
#   CHECKER_MODEL=sonnet              # model for the checker sub-agent
#   PYTHON_SDK_DIR=../claude-agent-sdk-python
#   JAVA_SDK_DIR=.
#   STATE_FILE=REWRITE_CHECK.md

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"

PYTHON_SDK_DIR="${PYTHON_SDK_DIR:-$ROOT_DIR/../claude-agent-sdk-python}"
JAVA_SDK_DIR="${JAVA_SDK_DIR:-$ROOT_DIR}"
STATE_FILE="${STATE_FILE:-$ROOT_DIR/REWRITE_CHECK.md}"
SKILL_DIR="$ROOT_DIR/.claude/skills/rewrite-check"
AGENTS_DIR="$ROOT_DIR/.claude/agents"

READER_MODEL="${READER_MODEL:-haiku}"
CHECKER_MODEL="${CHECKER_MODEL:-sonnet}"

AREA_FILTER=""
SINCE_TAG=""
DRY_RUN=0

usage() {
    grep -E '^# ' "$0" | sed 's/^# //'
    echo
    grep -E '^#   ' "$0" | sed 's/^#   //'
    exit "${1:-0}"
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --area) AREA_FILTER="$2"; shift 2 ;;
        --since) SINCE_TAG="$2"; shift 2 ;;
        --dry-run) DRY_RUN=1; shift ;;
        -h|--help) usage 0 ;;
        *) echo "Unknown flag: $1" >&2; usage 1 ;;
    esac
done

# Preconditions
[[ -d "$PYTHON_SDK_DIR" ]] || { echo "Python SDK not found: $PYTHON_SDK_DIR" >&2; exit 1; }
[[ -d "$JAVA_SDK_DIR/claude-agent-sdk-core" ]] || { echo "Java SDK not found: $JAVA_SDK_DIR" >&2; exit 1; }
[[ -f "$STATE_FILE" ]] || { echo "State file not found: $STATE_FILE" >&2; exit 1; }
command -v claude >/dev/null || { echo "claude CLI not on PATH" >&2; exit 1; }

# Discover areas needing a check. A row in the summary table is "needs work"
# if its Status is anything other than "checked" or "intentional".
discover_areas() {
    awk -F'|' '
        /^\| / && !/^\| ---/ && !/^\| Area/ {
            status = $5
            area = $2
            gsub(/^ +| +$/, "", status)
            gsub(/^ +| +$/, "", area)
            # Skip the legend row
            if (status ~ /(checked|intentional|---|Area)/) next
            print area "|" status
        }
    ' "$STATE_FILE"
}

areas=()
while IFS='|' read -r area status; do
    [[ -z "$area" ]] && continue
    if [[ -n "$AREA_FILTER" ]] && [[ "$area" != *"$AREA_FILTER"* ]]; then
        continue
    fi
    if [[ -n "$SINCE_TAG" ]]; then
        # Areas "diverged since <tag>" means the user is re-checking
        # after a Python bump. Run on all diverged rows regardless of
        # when they were introduced; the user knows what they want.
        [[ "$status" != "diverged" ]] && continue
    fi
    areas+=("$area")
done < <(discover_areas)

if [[ ${#areas[@]} -eq 0 ]]; then
    echo "No areas to check. Summary table is up to date."
    exit 0
fi

echo "Areas to check: ${#areas[@]}"
printf '  - %s\n' "${areas[@]}"
echo

if [[ "$DRY_RUN" -eq 1 ]]; then
    exit 0
fi

# Spawn a sub-agent and capture its final text.
spawn_agent() {
    local agent="$1" prompt="$2" model="$3"
    local agent_file="$AGENTS_DIR/${agent}.md"
    [[ -f "$agent_file" ]] || { echo "Agent not found: $agent_file" >&2; return 1; }

    # Build the prompt: agent file content + task
    local system
    system="$(cat "$agent_file")"
    # Strip the YAML front-matter — claude -p uses --append-system-prompt
    system="$(awk 'BEGIN{p=0} /^---$/{p=!p; next} !p{print}' <<< "$system")"

    # Invoke claude. The agent has a hard job; let it use Grep/Read/Glob.
    claude -p \
        --model "$model" \
        --append-system-prompt "$system" \
        --allowedTools "Read,Grep,Glob,Bash" \
        --cwd "$ROOT_DIR" \
        "$prompt"
}

update_state() {
    local area="$1" verdict="$2" reasoning="$3"

    # Replace the Status cell on the row whose Area cell contains $area.
    # Status must be one of: checked, in_progress, diverged, intentional.
    local new_status
    case "$verdict" in
        checked|intentional) new_status="$verdict" ;;
        diverged) new_status="diverged" ;;
        *) echo "Unknown verdict: $verdict" >&2; return 1 ;;
    esac

    # Use awk to swap the status cell in place.
    awk -v area="$area" -v new_status="$new_status" -F'|' '
        $0 ~ "^\\| .*"area && $0 !~ "^\\| ---" && $0 !~ "^\\| Area" {
            $5 = " " new_status " "
            # If the divergence log has a new entry, leave the table alone;
            # the caller is responsible for appending to the log.
        }
        { print }
    ' "$STATE_FILE" > "$STATE_FILE.tmp" && mv "$STATE_FILE.tmp" "$STATE_FILE"

    # Append a "Last run" timestamp.
    local ts
    ts="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
    sed -i '' "s/^- \*\*Last run:\*\* .*/- **Last run:** $ts/" "$STATE_FILE"

    echo "[$area] $verdict — $reasoning"
}

for area in "${areas[@]}"; do
    echo "=== Checking: $area ==="

    py_summary=$(spawn_agent "python-source-reader" \
        "Read the Python SDK at $PYTHON_SDK_DIR and produce a contract summary for: $area" \
        "$READER_MODEL") || { echo "  python-source-reader failed for $area" >&2; continue; }

    java_summary=$(spawn_agent "java-source-reader" \
        "Read the Java SDK at $JAVA_SDK_DIR/claude-agent-sdk-core and produce a contract summary for: $area" \
        "$READER_MODEL") || { echo "  java-source-reader failed for $area" >&2; continue; }

    verdict_block=$(spawn_agent "rewrite-checker" \
        "Compare these two contract summaries and produce a verdict.

Area: $area

=== Python ===
$py_summary

=== Java ===
$java_summary

Apply the rules in $SKILL_DIR/SKILL.md. Default to diverged." \
        "$CHECKER_MODEL") || { echo "  rewrite-checker failed for $area" >&2; continue; }

    # Parse the verdict block. Look for '## Verdict: <v>'.
    verdict=$(grep -E '^## Verdict:' <<< "$verdict_block" | awk '{print $3}' | head -1)
    reasoning=$(grep -E '^### Reasoning' -A2 <<< "$verdict_block" | tail -1)

    case "$verdict" in
        checked|diverged|intentional) ;;
        *) echo "  Unparseable verdict for $area: $verdict" >&2; continue ;;
    esac

    update_state "$area" "$verdict" "$reasoning"
    echo
done

echo "Done. See $STATE_FILE for updated status."
