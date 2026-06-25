package com.anthropic.claude.sdk.types;

/** SDK plugin configuration. Currently only local plugins are supported. */
public record SdkPluginConfig(String type, String path) {
    public static SdkPluginConfig local(String path) {
        return new SdkPluginConfig("local", path);
    }
}
