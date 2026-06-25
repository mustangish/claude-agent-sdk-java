# rewrite-check

The Java SDK is a port of the Python SDK. The `rewrite-check` loop verifies,
area by area, that the Java port faithfully mirrors the Python reference.

## Pieces (one file each)

| Piece | Path | Role |
|---|---|---|
| State (memory) | `REWRITE_CHECK.md` | Summary table + divergence log. Durable across runs. |
| Skill | `.claude/skills/rewrite-check/SKILL.md` | Codifies the equivalence rules. |
| Reader (Python) | `.claude/agents/python-source-reader.md` | Haiku — describes Python's contract. |
| Reader (Java) | `.claude/agents/java-source-reader.md` | Haiku — describes Java's contract. |
| Checker (adversarial) | `.claude/agents/rewrite-checker.md` | Sonnet — disproves equivalence. |
| Automation | `scripts/rewrite-check.sh` | Heartbeat: discovers areas, spawns agents, writes back. |
| Schedule | `.github/workflows/rewrite-check.yml` | Weekly + on-change CI run. |

## Drive it

```bash
# Full pass
./scripts/rewrite-check.sh

# One area
./scripts/rewrite-check.sh --area "HookInput"

# Re-check diverged rows after a Python SDK release
./scripts/rewrite-check.sh --since v0.2.110

# See what would be checked, without spawning agents
./scripts/rewrite-check.sh --dry-run
```

In Claude Code:

```
/loop 1d /rewrite-check
```

`/loop` re-runs on a cadence. The script never chains into fixing — it only
writes verdicts to `REWRITE_CHECK.md`. The fix loop is a separate concern
(the diverged rows in the table tell a future agent exactly what to do).

## Maker vs. checker

The two reader agents run on **Haiku** (cheap, fast, read-only). The checker
runs on **Sonnet** (more expensive, adversarial, paid to disprove). This is
deliberate: the model that described the code is never the model that grades
it. The verifier is incentivized to find divergence, not to confirm
parity — the same structural pattern as `/goal`, where a separate small
model judges whether the loop is done.

## What diverges today

See `REWRITE_CHECK.md` "Divergence log" for the current list. The four
documented divergences as of 2026-06-25 are:

- **D-001** `ForkSessionResult` not wrapped in Java
- **D-002** `BaseHookInput` flattening (Java-idiomatic but not 1:1)
- **D-003** `MirrorErrorMessage` not exported from top-level
- **D-004** `TERMINAL_TASK_STATUSES` constant not exposed

Fixes for these should each open a worktree branch (so the fixer does not
stomp on the main loop's state file) and update the row to `checked` once
merged.
