package com.stylemind.stylist.retrieval;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylemind.stylist.repository.retrieval.QdrantHttpVectorStore;
import com.stylemind.stylist.model.retrieval.RetrievalModel.VectorPoint;
import com.stylemind.stylist.service.retrieval.RetrievalUnavailableException;
import com.stylemind.stylist.service.retrieval.VectorStoreProtocolException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QdrantHttpVectorStoreProtocolTests {
    private static final String COLLECTION = "stylemind_p041_test_eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee";

    @Test
    void mapsMalformedSuccessfulJsonToProtocolFailureWithoutExposingBody() throws IOException {
        assertQueryResponse(new Response(200, "{\"sensitive\":\"fixture text\",\"result\":"))
                .isInstanceOf(VectorStoreProtocolException.class)
                .hasMessageContaining("malformed JSON")
                .hasMessageNotContaining("fixture text");
    }

    @Test
    void mapsWellFormedButWrongQueryShapeToProtocolFailure() throws IOException {
        assertQueryResponse(new Response(200, "{\"result\":{\"points\":{}}}"))
                .isInstanceOf(VectorStoreProtocolException.class)
                .hasMessageContaining("no points array");
    }

    @Test
    void mapsWellFormedButWrongUpsertShapeToProtocolFailure() throws IOException {
        HttpServer server = server(new Response(200, "{\"unexpected\":true}"));
        server.start();
        try {
            QdrantHttpVectorStore store = new QdrantHttpVectorStore(
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                    new ObjectMapper(), Duration.ofSeconds(1));
            store.createCollection(COLLECTION);
            VectorPoint point = new VectorPoint(UUID.randomUUID(), Collections.nCopies(128, 0.1f), Map.of());

            assertThatThrownBy(() -> store.upsert(COLLECTION, java.util.List.of(point)))
                    .isInstanceOf(VectorStoreProtocolException.class)
                    .hasMessageContaining("upsert response has an invalid operation result");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void mapsClientStatusToProtocolAndServerStatusToUnavailableWithoutBodyLogging() throws IOException {
        assertQueryResponse(new Response(400, "private request details"))
                .isInstanceOf(VectorStoreProtocolException.class)
                .hasMessageContaining("HTTP 400")
                .hasMessageNotContaining("private request details");
        assertQueryResponse(new Response(503, "private server details"))
                .isInstanceOf(RetrievalUnavailableException.class)
                .hasMessageContaining("HTTP 503")
                .hasMessageNotContaining("private server details");
    }

    private static org.assertj.core.api.AbstractThrowableAssert<?, ? extends Throwable> assertQueryResponse(
            Response queryResponse) throws IOException {
        HttpServer server = server(queryResponse);
        server.start();
        try {
            QdrantHttpVectorStore store = new QdrantHttpVectorStore(
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                    new ObjectMapper(), Duration.ofSeconds(1));
            store.createCollection(COLLECTION);
            return org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                    store.search(COLLECTION, new float[128], "TOP", 5));
        } finally {
            server.stop(0);
        }
    }

    private static HttpServer server(Response queryResponse) throws IOException {
        return server(exchange -> {
            String path = exchange.getRequestURI().getPath();
            if ("GET".equals(exchange.getRequestMethod()) && path.endsWith("/exists")) {
                respond(exchange, 200, "{\"result\":{\"exists\":false}}");
            } else if ("PUT".equals(exchange.getRequestMethod()) && path.endsWith("/points")) {
                respond(exchange, queryResponse.status(), queryResponse.body());
            } else if ("PUT".equals(exchange.getRequestMethod())) {
                respond(exchange, 200, "{\"result\":true}");
            } else if ("POST".equals(exchange.getRequestMethod())) {
                respond(exchange, queryResponse.status(), queryResponse.body());
            } else {
                respond(exchange, 405, "{}");
            }
        });
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

    private record Response(int status, String body) { }
}
