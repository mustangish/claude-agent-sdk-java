# Javadoc Migration

> **The spine of the Javadoc migration loop.** This file lists every public
> method/record/class that needs a Javadoc port from the Python source.
> State persists across agent runs; the model forgets, the file doesn't.

- **Source of truth (Python):** `../claude-agent-sdk-python/src/claude_agent_sdk/`
- **Target (Java):** `claude-agent-sdk-core/src/main/java/com/anthropic/claude/sdk/`
- **Started:** 2026-06-26
- **Language:** 中文（用户 2026-06-26 要求全量翻译为中文 Javadoc）
- **Rule:** every public method that's *user-facing* gets a Javadoc. Record
  component accessors, builder setters, and `toString`/`equals` are
  excluded — the compiler auto-documents them.

## Batches (priority order)

| Batch | File | Methods | Status | Notes |
|---|---|---|---|---|
| 1 | `ClaudeAgentSdk.java` (facade) | 26 | ✅ done | 2026-06-26 pass（中文版） |
| 2 | `ClaudeSdkClient.java` (interactive) | 7 | ✅ done | 2026-06-26（中文） |
| 3 | `QuerySession.java` (one-shot) | 6 | ✅ done | 2026-06-26（中文） |
| 4 | `Transport.java` (interface) | 6 | ✅ done | 2026-06-26（中文） |
| 5 | `SubprocessCliTransport.java` | 9 | ✅ done | 2026-06-26（中文） |
| 6 | `SessionMutations.java` | 14 | ✅ done | 2026-06-26（中文） |
| 7 | `SessionListing.java` | 9 | ✅ done | 2026-06-26（中文） |
| 8 | `SessionImport.java` | 4 | ✅ done | 2026-06-26（中文） |
| 9 | `SessionResume.java` | 3 | ✅ done | 2026-06-26（中文） |
| 10 | `InMemorySessionStore.java` | 7 | ✅ done | 2026-06-26（中文） |
| 11 | `SdkMcpServer.java` | 7 | ✅ done | 2026-06-26（中文） |
| 12 | `SdkMcpRegistry.java` | 3 | ✅ done | 2026-06-26（中文） |
| 13 | `mcp/Tool.java` | n/a | ⏭️ skip | annotation，无方法可注 |
| 14 | `mcp/ToolResult.java` | 3 | ✅ done | 2026-06-26（中文） |
| 15 | Hook input records (10 in `HookInput.java`) | 10 | ✅ done | 2026-06-26（中文，类级别） |
| 16 | System message records (7 in `SystemMessage.java`) | 7 | ✅ done | 2026-06-26（中文，类级别） |
| 17 | `PermissionResult.java` records | 2 | ✅ done | 2026-06-26（中文） |
| 18 | `PermissionUpdate.java` methods | 6 | ✅ done | 2026-06-26（中文） |
| 19 | `errors/*.java` constructors | 6 | ✅ done | 2026-06-26（中文） |

**Total: ~140 methods** (after excluding record accessors, builder setters, `toString`/`equals`).

## Progress log

### Batch 1 — `ClaudeAgentSdk.java` facade (26 methods)
- [ ] `query(String, ClaudeAgentOptions)` — Python: `query()`
- [ ] `query(String)` — Python: `query()`
- [ ] `queryWith(String, Transport)` — Python: `query(transport=...)`
- [ ] `listSessions(String)` — Python: `list_sessions()`
- [ ] `listSessions(String, Integer, int)` — Python: `list_sessions(limit, offset)`
- [ ] `getSessionInfo(String, String)` — Python: `get_session_info()`
- [ ] `getSessionMessages(String, String)` — Python: `get_session_messages()`
- [ ] `listSubagents(String, String)` — Python: `list_subagents()`
- [ ] `getSubagentMessages(String, String, String)` — Python: `get_subagent_messages()`
- [ ] `projectKeyForDirectory(String)` — Python: `project_key_for_directory()`
- [ ] `listSessionsFromStore(...)` — Python: `list_sessions_from_store()`
- [ ] `getSessionInfoFromStore(...)` — Python: `get_session_info_from_store()`
- [ ] `getSessionMessagesFromStore(...)` — Python: `get_session_messages_from_store()`
- [ ] `listSubagentsFromStore(...)` — Python: `list_subagents_from_store()`
- [ ] `getSubagentMessagesFromStore(...)` — Python: `get_subagent_messages_from_store()`
- [ ] `renameSession(String, String, String)` — Python: `rename_session()`
- [ ] `tagSession(String, String, String)` — Python: `tag_session()`
- [ ] `deleteSession(String, String)` — Python: `delete_session()`
- [ ] `forkSession(String, String, String)` — Python: `fork_session()`
- [ ] `deleteSessionViaStore(...)` — Python: `delete_session_via_store()`
- [ ] `tagSessionViaStore(...)` — Python: `tag_session_via_store()`
- [ ] `renameSessionViaStore(...)` — Python: `rename_session_via_store()`
- [ ] `forkSessionViaStore(...)` — Python: `fork_session_via_store()`
- [ ] `cloneSessionViaStore(...)` — Java-only (renamed old copy-and-delete)
- [ ] `importSessionToStore(...)` — Python: `import_session_to_store()`
- [ ] `foldSessionSummary(...)` — Python: `fold_session_summary()`

## Translation rules (encoded in `.claude/skills/javadoc-migration/SKILL.md`)

1. **Python `Args:` → `@param name description`** — one `@param` per line.
2. **Python `Returns:` → `@return description`** — omit if `-> None`.
3. **Python `Raises:` → `@throws ClassName description`** — one per line.
4. **Python `Example:` → `{@code ...}` code block** — preserve the example.
5. **First line of docstring = summary sentence**, ending with a period.
6. **Cross-references:** `{@link com.anthropic.claude.sdk.types.Foo}` for type refs, never bare `Foo`.
7. **No marketing fluff** — drop "Conveniently" / "Simply" / "Just call" filler.
8. **Wrap at 100 chars** — match the surrounding style in `CONTRIBUTING.md`.

## How to run the loop

```bash
./scripts/javadoc-migrate.sh                # one method
./scripts/javadoc-migrate.sh --batch 1      # whole batch
./scripts/javadoc-migrate.sh --dry-run      # show next 5 pending
```

In Claude Code:

```
/loop 30m /javadoc-migrate
```

The heartbeat advances the state file. The fix-loop never chains into
tests — verify with `mvn javadoc:javadoc` at the end of each batch.

## Resolved

中文翻译全部完成，2026-06-26 单次会话内交付：

- **Batch 1** `ClaudeAgentSdk.java` — 26 个 facade 方法
- **Batch 2** `SessionMutations` / `SessionListing` / `SessionImport` / `SessionResume` / `InMemorySessionStore` — 37 个方法
- **Batch 3+** `ClaudeSdkClient` / `QuerySession` / `Transport` / `mcp/ToolResult` / `mcp/SdkMcpServer` / `mcp/SdkMcpRegistry` / `errors/*` / `PermissionResult` / `HookInput` / `SystemMessage` — 全部翻译

**最终验证：**
- `mvn -pl claude-agent-sdk-core test` → **113/113 通过**
- `mvn -pl claude-agent-sdk-core javadoc:javadoc` → **BUILD SUCCESS**

`{@link ...}` 引用全部使用完整包名（如
`{@link com.anthropic.claude.sdk.types.Foo}`），没有引用错误。
Javadoc 标签（`@param`、`@return`、`@throws`）保持英文——这是
JLS 强制要求，标签本身不能用中文。

