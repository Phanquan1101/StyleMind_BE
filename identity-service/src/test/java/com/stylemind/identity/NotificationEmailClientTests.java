package com.stylemind.identity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylemind.identity.client.notification.NotificationEmailClient;
import com.stylemind.identity.client.notification.NotificationFeignClient;
import com.stylemind.identity.client.notification.payload.EmailDispatch;
import com.stylemind.identity.client.notification.payload.PasswordResetEmailDispatch;
import com.stylemind.identity.errors.ApiOperationException;
import feign.Feign;
import feign.Request;
import feign.Retryer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.support.ResponseEntityDecoder;
import org.springframework.cloud.openfeign.support.SpringDecoder;
import org.springframework.cloud.openfeign.support.SpringEncoder;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.beans.factory.ObjectFactory;

class NotificationEmailClientTests {

    @Test
    void onlyAcceptedResponseConfirmsEmailDelivery() throws Exception {
        try (HttpResponseServer server = new HttpResponseServer(202, 0)) {
            client(server, Duration.ofSeconds(2)).send(dispatch());
            org.assertj.core.api.Assertions.assertThat(server.validServiceToken()).isTrue();
            org.assertj.core.api.Assertions.assertThat(server.validVerificationPayload()).isTrue();
        }
    }

    @Test
    void otherSuccessfulHttpResponsesRemainUnknownDelivery() throws Exception {
        for (int status : new int[] {200, 204}) {
            try (HttpResponseServer server = new HttpResponseServer(status, 0)) {
                assertThatThrownBy(() -> client(server, Duration.ofSeconds(2)).send(dispatch()))
                        .isInstanceOfSatisfying(ApiOperationException.class, failure -> {
                            org.assertj.core.api.Assertions.assertThat(failure.status().value()).isEqualTo(504);
                            org.assertj.core.api.Assertions.assertThat(failure.code())
                                    .isEqualTo("EMAIL_DELIVERY_UNKNOWN");
                        });
            }
        }
    }

    @Test
    void mapsDefiniteFailureAndUnknownNotificationResponses() throws Exception {
        assertDeliveryResult(503, 503, "EMAIL_DELIVERY_FAILED", Duration.ofSeconds(2));
        assertDeliveryResult(504, 504, "EMAIL_DELIVERY_UNKNOWN", Duration.ofSeconds(2));
    }

    @Test
    void mapsHttpResponseTimeoutToUnknownDelivery() throws Exception {
        try (HttpResponseServer server = new HttpResponseServer(202, 600)) {
            NotificationEmailClient client = client(server, Duration.ofMillis(100));
            assertThatThrownBy(() -> client.send(dispatch()))
                    .isInstanceOfSatisfying(ApiOperationException.class, failure -> {
                        org.assertj.core.api.Assertions.assertThat(failure.status().value()).isEqualTo(504);
                        org.assertj.core.api.Assertions.assertThat(failure.code()).isEqualTo("EMAIL_DELIVERY_UNKNOWN");
                    });
        }
    }

    @Test
    void passwordResetUsesItsDedicatedInternalRouteAndRequiresExactAcceptedStatus() throws Exception {
        try (HttpResponseServer server = new HttpResponseServer(202, 0)) {
            client(server, Duration.ofSeconds(2)).sendPasswordReset(
                    new PasswordResetEmailDispatch("reset@example.test", "012345"));
            org.assertj.core.api.Assertions.assertThat(server.requestPath())
                    .isEqualTo("/internal/v1/password-resets");
            org.assertj.core.api.Assertions.assertThat(server.validServiceToken()).isTrue();
            org.assertj.core.api.Assertions.assertThat(server.validResetPayload()).isTrue();
        }
        for (int status : new int[] {200, 204}) {
            try (HttpResponseServer server = new HttpResponseServer(status, 0)) {
                assertThatThrownBy(() -> client(server, Duration.ofSeconds(2)).sendPasswordReset(
                        new PasswordResetEmailDispatch("reset@example.test", "012345")))
                        .isInstanceOfSatisfying(ApiOperationException.class, failure -> {
                            org.assertj.core.api.Assertions.assertThat(failure.status().value()).isEqualTo(504);
                            org.assertj.core.api.Assertions.assertThat(failure.code())
                                    .isEqualTo("EMAIL_DELIVERY_UNKNOWN");
                        });
            }
        }
    }

    @Test
    void responseServerConsumesContentLengthAndChunkedBodies() throws Exception {
        try (HttpResponseServer server = new HttpResponseServer(202, 0)) {
            org.assertj.core.api.Assertions.assertThat(sendRawRequest(server.port(),
                    "Content-Length: 4\r\n", "test"))
                    .isEqualTo("HTTP/1.1 202 Accepted");
            org.assertj.core.api.Assertions.assertThat(server.requestPath()).isEqualTo("/content-length");
        }

        try (HttpResponseServer server = new HttpResponseServer(202, 0)) {
            org.assertj.core.api.Assertions.assertThat(sendRawRequest(server.port(),
                    "Transfer-Encoding: chunked\r\n", "4\r\ntest\r\n0\r\n\r\n"))
                    .isEqualTo("HTTP/1.1 202 Accepted");
            org.assertj.core.api.Assertions.assertThat(server.requestPath()).isEqualTo("/chunked");
        }
    }

