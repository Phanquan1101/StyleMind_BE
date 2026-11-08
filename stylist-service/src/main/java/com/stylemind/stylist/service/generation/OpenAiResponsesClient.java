package com.stylemind.stylist.service.generation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stylemind.stylist.errors.ErrorCode;
import com.stylemind.stylist.errors.StylistApiException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

/** Minimal one-attempt Responses API client for exact token preflight and structured generation. */
public final class OpenAiResponsesClient {
    private static final int MAX_RESPONSE_BYTES = 256 * 1024;
    private final URI baseUri;
    private final String apiKey;
    private final ObjectMapper json;
    private final Duration connectTimeout;
    private final Duration requestTimeout;
    private final HttpClient http;

    public OpenAiResponsesClient(String baseUrl, String apiKey, ObjectMapper json,
                                 Duration connectTimeout, Duration requestTimeout) {
        this.baseUri = validateOrigin(baseUrl);
        this.apiKey = apiKey == null ? "" : apiKey;
        this.json = Objects.requireNonNull(json);
        this.connectTimeout = Objects.requireNonNull(connectTimeout);
        this.requestTimeout = Objects.requireNonNull(requestTimeout);
        if (connectTimeout.isNegative() || connectTimeout.isZero()
                || requestTimeout.isNegative() || requestTimeout.isZero()) throw new IllegalArgumentException("timeout");
        this.http = HttpClient.newBuilder().connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    public JsonNode countInputTokens(ObjectNode request, Duration timeout) {
        return post("/v1/responses/input_tokens", request, timeout);
    }

    public JsonNode createResponse(ObjectNode request, Duration timeout) {
        return post("/v1/responses", request, timeout);
    }

    private JsonNode post(String path, ObjectNode payload, Duration timeout) {
        if (apiKey.isBlank()) throw new StylistApiException(ErrorCode.GENERATION_RUNTIME_UNCONFIGURED);
        if (payload == null || timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new StylistApiException(ErrorCode.GENERATION_PROVIDER_TIMEOUT);
        }
        final String requestBody;
        try {
            requestBody = json.writeValueAsString(payload);
        } catch (IOException exception) {
            throw new StylistApiException(ErrorCode.GENERATION_PROVIDER_PROTOCOL_ERROR);
        }
        try {
            Duration boundedTimeout = timeout.compareTo(requestTimeout) <= 0 ? timeout : requestTimeout;
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(path))
                    .timeout(boundedTimeout)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();
            try (InputStream body = response.body()) {
                if (status == 408 || status == 504) {
                    throw new StylistApiException(ErrorCode.GENERATION_PROVIDER_TIMEOUT);
                }
                if (status == 401 || status == 403 || status == 429 || status >= 500) {
                    throw new StylistApiException(ErrorCode.GENERATION_PROVIDER_UNAVAILABLE);
                }
                if (status < 200 || status >= 300) {
                    throw new StylistApiException(ErrorCode.GENERATION_PROVIDER_PROTOCOL_ERROR);
                }
                byte[] bytes = body.readNBytes(MAX_RESPONSE_BYTES + 1);
                if (bytes.length > MAX_RESPONSE_BYTES) {
                    throw new StylistApiException(ErrorCode.GENERATION_PROVIDER_PROTOCOL_ERROR);
                }
                try {
                    return json.readTree(bytes);
                } catch (IOException | IllegalArgumentException exception) {
                    throw new StylistApiException(ErrorCode.GENERATION_PROVIDER_PROTOCOL_ERROR);
                }
            }
        } catch (StylistApiException exception) {
            throw exception;
        } catch (java.net.http.HttpTimeoutException exception) {
            throw new StylistApiException(ErrorCode.GENERATION_PROVIDER_TIMEOUT);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new StylistApiException(ErrorCode.GENERATION_PROVIDER_UNAVAILABLE);
        } catch (IOException | IllegalArgumentException exception) {
            throw new StylistApiException(ErrorCode.GENERATION_PROVIDER_UNAVAILABLE);
        }
    }

    private static URI validateOrigin(String raw) {
        URI uri = URI.create(raw == null ? "" : raw.trim());
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                || uri.getRawFragment() != null || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())) {
            throw new IllegalArgumentException("OpenAI base URL must be an HTTP(S) origin");
        }
        return uri;
    }
}
