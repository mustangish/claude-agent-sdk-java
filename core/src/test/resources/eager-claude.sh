#!/usr/bin/env bash
# Fake Claude Code CLI that emits canned messages immediately (no stdin wait).
# Used by SubprocessCliTransport tests that don't write to stdin.

set -euo pipefail

for arg in "$@"; do
    case "$arg" in
        -v|--version)
            echo "2.1.191"
            exit 0
            ;;
        --output-format|--system-prompt|--append-system-prompt|--system-prompt-file|\
        --tools|--allowedTools|--disallowedTools|--max-turns|--max-budget-usd|\
        --task-budget|--model|--fallback-model|--betas|--permission-prompt-tool|\
        --permission-mode|--resume|--session-id|--settings|--add-dir|--mcp-config|\
        --json-schema|--thinking|--max-thinking-tokens|--thinking-display|\
        --effort|--plugin-dir|--setting-sources)
            shift || true
            ;;
    esac
done

# Drain stdin in the background and immediately emit canned messages.
cat > /dev/null &

echo '{"type":"assistant","model":"claude-sonnet-4-5","content":[{"type":"text","text":"Hello from fake CLI!"}]}'
echo '{"type":"result","subtype":"success","duration_ms":100,"duration_api_ms":80,"is_error":false,"num_turns":1,"session_id":"test-session","stop_reason":"end_turn","total_cost_usd":0.0001}'

wait
exit 0
