package com.anthropic.claude.sdk.mcp;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as an SDK MCP tool that can be invoked by Claude.
 *
 * <p>The annotated method must be on a public class with a no-arg constructor, accept a
 * single argument whose type is the JSON-serializable input schema, and return either a
 * {@link ToolResult} or a {@code CompletableFuture<ToolResult>}.
 *
 * <p>Example:
 * <pre>{@code
 * public class MyTools {
 *     @Tool("greet", "Greet a user", GreetArgs.class)
 *     public ToolResult greet(GreetArgs args) {
 *         return ToolResult.text("Hello, " + args.name() + "!");
 *     }
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Tool {
    String name();
    String description();
    Class<?> inputSchema();
}
