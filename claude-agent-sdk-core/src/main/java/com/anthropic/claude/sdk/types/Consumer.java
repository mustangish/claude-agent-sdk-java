package com.anthropic.claude.sdk.types;

/**
 * Callback interface for one-input consumers.
 *
 * <p>Used for {@code stderr} callbacks. Avoids clashing with {@link java.util.function.Consumer}
 * which has a different exception policy.
 */
@FunctionalInterface
public interface Consumer<T> {
    void accept(T value);
}