    @Test
    void incompleteChunkedBodyTimesOutAndServerThreadTerminates() throws Exception {
        try (HttpResponseServer server = new HttpResponseServer(202, 0);
                Socket socket = new Socket("127.0.0.1", server.port())) {
            socket.setSoTimeout(4000);
            Instant started = Instant.now();
            socket.getOutputStream().write(("POST /incomplete HTTP/1.1\r\n"
                    + "Host: 127.0.0.1\r\n"
                    + "Transfer-Encoding: chunked\r\n"
                    + "Connection: close\r\n\r\n"
                    + "10\r\nshort").getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();

            int responseByte = socket.getInputStream().read();

            org.assertj.core.api.Assertions.assertThat(responseByte).isEqualTo(-1);
            org.assertj.core.api.Assertions.assertThat(Duration.between(started, Instant.now()))
                    .isLessThan(Duration.ofSeconds(4));
        }
    }

    private String sendRawRequest(int port, String bodyHeader, String wireBody) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(3000);
            socket.getOutputStream().write(("POST "
                    + (bodyHeader.startsWith("Content-Length:") ? "/content-length" : "/chunked")
                    + " HTTP/1.1\r\nHost: 127.0.0.1\r\n" + bodyHeader
                    + "Connection: close\r\n\r\n" + wireBody).getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            ByteArrayOutputStream responseLine = new ByteArrayOutputStream();
            int previous = -1;
            int current;
            while ((current = socket.getInputStream().read()) >= 0) {
                responseLine.write(current);
                if (previous == '\r' && current == '\n') {
                    byte[] bytes = responseLine.toByteArray();
                    return new String(bytes, 0, bytes.length - 2, StandardCharsets.US_ASCII);
                }
                previous = current;
            }
            throw new IOException("Server closed before writing a response line");
        }
    }

    private void assertDeliveryResult(int upstreamStatus, int expectedStatus, String expectedCode,
            Duration readTimeout) throws Exception {
        try (HttpResponseServer server = new HttpResponseServer(upstreamStatus, 0)) {
            NotificationEmailClient client = client(server, readTimeout);
            assertThatThrownBy(() -> client.send(dispatch()))
                    .isInstanceOfSatisfying(ApiOperationException.class, failure -> {
                        org.assertj.core.api.Assertions.assertThat(failure.status().value()).isEqualTo(expectedStatus);
                        org.assertj.core.api.Assertions.assertThat(failure.code()).isEqualTo(expectedCode);
                    });
        }
    }

    private NotificationEmailClient client(HttpResponseServer server, Duration readTimeout) {
        HttpMessageConverters converters = new HttpMessageConverters(new MappingJackson2HttpMessageConverter());
        ObjectFactory<HttpMessageConverters> converterFactory = () -> converters;
        NotificationFeignClient feignClient = Feign.builder()
                .contract(new org.springframework.cloud.openfeign.support.SpringMvcContract())
                .encoder(new SpringEncoder(converterFactory))
                .decoder(new ResponseEntityDecoder(new SpringDecoder(converterFactory)))
                .options(new Request.Options(1000, TimeUnit.MILLISECONDS,
                        readTimeout.toMillis(), TimeUnit.MILLISECONDS, false))
                .retryer(Retryer.NEVER_RETRY)
                .target(NotificationFeignClient.class, "http://127.0.0.1:" + server.port());
        return new NotificationEmailClient(feignClient, "x".repeat(32));
    }

    private EmailDispatch dispatch() {
        return new EmailDispatch("test@example.test", "012345");
    }

    private static final class HttpResponseServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final Thread thread;
        private final AtomicReference<String> requestPath = new AtomicReference<>();
        private final AtomicBoolean validServiceToken = new AtomicBoolean();
        private final AtomicBoolean validVerificationPayload = new AtomicBoolean();
        private final AtomicBoolean validResetPayload = new AtomicBoolean();
        private final ObjectMapper objectMapper = new ObjectMapper();

