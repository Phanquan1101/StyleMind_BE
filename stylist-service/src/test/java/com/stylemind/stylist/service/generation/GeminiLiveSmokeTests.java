package com.stylemind.stylist.service.generation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** One explicit synthetic-only generation attempt, forced through the cumulative local meter. */
@EnabledIfEnvironmentVariable(named = "STYLEMIND_GEMINI_LIVE_CALLS_ENABLED", matches = "true")
class GeminiLiveSmokeTests {
    private static final String API_KEY_ENV = "STYLEMIND_GEMINI_API_KEY";
    private static final String MODEL = GeminiDeveloperApiClient.GEMINI_3_5_FLASH_LITE;
    // Keep the direct probe below the local meter's 30-second upstream cap so
    // a slow provider call is reported as the client's timeout, not a proxy 502.
    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(25);
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void sendsOneMinimalFlashLiteGenerationAndValidatesTheProviderTextResponse() throws Exception {
        String apiKey = System.getenv(API_KEY_ENV);
        assertThat(apiKey).as(API_KEY_ENV + " is required in this process").isNotBlank();
        assertThat(System.getenv("STYLEMIND_AI_PROVIDER")).isEqualTo("gemini");
        Meter meter = requireMeter();
        JsonNode before = meter.snapshot();
        assertBudget(before);

        GeminiDeveloperApiClient client = new GeminiDeveloperApiClient(
                GeminiDeveloperApiClient.API_BASE_URL, apiKey, json,
                Duration.ofSeconds(5), Duration.ofSeconds(30), true,
                ProxySelector.of(new InetSocketAddress("127.0.0.1", meter.proxyPort())), MODEL);
        ObjectNode request = json.createObjectNode();
        request.putArray("contents").addObject().put("role", "user").putArray("parts")
                .addObject().put("text", "Reply with one short sentence saying hello.");
        ObjectNode generation = request.putObject("generationConfig");
        generation.put("candidateCount", 1).put("maxOutputTokens", 64);
        generation.putObject("thinkingConfig").put("thinkingLevel", "MINIMAL");

        Instant started = Instant.now();
        JsonNode response = client.generateContent(request, PROBE_TIMEOUT);
        String text = responseText(response);
        assertThat(text).isNotBlank();
        JsonNode after = meter.snapshot();
        assertDelta(before, after);
        assertThat(after.path("stoppedOn429").asBoolean()).isFalse();

        JsonNode event = lastGeneration(after);
        assertThat(event.path("status").asInt()).isEqualTo(200);
        System.out.println("GEMINI_35_MINIMAL_PROBE " + json.writeValueAsString(json.createObjectNode()
                .put("status", "PASS")
                .put("checked_at", started.toString())
                .put("api_version", "v1beta")
                .put("endpoint", ":generateContent")
                .put("model", MODEL)
                .put("thinking_level", "MINIMAL")
                .put("client_timeout_ms", PROBE_TIMEOUT.toMillis())
                .put("output_text_valid", true)
                .put("finish_reason", "STOP")
                .put("generation_latency_ms", event.path("latencyMs").asLong(-1))
                .put("input_tokens", event.path("promptTokens").asInt(-1))
                .put("output_tokens", event.path("outputTokens").asInt(-1))
                .put("thought_tokens", event.path("thoughtTokens").asInt(-1))));
    }

    private Meter requireMeter() throws Exception {
        String summaryUrl = System.getenv("STYLEMIND_GEMINI_METER_URL");
        String proxyPortValue = System.getenv("STYLEMIND_GEMINI_METER_PROXY_PORT");
        assertThat(summaryUrl).as("local meter summary URL").isNotBlank();
        assertThat(proxyPortValue).as("local meter proxy port").isNotBlank();
        URI uri = URI.create(summaryUrl);
        assertThat(uri.getScheme()).isEqualTo("http");
        assertThat(uri.getHost()).isEqualTo("127.0.0.1");
        assertThat(uri.getPath()).isEqualTo("/__summary");
        int port = Integer.parseInt(proxyPortValue);
        assertThat(port).isBetween(1, 65_535);
        assertThat(uri.getPort()).isEqualTo(port);
        HttpClient local = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        return new Meter(local, uri, port);
    }

    private static void assertBudget(JsonNode before) {
        assertThat(before.path("stoppedOn429").asBoolean()).isFalse();
        assertThat(before.path("calls").path("embedding").asInt()).isLessThanOrEqualTo(10);
        assertThat(before.path("calls").path("tokenCount").asInt()).isLessThanOrEqualTo(20);
        assertThat(before.path("calls").path("generation").asInt() + 1).isLessThanOrEqualTo(5);
        assertThat(before.path("calls").path("blocked").asInt()).isZero();
    }

    private static void assertDelta(JsonNode before, JsonNode after) {
        assertThat(after.path("calls").path("embedding").asInt())
                .isEqualTo(before.path("calls").path("embedding").asInt());
        assertThat(after.path("calls").path("tokenCount").asInt())
                .isEqualTo(before.path("calls").path("tokenCount").asInt());
        assertThat(after.path("calls").path("generation").asInt())
                .isEqualTo(before.path("calls").path("generation").asInt() + 1);
        assertThat(after.path("calls").path("blocked").asInt())
                .isEqualTo(before.path("calls").path("blocked").asInt());
    }

    private static JsonNode lastGeneration(JsonNode summary) {
        JsonNode events = summary.path("events");
        for (int index = events.size() - 1; index >= 0; index--) {
            JsonNode event = events.get(index);
            if ("generation".equals(event.path("operation").asText())) return event;
        }
        throw new AssertionError("meter generation event missing");
    }

    private static String responseText(JsonNode response) {
        assertThat(response.path("promptFeedback").path("blockReason").asText("BLOCK_REASON_UNSPECIFIED"))
                .isEqualTo("BLOCK_REASON_UNSPECIFIED");
        JsonNode candidates = response.path("candidates");
        assertThat(candidates).hasSize(1);
        JsonNode candidate = candidates.get(0);
        assertThat(candidate.path("finishReason").asText()).isEqualTo("STOP");
        JsonNode parts = candidate.path("content").path("parts");
        assertThat(parts).isNotEmpty();
        StringBuilder result = new StringBuilder();
        for (JsonNode part : parts) {
            assertThat(part.path("thought").asBoolean(false)).isFalse();
            assertThat(part.path("text").isTextual()).isTrue();
            result.append(part.path("text").asText());
        }
        return result.toString();
    }

    private record Meter(HttpClient localClient, URI summaryUri, int proxyPort) {
        private JsonNode snapshot() throws Exception {
            HttpRequest request = HttpRequest.newBuilder(summaryUri).timeout(Duration.ofSeconds(3)).GET().build();
            HttpResponse<String> response = localClient.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            return new ObjectMapper().readTree(response.body());
        }
    }
}
