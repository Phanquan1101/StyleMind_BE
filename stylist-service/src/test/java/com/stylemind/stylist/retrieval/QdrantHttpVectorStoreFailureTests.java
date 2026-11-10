package com.stylemind.stylist.retrieval;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylemind.stylist.repository.retrieval.QdrantHttpVectorStore;
import com.stylemind.stylist.service.retrieval.RetrievalUnavailableException;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class QdrantHttpVectorStoreFailureTests {
    private static final String COLLECTION = "stylemind_p041_test_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @Test
    void mapsConnectionFailureToRetrievalUnavailable() throws IOException {
        int unusedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            unusedPort = socket.getLocalPort();
        }
        QdrantHttpVectorStore store = store(unusedPort, Duration.ofMillis(300));

        assertThatThrownBy(() -> store.createCollection(COLLECTION))
                .isInstanceOf(RetrievalUnavailableException.class)
                .hasMessageContaining("Qdrant request failed or timed out");
    }

    @Test
    void mapsHttpTimeoutToRetrievalUnavailableWithoutRetry() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "p04-qdrant-timeout-test");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);
        server.createContext("/collections/", exchange -> {
            try {
                Thread.sleep(600);
                exchange.sendResponseHeaders(200, 0);
                exchange.close();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                exchange.close();
            } catch (IOException ignored) {
                exchange.close();
            }
        });
        server.start();
        try {
            QdrantHttpVectorStore store = store(server.getAddress().getPort(), Duration.ofMillis(100));
            assertThatThrownBy(() -> store.createCollection(COLLECTION))
                    .isInstanceOf(RetrievalUnavailableException.class)
                    .hasMessageContaining("Qdrant request failed or timed out");
        } finally {
            server.stop(0);
            executor.shutdownNow();
        }
    }

    @Test
    void refusesAnyNonP041CollectionName() throws IOException {
        int unusedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            unusedPort = socket.getLocalPort();
        }
        QdrantHttpVectorStore store = store(unusedPort, Duration.ofMillis(300));

        assertThatThrownBy(() -> store.createCollection("shared_stylemind_collection"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("restricted to a unique P04.1 test collection");
    }

    private static QdrantHttpVectorStore store(int port, Duration timeout) {
        return new QdrantHttpVectorStore(URI.create("http://127.0.0.1:" + port), new ObjectMapper(), timeout);
    }
}
