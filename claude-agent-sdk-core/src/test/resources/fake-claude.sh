#!/usr/bin/env bash
# Fake Claude Code CLI that handles the control protocol minimally.
#
# -v                                          -> print "2.1.191"
# (default)                                   ->
#   1. Read first line; if it's a control_request (initialize), respond with control_response.
#   2. Wait for user message on stdin.
#   3. Emit canned assistant + result messages.

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

# Helper: read a JSON-line and try to extract request_id if it's a control_request.
read_request_id() {
    local line="$1"
    if command -v python3 >/dev/null 2>&1; then
        printf '%s' "$line" | python3 -c 'import sys, json
try:
    d = json.loads(sys.stdin.read())
    if d.get("type") == "control_request":
        print(d.get("request_id", ""))
except Exception:
    pass' 2>/dev/null
    fi
}

# Read first line (could be initialize control_request, or user message, or anything)
INIT_LINE=""
if IFS= read -r -t 30 INIT_LINE; then
    REQ_ID=$(read_request_id "$INIT_LINE")
    if [ -n "$REQ_ID" ]; then
        # Respond with a success control_response (initialize handshake)
        printf '{"type":"control_response","response":{"subtype":"success","request_id":"%s","response":{}}}\n' "$REQ_ID"
    fi
fi

# Wait for a user message (or EOF)
USER_LINE=""
if IFS= read -r -t 30 USER_LINE; then
    :  # got user message
fi

# Drain remaining stdin in background so we can exit cleanly
cat > /dev/null &

# Emit canned response
echo '{"type":"assistant","model":"claude-sonnet-4-5","content":[{"type":"text","text":"Hello from fake CLI!"}]}'
echo '{"type":"result","subtype":"success","duration_ms":100,"duration_api_ms":80,"is_error":false,"num_turns":1,"session_id":"test-session","stop_reason":"end_turn","total_cost_usd":0.0001}'

wait
exit 0