        HttpResponseServer(int status, long delayMillis) throws IOException {
            serverSocket = new ServerSocket(0);
            serverSocket.setSoTimeout(2000);
            thread = new Thread(() -> respond(status, delayMillis), "notification-client-test-server");
            thread.setDaemon(true);
            thread.start();
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        String requestPath() {
            return requestPath.get();
        }

        boolean validServiceToken() {
            return validServiceToken.get();
        }

        boolean validVerificationPayload() {
            return validVerificationPayload.get();
        }

        boolean validResetPayload() {
            return validResetPayload.get();
        }

        private void respond(int status, long delayMillis) {
            try (Socket socket = serverSocket.accept()) {
                socket.setSoTimeout(2000);
                requestPath.set(readAndInspectRequest(socket));
                if (delayMillis > 0) {
                    Thread.sleep(delayMillis);
                }
                String reason = switch (status) {
                    case 200 -> "OK";
                    case 202 -> "Accepted";
                    case 204 -> "No Content";
                    case 503 -> "Service Unavailable";
                    default -> "Gateway Timeout";
                };
                String retryAfter = status == 503 ? "Retry-After: 0\r\n" : "";
                byte[] response = ("HTTP/1.1 " + status + " " + reason
                        + "\r\n" + retryAfter + "Content-Length: 0\r\nConnection: close\r\n\r\n")
                        .getBytes(StandardCharsets.US_ASCII);
                socket.getOutputStream().write(response);
                socket.getOutputStream().flush();
            } catch (Exception ignored) {
                // The timeout test intentionally closes the client socket before this server responds.
            }
        }

        private String readAndInspectRequest(Socket socket) throws IOException {
            ByteArrayOutputStream received = new ByteArrayOutputStream();
            int matched = 0;
            byte[] terminator = {'\r', '\n', '\r', '\n'};
            while (matched < terminator.length) {
                int next = socket.getInputStream().read();
                if (next < 0) {
                    throw new IOException("Client closed before sending request headers");
                }
                received.write(next);
                matched = next == (terminator[matched] & 0xff) ? matched + 1
                        : next == (terminator[0] & 0xff) ? 1 : 0;
            }
            String headers = received.toString(StandardCharsets.US_ASCII);
            for (String line : headers.split("\\r\\n")) {
                if (line.regionMatches(true, 0, "X-StyleMind-Service-Token:", 0,
                        "X-StyleMind-Service-Token:".length())) {
                    validServiceToken.set(line.substring("X-StyleMind-Service-Token:".length()).trim()
                            .equals("x".repeat(32)));
                }
            }
            String body = consumeRequestBody(socket, headers);
            try {
                JsonNode json = objectMapper.readTree(body);
                validVerificationPayload.set(json.path("recipientEmail").asText().equals("test@example.test")
                        && json.path("verificationCode").asText().equals("012345")
                        && !json.has("resetCode"));
                validResetPayload.set(json.path("recipientEmail").asText().equals("reset@example.test")
                        && json.path("resetCode").asText().equals("012345")
                        && !json.has("verificationCode"));
            } catch (Exception ignored) {
                // The test asserts only the boolean contract result, never the received body.
            }
            String firstLine = headers.split("\\r\\n", 2)[0];
            return firstLine.split(" ", 3)[1];
        }

        private String consumeRequestBody(Socket socket, String headers) throws IOException {
            for (String line : headers.split("\\r\\n")) {
                if (line.regionMatches(true, 0, "Content-Length:", 0, "Content-Length:".length())) {
                    int contentLength;
                    try {
                        contentLength = Integer.parseInt(line.substring("Content-Length:".length()).trim());
                    } catch (NumberFormatException exception) {
                        throw new IOException("Invalid Content-Length request header", exception);
                    }
                    if (contentLength < 0) {
                        throw new IOException("Invalid Content-Length request header");
                    }
                    return new String(readExactly(socket, contentLength), StandardCharsets.UTF_8);
                }
                if (line.regionMatches(true, 0, "Transfer-Encoding:", 0, "Transfer-Encoding:".length())
                        && line.toLowerCase(java.util.Locale.ROOT).contains("chunked")) {
                    return consumeChunkedBody(socket);
                }
            }
            throw new IOException("Client request did not specify a body length");
        }

        private byte[] readExactly(Socket socket, int contentLength) throws IOException {
            byte[] body = socket.getInputStream().readNBytes(contentLength);
            if (body.length != contentLength) {
                throw new IOException("Client closed before sending the complete request body");
            }
            return body;
        }

        private String consumeChunkedBody(Socket socket) throws IOException {
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            while (true) {
                String sizeLine = readAsciiLine(socket);
                String sizeText = sizeLine.split(";", 2)[0].trim();
                int chunkSize;
                try {
                    chunkSize = Integer.parseInt(sizeText, 16);
                } catch (NumberFormatException exception) {
                    throw new IOException("Invalid chunked request body", exception);
                }
                if (chunkSize == 0) {
                    while (!readAsciiLine(socket).isEmpty()) {
                        // Ignore request trailers; this test server only needs to drain the body.
                    }
                    return body.toString(StandardCharsets.UTF_8);
                }
                body.write(readExactly(socket, chunkSize));
                if (socket.getInputStream().read() != '\r' || socket.getInputStream().read() != '\n') {
                    throw new IOException("Invalid chunk terminator");
                }
            }
        }

        private String readAsciiLine(Socket socket) throws IOException {
            ByteArrayOutputStream line = new ByteArrayOutputStream();
            int previous = -1;
            int current;
            while ((current = socket.getInputStream().read()) >= 0) {
                line.write(current);
                if (previous == '\r' && current == '\n') {
                    byte[] bytes = line.toByteArray();
                    return new String(bytes, 0, bytes.length - 2, StandardCharsets.US_ASCII);
                }
                previous = current;
            }
            throw new IOException("Client closed before completing a chunked request body");
        }

        @Override
        public void close() throws Exception {
            serverSocket.close();
            thread.join(3000);
            if (thread.isAlive()) {
                throw new IllegalStateException("Notification test server thread did not terminate");
            }
        }
    }
}
