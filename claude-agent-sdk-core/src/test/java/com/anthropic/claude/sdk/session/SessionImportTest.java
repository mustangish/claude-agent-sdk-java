package com.anthropic.claude.sdk.session;

import com.anthropic.claude.sdk.types.SessionKey;
import com.anthropic.claude.sdk.types.SessionStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SessionImportTest {

    @Test
    void importsJsonlFilesIntoStore(@TempDir Path tempDir) throws Exception {
        // Create two JSONL files in the source directory
        Path sourceDir = tempDir.resolve("source");
        Files.createDirectories(sourceDir);

        Path jsonl1 = sourceDir.resolve("session1.jsonl");
        Files.writeString(jsonl1,
            "{\"type\":\"user\",\"uuid\":\"u-1\",\"timestamp\":\"2026-06-25T00:00:00Z\"," +
            "\"message\":{\"role\":\"user\",\"content\":\"Hi\"}}\n" +
            "{\"type\":\"assistant\",\"uuid\":\"a-1\",\"timestamp\":\"2026-06-25T00:00:01Z\"," +
            "\"message\":{\"role\":\"assistant\",\"content\":\"Hello\"}}\n");

        Path jsonl2 = sourceDir.resolve("session2.jsonl");
        Files.writeString(jsonl2,
            "{\"type\":\"user\",\"uuid\":\"u-2\",\"timestamp\":\"2026-06-25T00:00:02Z\"," +
            "\"message\":{\"role\":\"user\",\"content\":\"Bye\"}}\n");

        // Override InMemorySessionStore to provide listSubkeys (so SessionImport doesn't crash)
        SessionStore realStore = new InMemorySessionStore() {
            @Override
            public java.util.concurrent.CompletionStage<java.util.List<String>> listSubkeys(
                SessionStore.SessionListSubkeysKey key
            ) {
                return java.util.concurrent.CompletableFuture.completedFuture(List.of());
            }
        };

        // Import session1 → key "s1", session2 → key "s2"
        // (sourceDir contains both jsonl1 and jsonl2, but we want each in its own key)
        int total1 = SessionImport.importSessionFile(
            jsonl1, realStore, "p", "s1"
        ).toCompletableFuture().get();
        // Just import the second file directly
        int total2 = SessionImport.importSessionFile(
            jsonl2, realStore, "p", "s2"
        ).toCompletableFuture().get();

        assertThat(total1).isEqualTo(2);
        assertThat(total2).isEqualTo(1);

        var loaded1 = realStore.load(new SessionKey("p", "s1", null)).toCompletableFuture().get();
        var loaded2 = realStore.load(new SessionKey("p", "s2", null)).toCompletableFuture().get();
        assertThat(loaded1).hasSize(2);
        assertThat(loaded1.get(0).uuid()).isEqualTo("u-1");
        assertThat(loaded1.get(1).uuid()).isEqualTo("a-1");
        assertThat(loaded2).hasSize(1);
        assertThat(loaded2.get(0).uuid()).isEqualTo("u-2");
    }

    @Test
    void returnsZeroWhenSourceDirMissing(@TempDir Path tempDir) throws Exception {
        Path missing = tempDir.resolve("does-not-exist");
        InMemorySessionStore store = new InMemorySessionStore();

        int total = SessionImport.importSessionToStore(
            missing, store, "p", "s"
        ).toCompletableFuture().get();

        assertThat(total).isEqualTo(0);
    }

    @Test
    void skipsBlankAndInvalidLines(@TempDir Path tempDir) throws Exception {
        Path sourceDir = tempDir.resolve("source");
        Files.createDirectories(sourceDir);

        Path jsonl = sourceDir.resolve("test.jsonl");
        Files.writeString(jsonl,
            "{\"type\":\"user\",\"uuid\":\"u-1\",\"timestamp\":\"ts\"," +
            "\"message\":{\"role\":\"user\",\"content\":\"Hi\"}}\n" +
            "\n" +  // blank line
            "this is not valid json\n" +  // invalid line
            "{\"type\":\"assistant\",\"uuid\":\"a-1\",\"timestamp\":\"ts\"," +
            "\"message\":{\"role\":\"assistant\",\"content\":\"Hello\"}}\n");

        InMemorySessionStore store = new InMemorySessionStore() {
            @Override
            public java.util.concurrent.CompletionStage<java.util.List<String>> listSubkeys(
                SessionStore.SessionListSubkeysKey key
            ) {
                return java.util.concurrent.CompletableFuture.completedFuture(List.of());
            }
        };

        int total = SessionImport.importSessionToStore(sourceDir, store, "p", "s")
            .toCompletableFuture().get();

        assertThat(total).isEqualTo(2);  // blank and invalid lines skipped
    }
}
