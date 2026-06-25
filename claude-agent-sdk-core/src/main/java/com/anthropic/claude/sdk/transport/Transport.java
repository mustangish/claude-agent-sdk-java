package com.anthropic.claude.sdk.transport;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Iterator;
import java.util.List;

/**
 * Low-level transport for Claude Code communication.
 *
 * <p>This is the extension point for non-default implementations (e.g. SSH-based remote CLI,
 * custom daemon, etc.). The default is {@link com.anthropic.claude.sdk.transport.subprocess.SubprocessCliTransport}.
 *
 * <p>The interface mirrors the Python SDK's {@code Transport} ABC: it deals only with raw
 * I/O. The control-protocol layer (request/response correlation, hooks, etc.) sits on top.
 */
public interface Transport extends AutoCloseable {

    /** Start the transport and prepare for I/O. */
    void connect();

    /** Write raw data (typically JSON + newline) to the transport. */
    void write(String data);

    /** Read parsed JSON messages from the transport. Blocking iterator. */
    Iterator<JsonNode> readMessages();

    /** End the input stream (close stdin for process transports). */
    void endInput();

    /** Check if the transport is ready to send/receive messages. */
    boolean isReady();

    /** Close the transport and release all resources. */
    @Override
    void close();
}
