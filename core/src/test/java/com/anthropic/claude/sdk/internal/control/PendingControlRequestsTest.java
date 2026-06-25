package com.anthropic.claude.sdk.internal.control;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PendingControlRequestsTest {

    @Test
    void registerAndCompleteReturnsResponse() throws Exception {
        var pending = new PendingControlRequests();
        String id = pending.register();
        assertThat(pending.pendingCount()).isEqualTo(1);

        var responseNode = JsonNodeFactory.instance.objectNode().put("ok", true);
        pending.complete(id, responseNode);

        var received = pending.await(id, 1000);
        assertThat(received.path("ok").asBoolean()).isTrue();
        assertThat(pending.pendingCount()).isEqualTo(0);
    }

    @Test
    void multiplePendingRequestsAreIndependent() throws Exception {
        var pending = new PendingControlRequests();
        String idA = pending.register();
        String idB = pending.register();
        assertThat(pending.pendingCount()).isEqualTo(2);

        pending.complete(idB, JsonNodeFactory.instance.objectNode().put("which", "B"));
        pending.complete(idA, JsonNodeFactory.instance.objectNode().put("which", "A"));

        assertThat(pending.await(idA, 1000).path("which").asText()).isEqualTo("A");
        assertThat(pending.await(idB, 1000).path("which").asText()).isEqualTo("B");
    }

    @Test
    void completeErrorThrowsOnAwait() {
        var pending = new PendingControlRequests();
        String id = pending.register();
        pending.completeError(id, "boom");

        assertThatThrownBy(() -> pending.await(id, 1000))
            .isInstanceOf(ExecutionException.class)
            .hasMessageContaining("boom");
    }

    @Test
    void awaitTimesOut() {
        var pending = new PendingControlRequests();
        String id = pending.register();

        assertThatThrownBy(() -> pending.await(id, 50))
            .isInstanceOf(TimeoutException.class);
    }

    @Test
    void cancelAllCancelsPendingRequests() {
        var pending = new PendingControlRequests();
        String idA = pending.register();
        String idB = pending.register();
        assertThat(pending.pendingCount()).isEqualTo(2);

        pending.cancelAll();

        assertThatThrownBy(() -> pending.await(idA, 1000))
            .isInstanceOf(ExecutionException.class);
        assertThatThrownBy(() -> pending.await(idB, 1000))
            .isInstanceOf(ExecutionException.class);

        // After awaiting, the futures are removed from the map.
        assertThat(pending.pendingCount()).isEqualTo(0);
    }

    @Test
    void uniqueIdsGenerated() {
        var pending = new PendingControlRequests();
        String id1 = pending.register();
        String id2 = pending.register();
        String id3 = pending.register();
        assertThat(id1).isNotEqualTo(id2);
        assertThat(id2).isNotEqualTo(id3);
    }
}
