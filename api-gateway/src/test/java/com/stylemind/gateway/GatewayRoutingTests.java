package com.stylemind.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GatewayRoutingTests {

    private static final AtomicReference<RecordingIdentityServer> IDENTITY_SERVER = new AtomicReference<>();

    @Value("${local.server.port}")
    private int gatewayPort;

    @DynamicPropertySource
    static void routeIdentityToTestServer(DynamicPropertyRegistry registry) {
        registry.add("stylemind.gateway.identity-base-url", () -> identityServer().baseUrl());
    }

    @AfterAll
    static void stopIdentityServer() {
        RecordingIdentityServer server = IDENTITY_SERVER.getAndSet(null);
        if (server != null) {
            server.close();
        }
    }

    @BeforeEach
    void resetIdentityResponse() {
        identityServer().respondWith(202, "application/json", "{\"upstream\":\"identity\"}", Map.of());
    }

    @Test
    @Order(1)
    void forwardsPublicIdentityApiMethodPathQueryHeadersAndBodyUnchanged() throws Exception {
        String body = "{\"email\":\"gateway@example.test\",\"password\":\"sample\"}";
        HttpRequest request = HttpRequest.newBuilder(uri("/api/v1/auth/login?source=postman"))
                .header("Authorization", "Bearer gateway-test-token")
                .header("X-Correlation-Id", "gateway-correlation-test")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(202);
        assertThat(response.body()).isEqualTo("{\"upstream\":\"identity\"}");
        assertThat(response.headers().firstValue("X-Identity-Upstream")).contains("reached");
        assertThat(identityServer().lastMethod()).isEqualTo("POST");
        assertThat(identityServer().lastPath()).isEqualTo("/api/v1/auth/login?source=postman");
        assertThat(identityServer().lastAuthorization()).isEqualTo("Bearer gateway-test-token");
        assertThat(identityServer().lastCorrelationId()).isEqualTo("gateway-correlation-test");
        assertThat(identityServer().lastBody()).isEqualTo(body);
    }

    @Test
    @Order(2)
    void proxiesJwksAndDoesNotRouteNotificationInternalEndpoints() throws Exception {
        HttpResponse<String> jwks = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(uri("/.well-known/jwks.json")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(jwks.statusCode()).isEqualTo(202);
        int requestsBeforeInternalPath = identityServer().requestCount();
        HttpResponse<String> internal = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(uri("/internal/v1/password-resets"))
                        .POST(HttpRequest.BodyPublishers.ofString("{}"))
                        .header("Content-Type", "application/json")
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(internal.statusCode()).isEqualTo(404);
        assertThat(identityServer().requestCount()).isEqualTo(requestsBeforeInternalPath);
    }

    @Test
    @Order(3)
    void preservesProblemDetailsStatusContentTypeAndResponseHeaders() throws Exception {
        String problem = "{\"type\":\"about:blank\",\"title\":\"Conflict\",\"status\":409,"
                + "\"code\":\"EMAIL_ALREADY_REGISTERED\"}";
        identityServer().respondWith(409, "application/problem+json", problem, Map.of(
                "Cache-Control", "no-store",
                "Location", "/api/v1/auth/register",
                "Retry-After", "17"));

        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(uri("/api/v1/auth/register?next=verify"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"collision@example.test\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.headers().firstValue("Content-Type")).contains("application/problem+json");
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(response.headers().firstValue("Location")).contains("/api/v1/auth/register");
        assertThat(response.headers().firstValue("Retry-After")).contains("17");
        assertThat(response.body()).isEqualTo(problem);
        assertThat(identityServer().lastPath()).isEqualTo("/api/v1/auth/register?next=verify");
    }

    @Test
    @Order(4)
    void doesNotAddBodyTo204OrHeadResponses() throws Exception {
        identityServer().respondWith(204, "application/json", "must-not-appear", Map.of());
        HttpResponse<byte[]> noContent = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(uri("/api/v1/me/addresses/123")).DELETE().build(),
                HttpResponse.BodyHandlers.ofByteArray());

        assertThat(noContent.statusCode()).isEqualTo(204);
        assertThat(noContent.body()).isEmpty();

        identityServer().respondWith(200, "application/json", "must-not-appear", Map.of());
        HttpResponse<byte[]> head = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(uri("/api/v1/me/addresses")).method("HEAD",
                        HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofByteArray());

        assertThat(head.statusCode()).isEqualTo(200);
        assertThat(head.body()).isEmpty();
        assertThat(identityServer().lastMethod()).isEqualTo("HEAD");
    }

    @Test
    @Order(5)
    void proxiesJwksWithoutWrappingOrChangingTheDocument() throws Exception {
        String jwks = "{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"gateway-test-key\","
                + "\"use\":\"sig\",\"alg\":\"RS256\",\"n\":\"abc\",\"e\":\"AQAB\"}]}";
        identityServer().respondWith(200, "application/json", jwks, Map.of("Cache-Control", "public, max-age=60"));

        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(uri("/.well-known/jwks.json")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).contains("application/json");
        assertThat(response.headers().firstValue("Cache-Control")).contains("public, max-age=60");
        assertThat(response.body()).isEqualTo(jwks);
    }

    @Test
    @Order(6)
    void internalNotificationPathsAndEncodedVariantsNeverReachIdentityRoute() throws Exception {
        String[] paths = {
            "/internal/v1/email-verifications",
            "/%69nternal/v1/email-verifications",
            "/internal%2fv1/email-verifications",
            "/x/../internal/v1/password-resets"
        };
        for (String path : paths) {
            int before = identityServer().requestCount();
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + gatewayPort + path))
                            .POST(HttpRequest.BodyPublishers.ofString("{}"))
                            .header("Content-Type", "application/json")
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).as("No Gateway route for %s", path).isBetween(400, 499);
            assertThat(identityServer().requestCount()).as("Identity must not receive %s", path).isEqualTo(before);
        }
    }

    @Test
    @Order(100)
    void stoppedIdentityUpstreamFailsPromptlyWithoutReplayingWriteRequest() throws Exception {
        RecordingIdentityServer server = identityServer();
        int before = server.requestCount();
        server.stopAcceptingRequests();
        long started = System.nanoTime();
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(uri("/api/v1/auth/register"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"upstream-down@example.test\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        long durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        assertThat(response.statusCode()).isBetween(500, 599);
        assertThat(durationMillis).isLessThan(5000);
        assertThat(server.requestCount()).isEqualTo(before);
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + gatewayPort + path);
    }

    private static RecordingIdentityServer identityServer() {
        RecordingIdentityServer current = IDENTITY_SERVER.get();
        if (current != null) {
            return current;
        }
        RecordingIdentityServer created;
        try {
            created = new RecordingIdentityServer();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to start the local Identity test server", exception);
        }
        if (IDENTITY_SERVER.compareAndSet(null, created)) {
            return created;
        }
        created.close();
        return IDENTITY_SERVER.get();
    }

    private static final class RecordingIdentityServer {
        private final HttpServer server;
        private final AtomicReference<String> lastMethod = new AtomicReference<>();
        private final AtomicReference<String> lastPath = new AtomicReference<>();
        private final AtomicReference<String> lastAuthorization = new AtomicReference<>();
        private final AtomicReference<String> lastCorrelationId = new AtomicReference<>();
        private final AtomicReference<String> lastBody = new AtomicReference<>();
        private final AtomicInteger requestCount = new AtomicInteger();
        private final AtomicBoolean stopped = new AtomicBoolean();
        private volatile int responseStatus = 202;
        private volatile String responseContentType = "application/json";
        private volatile String responseBody = "{\"upstream\":\"identity\"}";
        private volatile Map<String, String> responseHeaders = Map.of();

        private RecordingIdentityServer() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                requestCount.incrementAndGet();
                lastMethod.set(exchange.getRequestMethod());
                lastPath.set(exchange.getRequestURI().toString());
                lastAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                lastCorrelationId.set(exchange.getRequestHeaders().getFirst("X-Correlation-Id"));
                lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", responseContentType);
                exchange.getResponseHeaders().add("X-Identity-Upstream", "reached");
                responseHeaders.forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
                boolean bodyAllowed = !"HEAD".equals(exchange.getRequestMethod()) && responseStatus != 204;
                exchange.sendResponseHeaders(responseStatus, bodyAllowed ? body.length : -1);
                if (bodyAllowed && body.length > 0) {
                    exchange.getResponseBody().write(body);
                }
                exchange.close();
            });
            server.start();
        }

        private String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        private String lastMethod() {
            return lastMethod.get();
        }

        private String lastPath() {
            return lastPath.get();
        }

        private String lastAuthorization() {
            return lastAuthorization.get();
        }

        private String lastCorrelationId() {
            return lastCorrelationId.get();
        }

        private String lastBody() {
            return lastBody.get();
        }

        private int requestCount() {
            return requestCount.get();
        }

        private void respondWith(int status, String contentType, String body, Map<String, String> headers) {
            responseStatus = status;
            responseContentType = contentType;
            responseBody = body;
            responseHeaders = Map.copyOf(headers);
        }

        private void stopAcceptingRequests() {
            if (stopped.compareAndSet(false, true)) {
                server.stop(0);
            }
        }

        private void close() {
            stopAcceptingRequests();
        }
    }
}
