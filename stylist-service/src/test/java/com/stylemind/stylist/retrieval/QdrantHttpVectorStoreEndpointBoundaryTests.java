package com.stylemind.stylist.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylemind.stylist.repository.retrieval.QdrantHttpVectorStore;
import com.stylemind.stylist.service.retrieval.VectorStoreProtocolException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class QdrantHttpVectorStoreEndpointBoundaryTests {
    private static final String COLLECTION = "stylemind_p041_test_cccccccccccccccccccccccccccccccc";

    @Test
    void acceptsLoopbackTestEndpointAndLocalhostIsPinnedToLoopback() throws IOException {
        HttpServer server = server(exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) {
                respond(exchange, 200, "{\"result\":{\"exists\":false}}");
            } else {
                respond(exchange, 200, "{\"result\":true}");
            }
        });
        server.start();
        try {
            QdrantHttpVectorStore store = store(URI.create("http://localhost:" + server.getAddress().getPort()));
            store.createCollection(COLLECTION);
            store.deleteTestCollection(COLLECTION);
            assertThat(server.getAddress().getPort()).isPositive();
        } finally {
            server.stop(0);
        }

        assertThatCode(() -> store(URI.create("http://[::1]:6333"))).doesNotThrowAnyException();
    }

    @Test
    void rejectsRemoteHostsBeforeSendingAnyRequest() {
        assertRejected("http://203.0.113.9:6333");
        assertRejected("https://example.com:6333");
        assertRejected("http://localhost.attacker.invalid:6333");
        assertRejected("http://127.0.0.1.attacker.invalid:6333");
    }

    @Test
    void rejectsUserInfoPathsQueriesFragmentsAndInvalidPorts() {
        assertRejected("http://user:secret@127.0.0.1:6333");
        assertRejected("http://127.0.0.1:6333/qdrant");
        assertRejected("http://127.0.0.1:6333?target=remote");
        assertRejected("http://127.0.0.1:6333#fragment");
        assertRejected("http://127.0.0.1:70000");
    }

    @Test
    void doesNotFollowRedirectToAnotherDestination() throws IOException {
        AtomicInteger redirectedRequests = new AtomicInteger();
        HttpServer destination = server(exchange -> {
            redirectedRequests.incrementAndGet();
            respond(exchange, 200, "{\"result\":true}");
        });
        destination.start();
        HttpServer source = server(exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) {
                respond(exchange, 200, "{\"result\":{\"exists\":false}}");
            } else {
                exchange.getResponseHeaders().add("Location",
                        "http://127.0.0.1:" + destination.getAddress().getPort() + "/redirect-target");
                respond(exchange, 302, "");
            }
        });
        source.start();
        try {
            QdrantHttpVectorStore store = store(URI.create("http://127.0.0.1:" + source.getAddress().getPort()));
            assertThatThrownBy(() -> store.createCollection(COLLECTION))
                    .isInstanceOf(VectorStoreProtocolException.class)
                    .hasMessageContaining("unexpected redirect status 302");
            assertThat(redirectedRequests).hasValue(0);
        } finally {
            source.stop(0);
            destination.stop(0);
        }
    }

    private static void assertRejected(String value) {
        assertThatThrownBy(() -> store(URI.create(value)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("secret");
    }

    private static QdrantHttpVectorStore store(URI uri) {
        return new QdrantHttpVectorStore(uri, new ObjectMapper(), Duration.ofSeconds(1));
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
