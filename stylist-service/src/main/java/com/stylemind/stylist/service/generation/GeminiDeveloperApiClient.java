package com.stylemind.stylist.service.generation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stylemind.stylist.errors.ErrorCode;
import com.stylemind.stylist.errors.StylistApiException;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Minimal single-attempt Gemini Developer API transport. Request and error bodies are never logged. */
public final class GeminiDeveloperApiClient {
    public static final String API_BASE_URL = "https://generativelanguage.googleapis.com";
    public static final String GENERATION_MODEL = "gemini-3.5-flash-lite";
    public static final String GEMINI_3_5_FLASH_LITE = GENERATION_MODEL;
    public static final String EMBEDDING_MODEL = "gemini-embedding-001";
    public static final int EMBEDDING_DIMENSIONS = 1_536;
    public static final int EMBEDDING_MAX_COUNT_TOKENS = 2_041;
    private static final int MAX_RESPONSE_BYTES = 256 * 1024;
    private static final Logger LOGGER = LoggerFactory.getLogger(GeminiDeveloperApiClient.class);

    private final URI baseUri;
    private final String apiKey;
    private final ObjectMapper json;
    private final Duration connectTimeout;
    private final Duration requestTimeout;
    private final boolean externalCallsEnabled;
    private final String generationModel;
    private final HttpClient http;

    public GeminiDeveloperApiClient(String baseUrl, String apiKey, ObjectMapper json,
                                    Duration connectTimeout, Duration requestTimeout,
                                    boolean externalCallsEnabled) {
        this(baseUrl, apiKey, json, connectTimeout, requestTimeout, externalCallsEnabled, null);
    }

    public GeminiDeveloperApiClient(String baseUrl, String apiKey, ObjectMapper json,
                                    Duration connectTimeout, Duration requestTimeout,
                                    boolean externalCallsEnabled, ProxySelector proxySelector) {
        this(baseUrl, apiKey, json, connectTimeout, requestTimeout, externalCallsEnabled,
                proxySelector, GENERATION_MODEL);
    }

    public GeminiDeveloperApiClient(String baseUrl, String apiKey, ObjectMapper json,
                                    Duration connectTimeout, Duration requestTimeout,
                                    boolean externalCallsEnabled, ProxySelector proxySelector,
                                    String generationModel) {
        this.baseUri = validateBase(baseUrl);
        this.apiKey = apiKey == null ? "" : apiKey;
        this.json = Objects.requireNonNull(json);
        this.connectTimeout = requirePositive(connectTimeout, "connect timeout");
        this.requestTimeout = requirePositive(requestTimeout, "request timeout");
        this.externalCallsEnabled = externalCallsEnabled;
        this.generationModel = validateGenerationModel(generationModel);
        HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(this.connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER);
        if (proxySelector != null) builder.proxy(proxySelector);
        this.http = builder.build();
    }

    public boolean isConfigured() { return externalCallsEnabled && !apiKey.isBlank(); }
    public String generationModel() { return generationModel; }

    public JsonNode countGenerationTokens(ObjectNode generateContentRequest, Duration timeout) {
        if (generateContentRequest == null) throw protocol();
        ObjectNode payload = json.createObjectNode();
        ObjectNode countedRequest = generateContentRequest.deepCopy();
        countedRequest.put("model", "models/" + generationModel);
        payload.set("generateContentRequest", countedRequest);
        return post("/v1beta/models/" + generationModel + ":countTokens", payload, timeout);
    }

    /** Uses the Developer API CountTokensRequest.contents form used by the Java SDK. */
    public JsonNode countGenerationContentTokens(String syntheticText, Duration timeout) {
        if (syntheticText == null || syntheticText.isBlank()) throw protocol();
        ObjectNode payload = json.createObjectNode();
        ArrayNode contents = payload.putArray("contents");
        contents.addObject().put("role", "user").putArray("parts").addObject().put("text", syntheticText);
        return post("/v1beta/models/" + generationModel + ":countTokens", payload, timeout);
    }

