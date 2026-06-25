package com.anthropic.claude.sdk.query;

import com.anthropic.claude.sdk.errors.CliConnectionError;
import com.anthropic.claude.sdk.internal.JacksonSupport;
import com.anthropic.claude.sdk.internal.message.MessageParser;
import com.anthropic.claude.sdk.transport.Transport;
import com.anthropic.claude.sdk.transport.subprocess.SubprocessCliTransport;
import com.anthropic.claude.sdk.types.ClaudeAgentOptions;
import com.anthropic.claude.sdk.types.Message;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A one-shot query session: spawns a CLI subprocess, sends the prompt, and yields Messages.
 *
 * <p>Returned by {@code ClaudeAgentSdk.query(prompt, options)}. Implements {@link Iterable}
 * (so callers can use enhanced-for) and {@link AutoCloseable} (auto-close on iteration end
 * is the caller's responsibility).
 *
 * <p>For stateful, multi-turn conversations, use {@code ClaudeSdkClient} instead.
 */
public final class QuerySession implements Iterable<Message>, AutoCloseable {

    private final Transport transport;
    private final MessageParser parser;
    private final ObjectMapper mapper = JacksonSupport.mapper();
    private final AtomicBoolean userMessageSent = new AtomicBoolean(false);
    private final String initialPrompt;
    private final String sessionId;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public QuerySession(String prompt, ClaudeAgentOptions options, Transport transport) {
        this.initialPrompt = prompt;
        this.sessionId = options.sessionId() != null ? options.sessionId() : "default";
        this.transport = transport != null ? transport : new SubprocessCliTransport(options);
        this.parser = new MessageParser(mapper);
    }

    public Transport transport() {
        return transport;
    }

    /** Connect to CLI if not already connected and send the initial prompt. */
    public void start() {
        if (closed.get()) throw new IllegalStateException("QuerySession is closed");
        transport.connect();
        sendInitialPrompt();
    }

    private void sendInitialPrompt() {
        if (userMessageSent.compareAndSet(false, true)) {
            Map<String, Object> message = new LinkedHashMap<>();
            message.put("type", "user");
            message.put("message", Map.of(
                "role", "user",
                "content", initialPrompt));
            message.put("parent_tool_use_id", null);
            message.put("session_id", sessionId);
            try {
                transport.write(mapper.writeValueAsString(message) + "\n");
            } catch (JsonProcessingException e) {
                throw new CliConnectionError("Failed to serialize initial prompt", e);
            }
            // For one-shot queries, EOF stdin after sending the prompt.
            transport.endInput();
        }
    }

    @Override
    public Iterator<Message> iterator() {
        // Ensure we start on first iteration if caller didn't.
        if (!userMessageSent.get()) {
            start();
        }
        Iterator<JsonNode> raw = transport.readMessages();
        return new Iterator<>() {
            Message next;

            @Override
            public boolean hasNext() {
                if (next != null) return true;
                while (raw.hasNext()) {
                    Message parsed = parser.parse(raw.next());
                    if (parsed != null) {
                        next = parsed;
                        return true;
                    }
                }
                return false;
            }

            @Override
            public Message next() {
                if (!hasNext()) throw new NoSuchElementException();
                Message m = next;
                next = null;
                return m;
            }
        };
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            transport.close();
        }
    }
}
