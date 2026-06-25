package com.anthropic.claude.sdk.transport.subprocess;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks live subprocesses so they can be terminated when the JVM exits.
 *
 * <p>Mirrors Python SDK's {@code _ACTIVE_CHILDREN} set + {@code atexit.register(_kill_active_children)}
 * — when the Python interpreter exits, leftover {@code claude} processes get SIGTERM.
 *
 * <p>The Java equivalent uses {@link Runtime#addShutdownHook(Thread)} which fires on JVM
 * shutdown including SIGTERM.
 */
public final class ProcessRegistry {

    private static final Logger log = LoggerFactory.getLogger(ProcessRegistry.class);
    private static final Set<Process> ACTIVE = ConcurrentHashMap.newKeySet();
    private static final Thread CLEANUP_HOOK;

    static {
        CLEANUP_HOOK = new Thread(ProcessRegistry::destroyAll, "claude-cleanup");
        CLEANUP_HOOK.setDaemon(false);  // must complete before JVM exits
        Runtime.getRuntime().addShutdownHook(CLEANUP_HOOK);
    }

    private ProcessRegistry() {}

    public static void register(Process process) {
        if (process != null) ACTIVE.add(process);
    }

    public static void unregister(Process process) {
        if (process != null) ACTIVE.remove(process);
    }

    public static int activeCount() {
        return ACTIVE.size();
    }

    private static void destroyAll() {
        for (Process p : ACTIVE) {
            try {
                p.destroyForcibly();
            } catch (Exception e) {
                log.debug("Failed to destroy leaked subprocess on JVM shutdown", e);
            }
        }
        ACTIVE.clear();
    }
}
