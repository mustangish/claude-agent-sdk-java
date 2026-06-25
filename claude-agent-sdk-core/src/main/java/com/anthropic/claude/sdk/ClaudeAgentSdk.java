package com.anthropic.claude.sdk;

import com.anthropic.claude.sdk.query.QuerySession;
import com.anthropic.claude.sdk.session.ForkSessionResult;
import com.anthropic.claude.sdk.session.SessionImport;
import com.anthropic.claude.sdk.session.SessionListing;
import com.anthropic.claude.sdk.session.SessionMutations;
import com.anthropic.claude.sdk.session.Sessions;
import com.anthropic.claude.sdk.session.SDKSessionInfo;
import com.anthropic.claude.sdk.session.SessionMessage;
import com.anthropic.claude.sdk.session.SessionSummary;
import com.anthropic.claude.sdk.transport.Transport;
import com.anthropic.claude.sdk.types.ClaudeAgentOptions;
import com.anthropic.claude.sdk.types.SessionStore;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * Public facade for the Claude Agent SDK — mirrors Python SDK's top-level functions.
 */
public final class ClaudeAgentSdk {

    private ClaudeAgentSdk() {}

    // ─── Query ──────────────────────────────────────────────────────────────

    public static QuerySession query(String prompt, ClaudeAgentOptions options) {
        return new QuerySession(prompt, options, null);
    }

    public static QuerySession query(String prompt) {
        return query(prompt, ClaudeAgentOptions.builder().build());
    }

    public static QuerySession queryWith(String prompt, Transport transport) {
        return new QuerySession(prompt, ClaudeAgentOptions.builder().build(), transport);
    }

    // ─── Session listing (local disk) ───────────────────────────────────────

    public static List<SDKSessionInfo> listSessions(String projectKey) {
        return SessionListing.listSessions(projectKey);
    }

    public static List<SDKSessionInfo> listSessions(String projectKey, Integer limit, int offset) {
        return SessionListing.listSessions(projectKey, limit, offset);
    }

    public static SDKSessionInfo getSessionInfo(String sessionId, String projectKey) {
        return SessionListing.readSessionInfo(projectKey, sessionId);
    }

    public static List<SessionMessage> getSessionMessages(String sessionId, String projectKey) {
        return SessionListing.getSessionMessages(projectKey, sessionId);
    }

    public static List<String> listSubagents(String sessionId, String projectKey) {
        return SessionListing.listSubagents(projectKey, sessionId);
    }

    public static List<SessionMessage> getSubagentMessages(
        String sessionId, String agentId, String projectKey
    ) {
        return SessionListing.getSubagentMessages(projectKey, sessionId, agentId);
    }

    public static String projectKeyForDirectory(String cwd) {
        return Sessions.projectKeyForDirectory(cwd);
    }

    // ─── Session listing (via SessionStore) ────────────────────────────────

    public static CompletionStage<List<SDKSessionInfo>> listSessionsFromStore(
        SessionStore store, String projectKey
    ) {
        return SessionListing.listSessionsFromStore(store, projectKey);
    }

    public static CompletionStage<SDKSessionInfo> getSessionInfoFromStore(
        SessionStore store, String projectKey, String sessionId
    ) {
        return SessionListing.getSessionInfoFromStore(store, projectKey, sessionId);
    }

    public static CompletionStage<List<SessionMessage>> getSessionMessagesFromStore(
        SessionStore store, String projectKey, String sessionId
    ) {
        return SessionListing.getSessionMessagesFromStore(store, projectKey, sessionId);
    }

    public static CompletionStage<List<String>> listSubagentsFromStore(
        SessionStore store, String projectKey, String sessionId
    ) {
        return SessionListing.listSubagentsFromStore(store, projectKey, sessionId);
    }

    public static CompletionStage<List<SessionMessage>> getSubagentMessagesFromStore(
        SessionStore store, String projectKey, String sessionId, String agentId
    ) {
        return SessionListing.getSubagentMessagesFromStore(store, projectKey, sessionId, agentId);
    }

    // ─── Session mutations (local disk) ─────────────────────────────────────

    public static CompletionStage<Void> renameSession(String sessionId, String newTitle, String projectKey) {
        return SessionMutations.renameSession(sessionId, newTitle, projectKey);
    }

    public static CompletionStage<Void> tagSession(String sessionId, String tag, String projectKey) {
        return SessionMutations.tagSession(sessionId, tag, projectKey);
    }

    public static CompletionStage<Void> deleteSession(String sessionId, String projectKey) {
        return SessionMutations.deleteSession(sessionId, projectKey);
    }

    public static CompletionStage<ForkSessionResult> forkSession(String sessionId, String newSessionId, String projectKey) {
        return SessionMutations.forkSession(sessionId, newSessionId, projectKey);
    }

    // ─── Session mutations (via SessionStore) ───────────────────────────────

    public static CompletionStage<Void> deleteSessionViaStore(
        SessionStore store, String projectKey, String sessionId
    ) {
        return SessionMutations.deleteSessionViaStore(store, projectKey, sessionId);
    }

    public static CompletionStage<Void> tagSessionViaStore(
        SessionStore store, String projectKey, String sessionId, String tag
    ) {
        return SessionMutations.tagSessionViaStore(store, projectKey, sessionId, tag);
    }

    public static CompletionStage<Void> renameSessionViaStore(
        SessionStore store, String projectKey, String sessionId, String newTitle
    ) {
        return SessionMutations.renameSessionViaStore(store, projectKey, sessionId, newTitle);
    }

    public static CompletionStage<ForkSessionResult> forkSessionViaStore(
        SessionStore store, String projectKey, String oldSessionId, String newSessionId
    ) {
        return SessionMutations.forkSessionViaStore(store, projectKey, oldSessionId, newSessionId);
    }

    public static CompletionStage<String> cloneSessionViaStore(
        SessionStore store, String projectKey, String oldSessionId, String newSessionId
    ) {
        return SessionMutations.cloneSessionViaStore(store, projectKey, oldSessionId, newSessionId);
    }

    // ─── Session import ─────────────────────────────────────────────────────

    public static CompletionStage<Integer> importSessionToStore(
        Path baseDir, SessionStore store, String projectKey, String sessionId
    ) {
        return SessionImport.importSessionToStore(baseDir, store, projectKey, sessionId);
    }

    // ─── Session summary ───────────────────────────────────────────────────

    public static Map<String, Object> foldSessionSummary(Map<String, Object> prev, Map<String, Object> entry) {
        return SessionSummary.foldSessionSummary(prev, entry);
    }
}
