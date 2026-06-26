---
name: javadoc-migration
description: Translate Python docstrings to Java Javadoc on the Claude Agent SDK. Use when JAVADOC_AUDIT.md has a 🔴 pending method, or when adding a new public API. Encodes translation rules so the loop does not re-derive them per method.
---

# javadoc-migration

The Java SDK is a port of the Python SDK. The Python source has thorough
docstrings (`Args:`, `Returns:`, `Raises:`, `Example:` blocks). The Java
port has Javadoc on most types but **missing on most public methods** —
specifically the user-facing facade (`ClaudeAgentSdk`), interactive client
(`ClaudeSdkClient`), one-shot query (`QuerySession`), and the session
package.

This skill encodes the translation rules so the loop does not re-derive
them per method.

## Translation rules

### 1. Python → Javadoc tag mapping

| Python | Java |
|---|---|
| `Args:` section with one bullet per arg | One `@param name description` per line |
| `Returns:` section | `@return description` (omit if `-> None`) |
| `Raises:` section | One `@throws ExceptionType description` per line |
| `Example:` section | `{@code ...}` code block (preserve verbatim) |
| `Note:` / `Notes:` section | Plain paragraph after the tags |
| `See Also:` section | `{@link ...}` references |

### 2. First-line summary

- Python's first line of a docstring (before `Args:`) becomes the
  summary — a single sentence, ending with a period.
- If Python has a multi-line summary, condense to one sentence.
- Do NOT prepend "Returns" / "Constructs" / "Gets" — the Javadoc
  convention is to start with a verb in third person: "Send a prompt to
  Claude and return a session.", not "Returns a session.".

### 3. Cross-references

- Type references use `{@link com.anthropic.claude.sdk.types.Foo}` —
  never bare `Foo`. The compiler checks links.
- For param types that need disambiguation, use `{@code Foo}` (renders
  as monospace, no link).
- Multi-arg lines: `* @param sessionId  the session identifier (UUID)` —
  two spaces between name and description, per the Javadoc convention.

### 4. Drop Python-specific constructs

- `Optional[X]` (PEP 484) → just describe the nullability in prose.
- `:type X:` blocks → drop; Java types are in the signature.
- `self` / `cls` → drop; Java is implicit.
- `*args` / `**kwargs` → translate to `@param ...  variadic` with a brief
  explanation.
- `from __future__ import annotations` quirks → ignore.

### 5. Preserve meaning, not wording

- "Yields" → "Returns" (Java has no generators).
- "coroutine" / "async" → describe the CompletionStage pattern.
- "Lifted from X" → drop, attribute the source code if relevant.
- Marketing language ("Conveniently", "Simply", "Just call") → drop.
- Verbose examples → keep the first one or two, drop the rest.

### 6. Wrap, format, lint

- Wrap at 100 chars per line.
- Indent with 4 spaces (one space after `*`).
- No trailing whitespace.
- Empty line between summary and `@param` block.
- `*` continuation lines align with first letter of the summary text.

### 7. Exclude from Javadoc

These do NOT need Javadoc (compiler auto-documents them):

- Record component accessors (`record Foo(String bar)` → `bar()`)
- Builder setter methods (`Builder.foo(String x)`)
- `toString()`, `equals()`, `hashCode()`, `clone()`
- `Enum.values()`, `Enum.valueOf(String)`
- `static of(...)` factories that just call the constructor (when the
  constructor is documented)
- Default methods that just throw `UnsupportedOperationException`

### 8. Verify after writing

```bash
# Lint the Javadoc — catches malformed tags, broken {@link}, HTML errors
mvn -B -ntp -pl claude-agent-sdk-core javadoc:javadoc

# Strict mode (treats warnings as errors)
mvn -B -ntp -pl claude-agent-sdk-core javadoc:javadoc \
  -Dmaven.javadoc.failOnError=true
```

If the build fails, the Javadoc is malformed. Fix and re-run.

## Loop protocol

1. **Read** `JAVADOC_AUDIT.md` to find the next 🔴 pending method.
2. **Spawn** a `python-docstring-reader` sub-agent (Haiku) to read the
   Python source and return the docstring verbatim.
3. **Spawn** a `javadoc-translator` sub-agent (Sonnet) to translate the
   docstring into a Javadoc block.
4. **Apply** the Javadoc to the Java file. The translator outputs the
   exact text; do not rephrase.
5. **Verify** with `mvn -pl claude-agent-sdk-core javadoc:javadoc` —
   the build must succeed.
6. **Update** `JAVADOC_AUDIT.md`: mark the method ✅. Never delete a row.
7. **Exit.** Do not chain into tests.

## Out of scope

- Reformatting existing Javadoc (only fill gaps).
- Adding `@see` cross-references that the Python source doesn't have.
- Translating internal/private methods (Python has no `private` so
  internal code is harder to map — only do public API).
- Reformatting record component Javadoc (the compiler generates this from
  the record header).