    public JsonNode countEmbeddingTokens(String text, Duration timeout) {
        if (text == null || text.isBlank()) throw protocol();
        ObjectNode payload = json.createObjectNode();
        ArrayNode contents = payload.putArray("contents");
        ObjectNode content = contents.addObject().put("role", "user");
        content.putArray("parts").addObject().put("text", text);
        return post("/v1beta/models/" + EMBEDDING_MODEL + ":countTokens", payload, timeout);
    }

    public JsonNode generateContent(ObjectNode generateContentRequest, Duration timeout) {
        if (generateContentRequest == null) throw protocol();
        return post("/v1beta/models/" + generationModel + ":generateContent", generateContentRequest, timeout);
    }

    public JsonNode batchEmbedContent(String text, String taskType, Duration timeout) {
        if (text == null || text.isBlank()
                || !"RETRIEVAL_QUERY".equals(taskType) && !"RETRIEVAL_DOCUMENT".equals(taskType)) {
            throw protocol();
        }
        ObjectNode payload = json.createObjectNode();
        ObjectNode request = payload.putArray("requests").addObject()
                .put("model", "models/" + EMBEDDING_MODEL);
        ObjectNode content = request.putObject("content");
        content.putArray("parts").addObject().put("text", text);
        request.put("taskType", taskType).put("outputDimensionality", EMBEDDING_DIMENSIONS);
        JsonNode response = post("/v1beta/models/" + EMBEDDING_MODEL + ":batchEmbedContents", payload, timeout);
        JsonNode embeddings = response.path("embeddings");
        if (!embeddings.isArray() || embeddings.size() != 1) throw protocol();
        ObjectNode mapped = json.createObjectNode();
        mapped.set("embedding", embeddings.get(0).deepCopy());
        return mapped;
    }

