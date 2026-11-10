package com.stylemind.stylist.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylemind.stylist.repository.retrieval.QdrantHttpVectorStore;
import com.stylemind.stylist.service.retrieval.RetrievalUnavailableException;
import com.stylemind.stylist.service.retrieval.VectorStoreProtocolException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class QdrantHttpVectorStoreOwnershipTests {
    private static final String COLLECTION = "stylemind_p041_test_dddddddddddddddddddddddddddddddd";

    @Test
    void collectionCanOnlyBeDeletedByCreatingAdapterAndDeleteIsIdempotent() throws IOException {
        AtomicInteger deleteRequests = new AtomicInteger();
        HttpServer server = server(exchange -> {
            switch (exchange.getRequestMethod()) {
                case "GET" -> respond(exchange, 200, "{\"result\":{\"exists\":false}}");
                case "PUT" -> respond(exchange, 200, "{\"result\":true}");
                case "DELETE" -> {
                    deleteRequests.incrementAndGet();
                    respond(exchange, 200, "{\"result\":true}");
                }
                default -> respond(exchange, 405, "{}");
            }
        });
        server.start();
        try {
            URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            QdrantHttpVectorStore owner = store(endpoint, Duration.ofSeconds(1));
            QdrantHttpVectorStore otherAdapter = store(endpoint, Duration.ofSeconds(1));

            owner.createCollection(COLLECTION);
            assertThatThrownBy(() -> otherAdapter.deleteTestCollection(COLLECTION))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("does not own");
            assertThat(deleteRequests).hasValue(0);

            owner.deleteTestCollection(COLLECTION);
            owner.deleteTestCollection(COLLECTION);
            assertThat(deleteRequests).hasValue(1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void preExistingCollectionIsNeverRegisteredOrDeleted() throws IOException {
        AtomicInteger createRequests = new AtomicInteger();
        AtomicInteger deleteRequests = new AtomicInteger();
        HttpServer server = server(exchange -> {
            switch (exchange.getRequestMethod()) {
                case "GET" -> respond(exchange, 200, "{\"result\":{\"exists\":true}}");
                case "PUT" -> {
                    createRequests.incrementAndGet();
                    respond(exchange, 200, "{\"result\":true}");
                }
                case "DELETE" -> {
                    deleteRequests.incrementAndGet();
                    respond(exchange, 200, "{\"result\":true}");
                }
                default -> respond(exchange, 405, "{}");
            }
        });
        server.start();
        try {
            QdrantHttpVectorStore store = store(
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort()), Duration.ofSeconds(1));
            assertThatThrownBy(() -> store.createCollection(COLLECTION))
                    .isInstanceOf(VectorStoreProtocolException.class)
                    .hasMessageContaining("already exists");
            assertThatThrownBy(() -> store.deleteTestCollection(COLLECTION))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(createRequests).hasValue(0);
            assertThat(deleteRequests).hasValue(0);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void uncertainCreateTimeoutDoesNotGrantCleanupOwnership() throws IOException {
        AtomicInteger deleteRequests = new AtomicInteger();
        ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "p04-qdrant-create-timeout-test");
            thread.setDaemon(true);
            return thread;
        });
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) {
                respond(exchange, 200, "{\"result\":{\"exists\":false}}");
            } else if ("PUT".equals(exchange.getRequestMethod())) {
                try {
                    Thread.sleep(350);
                    respond(exchange, 200, "{\"result\":true}");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    exchange.close();
                } catch (IOException ignored) {
                    exchange.close();
                }
            } else if ("DELETE".equals(exchange.getRequestMethod())) {
                deleteRequests.incrementAndGet();
                respond(exchange, 200, "{\"result\":true}");
            } else {
                respond(exchange, 405, "{}");
            }
        });
        server.start();
        try {
            QdrantHttpVectorStore store = store(
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort()), Duration.ofMillis(100));
            assertThatThrownBy(() -> store.createCollection(COLLECTION))
                    .isInstanceOf(RetrievalUnavailableException.class);
            assertThatThrownBy(() -> store.deleteTestCollection(COLLECTION))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("does not own");
            assertThat(deleteRequests).hasValue(0);
        } finally {
            server.stop(0);
            executor.shutdownNow();
        }
    }

    @Test
    void deleteFailureRetainsOwnershipForRetry() throws IOException {
        AtomicInteger deleteRequests = new AtomicInteger();
        HttpServer server = server(exchange -> {
            switch (exchange.getRequestMethod()) {
                case "GET" -> respond(exchange, 200, "{\"result\":{\"exists\":false}}");
                case "PUT" -> respond(exchange, 200, "{\"result\":true}");
                case "DELETE" -> {
                    if (deleteRequests.incrementAndGet() == 1) respond(exchange, 503, "ignored-body");
                    else respond(exchange, 200, "{\"result\":true}");
                }
                default -> respond(exchange, 405, "{}");
            }
        });
        server.start();
        try {
            QdrantHttpVectorStore store = store(
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort()), Duration.ofSeconds(1));
            store.createCollection(COLLECTION);
            assertThatThrownBy(() -> store.deleteTestCollection(COLLECTION))
                    .isInstanceOf(RetrievalUnavailableException.class)
                    .hasMessageContaining("HTTP 503")
                    .hasMessageNotContaining("ignored-body");
            store.deleteTestCollection(COLLECTION);
            store.deleteTestCollection(COLLECTION);
            assertThat(deleteRequests).hasValue(2);
        } finally {
            server.stop(0);
        }
    }

    private static QdrantHttpVectorStore store(URI endpoint, Duration timeout) {
        return new QdrantHttpVectorStore(endpoint, new ObjectMapper(), timeout);
    }

    private static HttpServer server(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", handler);
        return server;
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