    private JsonNode post(String path, ObjectNode payload, Duration timeout) {
        if (!isConfigured()) throw new StylistApiException(ErrorCode.GENERATION_RUNTIME_UNCONFIGURED);
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new StylistApiException(ErrorCode.GENERATION_PROVIDER_TIMEOUT);
        }
        final byte[] requestBody;
        try {
            requestBody = json.writeValueAsBytes(payload);
        } catch (JsonProcessingException exception) {
            throw protocol();
        }
        try {
            Duration bounded = timeout.compareTo(requestTimeout) <= 0 ? timeout : requestTimeout;
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(path)).timeout(bounded)
                    .header("x-goog-api-key", apiKey)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody)).build();
            HttpResponse<byte[]> response = sendBounded(request, bounded);
            int status = response.statusCode();
            if (status == 408 || status == 504) {
                LOGGER.warn("Gemini HTTP provider returned timeout status {}", status);
                throw timeoutFailure();
            }
            if (status == 401 || status == 403 || status == 429 || status >= 500) {
                LOGGER.warn("Gemini HTTP provider returned unavailable status {}", status);
                throw new StylistApiException(ErrorCode.GENERATION_PROVIDER_UNAVAILABLE);
            }
            if (status < 200 || status >= 300) throw protocol();
            try {
                return json.readTree(response.body());
            } catch (IOException | IllegalArgumentException exception) {
                LOGGER.warn("Gemini HTTP response parser rejected the response ({})",
                        exception.getClass().getSimpleName());
                throw protocol();
            }
        } catch (StylistApiException exception) {
            throw exception;
        } catch (TimeoutException exception) {
            LOGGER.warn("Gemini HTTP request timed out");
            throw timeoutFailure();
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (hasCause(cause, java.net.http.HttpTimeoutException.class)) {
                LOGGER.warn("Gemini HTTP transport timed out ({})", exceptionTypes(cause));
                throw timeoutFailure();
            }
            if (hasCause(cause, ResponseTooLargeException.class)) throw protocol();
            if (cause instanceof RuntimeException runtimeException) {
                LOGGER.warn("Gemini HTTP client failed ({})", exceptionTypes(cause));
                throw runtimeException;
            }
            LOGGER.warn("Gemini HTTP transport failed ({})", exceptionTypes(cause));
            throw new StylistApiException(ErrorCode.GENERATION_PROVIDER_UNAVAILABLE);
        } catch (ResponseTooLargeException exception) {
            LOGGER.warn("Gemini HTTP response exceeded the configured size limit");
            throw protocol();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Gemini HTTP request was interrupted");
            throw new StylistApiException(ErrorCode.GENERATION_PROVIDER_UNAVAILABLE);
        } catch (IllegalArgumentException exception) {
            LOGGER.warn("Gemini HTTP request could not be constructed ({})",
                    exception.getClass().getSimpleName());
            throw new StylistApiException(ErrorCode.GENERATION_PROVIDER_UNAVAILABLE);
        }
    }

    private static URI validateBase(String raw) {
        URI uri = URI.create(raw == null ? "" : raw.trim());
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                || uri.getRawFragment() != null || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())) {
            throw new IllegalArgumentException("Gemini API base must be an HTTP(S) origin");
        }
        return uri;
    }

    private static Duration requirePositive(Duration value, String name) {
        if (value == null || value.isNegative() || value.isZero()) throw new IllegalArgumentException(name);
        return value;
    }

    private static String validateGenerationModel(String model) {
        if (!GENERATION_MODEL.equals(model) && !GEMINI_3_5_FLASH_LITE.equals(model)) {
            throw new IllegalArgumentException("Unsupported Gemini generation model");
        }
        return model;
    }

    private static StylistApiException protocol() {
        return new StylistApiException(ErrorCode.GENERATION_PROVIDER_PROTOCOL_ERROR);
    }

    private static StylistApiException timeoutFailure() {
        return new StylistApiException(ErrorCode.GENERATION_PROVIDER_TIMEOUT);
    }

    private static HttpResponse.BodyHandler<byte[]> boundedResponseBody() {
        return responseInfo -> new LimitedBodySubscriber(MAX_RESPONSE_BYTES);
    }

    private HttpResponse<byte[]> sendBounded(HttpRequest request, Duration timeout)
            throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<HttpResponse<byte[]>> pending = http.sendAsync(request, boundedResponseBody());
        try {
            return pending.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException exception) {
            pending.cancel(true);
            throw exception;
        }
    }

    private static boolean hasCause(Throwable failure, Class<? extends Throwable> causeType) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (causeType.isInstance(current)) return true;
        }
        return false;
    }

    private static String exceptionTypes(Throwable failure) {
        if (failure == null) return "unknown";
        StringBuilder types = new StringBuilder();
        int count = 0;
        for (Throwable current = failure; current != null && count < 4; current = current.getCause(), count++) {
            if (count > 0) types.append(" -> ");
            types.append(current.getClass().getSimpleName());
        }
        return types.toString();
    }

    private static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final int maximumBytes;
        private final ByteArrayOutputStream body = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private Flow.Subscription subscription;

        private LimitedBodySubscriber(int maximumBytes) { this.maximumBytes = maximumBytes; }

        @Override public CompletionStage<byte[]> getBody() { return result; }

        @Override
        public void onSubscribe(Flow.Subscription value) {
            subscription = value;
            value.request(1);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            try {
                for (ByteBuffer buffer : buffers) {
                    if (buffer.remaining() > maximumBytes - body.size()) {
                        subscription.cancel();
                        result.completeExceptionally(new ResponseTooLargeException());
                        return;
                    }
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);
                    body.write(bytes);
                }
                subscription.request(1);
            } catch (IOException | RuntimeException exception) {
                subscription.cancel();
                result.completeExceptionally(exception);
            }
        }

        @Override public void onError(Throwable failure) { result.completeExceptionally(failure); }
        @Override public void onComplete() { result.complete(body.toByteArray()); }
    }

    private static final class ResponseTooLargeException extends RuntimeException { }
}
