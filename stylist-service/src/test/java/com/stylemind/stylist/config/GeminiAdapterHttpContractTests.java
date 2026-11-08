package com.stylemind.stylist.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stylemind.stylist.errors.ErrorCode;
import com.stylemind.stylist.errors.StylistApiException;
import com.stylemind.stylist.errors.ApiExceptionHandler;
import com.stylemind.stylist.model.generation.GenerationModel.FrozenOutfit;
import com.stylemind.stylist.model.generation.GenerationModel.ItemAlias;
import com.stylemind.stylist.model.generation.GenerationModel.RequestContext;
import com.stylemind.stylist.service.generation.GeminiDeveloperApiClient;
import com.stylemind.stylist.service.generation.GeminiRuntimeEmbeddingAdapter;
import com.stylemind.stylist.service.generation.GeminiStructuredRecommendationModel;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import org.springframework.mock.web.MockHttpServletRequest;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Exercises Gemini Developer API adapters using only a local embedded HTTP fixture. */
class GeminiAdapterHttpContractTests {
    private static final String FIXTURE_KEY = "fixture-key-never-real";
    private final ObjectMapper json = new ObjectMapper();
    private final List<Captured> captured = new CopyOnWriteArrayList<>();
    private final AtomicInteger generationCountStatus = new AtomicInteger(200);
    private final AtomicInteger generationStatus = new AtomicInteger(200);
    private final AtomicInteger embeddingCountStatus = new AtomicInteger(200);
    private final AtomicInteger embeddingStatus = new AtomicInteger(200);
    private final AtomicInteger generationTokens = new AtomicInteger(220);
    private final AtomicInteger embeddingTokens = new AtomicInteger(35);
    private final AtomicInteger generationDelayMillis = new AtomicInteger();
    private final AtomicInteger generationBodyDelayMillis = new AtomicInteger();
    private final AtomicReference<String> generationOutput = new AtomicReference<>("valid");
    private final AtomicReference<String> vectorOutput = new AtomicReference<>("valid");
    private HttpServer server;

    @BeforeEach
    void startFixture() throws Exception {
        generationBodyDelayMillis.set(0);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool(task -> {
            Thread thread = new Thread(task, "gemini-contract-fixture");
            thread.setDaemon(true);
            return thread;
        }));
        server.createContext("/", this::respond);
        server.start();
    }

    @AfterEach
    void stopFixture() { if (server != null) server.stop(0); }

    @Test
    void generationCountsSameSystemAndContentBeforeUsingGeminiStructuredJsonContract() throws Exception {
        RequestContext context = context("synthetic Vietnamese query");
        List<FrozenOutfit> outfits = outfits();
        var output = model().select(context, outfits, List.of("synthetic reviewed fact"), Duration.ofSeconds(10));

        assertThat(output.recommendations()).hasSize(1);
        assertThat(output.recommendations().getFirst().outfitAlias()).isEqualTo("O1");
        assertThat(captured).extracting(Captured::path).containsExactly(
                "/v1beta/models/gemini-3.5-flash-lite:countTokens",
                "/v1beta/models/gemini-3.5-flash-lite:generateContent");
        Captured count = captured.getFirst();
        Captured generation = captured.get(1);
        JsonNode countedRequest = count.body().path("generateContentRequest");
        assertThat(countedRequest.path("model").asText()).isEqualTo("models/gemini-3.5-flash-lite");
        assertThat(countedRequest.path("systemInstruction").toString()).contains("exactly one TOP and one BOTTOM");
        assertThat(countedRequest.path("contents").toString()).contains("synthetic Vietnamese query", "O1_TOP", "O1_BOTTOM");
        assertThat(generation.body().has("model")).isFalse();
        assertThat(countedRequest.path("contents")).isEqualTo(generation.body().path("contents"));
        assertThat(countedRequest.path("systemInstruction")).isEqualTo(generation.body().path("systemInstruction"));
        JsonNode config = generation.body().path("generationConfig");
        assertThat(config.path("candidateCount").asInt()).isEqualTo(1);
        assertThat(config.path("maxOutputTokens").asInt()).isEqualTo(2_000);
        assertThat(config.path("thinkingConfig").path("thinkingLevel").asText()).isEqualTo("MINIMAL");
        assertThat(config.path("thinkingConfig").has("thinkingBudget")).isFalse();
        assertThat(config.path("responseFormat").path("text").path("mimeType").asText())
                .isEqualTo("APPLICATION_JSON");
        JsonNode schema = config.path("responseFormat").path("text").path("schema");
        assertThat(schema.toString()).contains("O1", "O1_TOP", "O1_BOTTOM", "required")
                .doesNotContain("minLength", "maxLength");
        assertThat(schema.path("type").asText()).isEqualTo("object");
        assertThat(schema.path("properties").path("recommendations").path("type").asText()).isEqualTo("array");
        assertThat(schema.path("properties").path("recommendations").path("items").path("type").asText())
                .isEqualTo("object");
        assertThat(schema.toString()).contains("\"type\":\"object\"", "\"type\":\"array\"",
                "\"type\":\"string\"");
        assertThat(generation.body().path("tools").isMissingNode()).isTrue();
        assertThat(count.apiKeyPresent()).isTrue();
        assertThat(generation.apiKeyPresent()).isTrue();
        assertThat(generation.authorizationHeaderPresent()).isFalse();
        assertThat(generation.body().toString()).doesNotContain(context.requestId().toString(),
                outfits.getFirst().items().getFirst().variantId().toString(),
                outfits.getFirst().items().getFirst().productId().toString(),
                "Authorization", "Bearer", "price_vnd", "image_url");

        JsonNode wireContract = json.readTree(getClass().getResourceAsStream(
                "/contracts/gemini-developer-v1beta-count-tokens-generate-request.json"));
        assertThat(wireContract.path("model").asText()).isEqualTo(GeminiDeveloperApiClient.GENERATION_MODEL);
        assertThat(fieldNames(count.body())).containsExactly(wireContract.path("countTokensEnvelope").asText());
        assertThat(fieldNames(countedRequest)).containsExactlyInAnyOrder("model", "systemInstruction", "contents",
                "generationConfig");
        assertThat(fieldNames(countedRequest.path("generationConfig"))).containsExactlyInAnyOrder(
                "candidateCount", "maxOutputTokens", "thinkingConfig", "responseFormat");
        JsonNode countTextFormat = countedRequest.path("generationConfig").path("responseFormat").path("text");
        assertThat(countTextFormat.path("mimeType").asText())
                .isEqualTo(wireContract.path("responseFormatMimeType").asText());
        assertThat(countedRequest.path("generationConfig").has("responseMimeType")).isFalse();
        assertThat(countTextFormat.path("schema").toString())
                .contains("\"additionalProperties\":false")
                .doesNotContain("\"additionalProperties\":true");
        assertThat(countedRequest.path("generationConfig").path("responseFormat"))
                .isEqualTo(generation.body().path("generationConfig").path("responseFormat"));
        assertThat(countedRequest.path("systemInstruction")).isEqualTo(generation.body().path("systemInstruction"));
        assertThat(countedRequest.path("contents")).isEqualTo(generation.body().path("contents"));
        assertThat(count.body().has("contents")).isFalse();
    }

    @Test
    void flashLiteUsesSameSelectedModelAndOnlyItsSupportedThinkingAndStructuredOutputFields() {
        var output = model(GeminiDeveloperApiClient.GEMINI_3_5_FLASH_LITE).select(
                context("synthetic request"), outfits(), List.of("synthetic reviewed fact"), Duration.ofSeconds(10));

        assertThat(output.recommendations()).hasSize(1);
        assertThat(captured).extracting(Captured::path).containsExactly(
                "/v1beta/models/gemini-3.5-flash-lite:countTokens",
                "/v1beta/models/gemini-3.5-flash-lite:generateContent");
        JsonNode countedRequest = captured.getFirst().body().path("generateContentRequest");
        JsonNode generation = captured.get(1).body();
        JsonNode config = generation.path("generationConfig");
        assertThat(countedRequest.path("model").asText()).isEqualTo("models/gemini-3.5-flash-lite");
        assertThat(countedRequest.path("contents")).isEqualTo(generation.path("contents"));
        assertThat(countedRequest.path("systemInstruction")).isEqualTo(generation.path("systemInstruction"));
        assertThat(countedRequest.path("generationConfig")).isEqualTo(config);
        assertThat(config.path("candidateCount").asInt()).isEqualTo(1);
        assertThat(config.path("maxOutputTokens").asInt()).isEqualTo(2_000);
        assertThat(config.path("thinkingConfig").path("thinkingLevel").asText()).isEqualTo("MINIMAL");
        assertThat(config.path("thinkingConfig").has("thinkingBudget")).isFalse();
        assertThat(config.path("responseFormat").path("text").path("mimeType").asText())
                .isEqualTo("APPLICATION_JSON");
        JsonNode schema = config.path("responseFormat").path("text").path("schema");
        assertThat(schema.path("type").asText()).isEqualTo("object");
        assertThat(schema.path("properties").path("recommendations").path("items")
                .path("properties").path("outfit_alias").path("enum").toString()).contains("O1");
        assertThat(schema.toString()).contains("O1_TOP", "O1_BOTTOM", "required");
        assertThat(config.has("responseMimeType")).isFalse();
        assertThat(config.has("responseSchema")).isFalse();
    }

    @Test
    void simpleCountTokensUsesDeveloperApiContentsEnvelopeWithoutDroppingProductionInputsElsewhere() {
        JsonNode result = client(Duration.ofSeconds(2)).countGenerationContentTokens(
                "Synthetic minimal countTokens probe", Duration.ofSeconds(2));
        assertThat(result.path("totalTokens").asInt()).isEqualTo(220);
        Captured call = captured.getFirst();
        assertThat(call.path()).isEqualTo("/v1beta/models/gemini-3.5-flash-lite:countTokens");
        assertThat(fieldNames(call.body())).containsExactly("contents");
        assertThat(call.body().path("contents").get(0).path("role").asText()).isEqualTo("user");
        assertThat(call.body().path("contents").get(0).path("parts").get(0).path("text").asText())
                .isEqualTo("Synthetic minimal countTokens probe");
    }

    @Test
    void upstreamErrorDiagnosticNeverEntersPublicProblemDetails() {
        generationCountStatus.set(400);
        var failure = org.assertj.core.api.Assertions.catchThrowable(() -> model().select(
                context("synthetic-private-prompt"), outfits(), List.of(), Duration.ofSeconds(10)));
        assertThat(failure).isInstanceOf(StylistApiException.class);
        var response = new ApiExceptionHandler().stylistError((StylistApiException) failure,
                new MockHttpServletRequest("POST", "/api/v1/stylist/requests/synthetic/generate"));
        String publicJson = json.valueToTree(response.getBody()).toString();
        assertThat(publicJson).contains("GENERATION_PROVIDER_PROTOCOL_ERROR")
                .doesNotContain(FIXTURE_KEY, "synthetic-private-prompt", "upstream-secret-value",
                        "SENSITIVE_UPSTREAM_BODY", "fieldViolations", "INVALID_ARGUMENT");
    }

    @Test
    void requestTimeoutAlsoBoundsAResponseBodyThatStallsAfterHeaders() {
        generationBodyDelayMillis.set(250);
        ObjectNode request = json.createObjectNode();
        request.putArray("contents").addObject().put("role", "user").putArray("parts")
                .addObject().put("text", "synthetic minimal request");
        assertApiError(() -> client(Duration.ofMillis(100)).generateContent(request, Duration.ofMillis(500)),
                ErrorCode.GENERATION_PROVIDER_TIMEOUT);
        assertThat(captured).hasSize(1);
        assertThat(captured.getFirst().path()).isEqualTo("/v1beta/models/gemini-3.5-flash-lite:generateContent");
    }

    @Test
    void inputAboveSixThousandProviderTokensStopsBeforeGeneration() {
        generationTokens.set(GeminiStructuredRecommendationModel.MAX_INPUT_TOKENS + 1);

        assertApiError(() -> model().select(context("synthetic"), outfits(), List.of(), Duration.ofSeconds(10)),
                ErrorCode.GENERATION_INPUT_TOO_LARGE);
        assertThat(captured).extracting(Captured::path)
                .containsExactly("/v1beta/models/gemini-3.5-flash-lite:countTokens");
    }

    @Test
    void structuredRefusalMalformedJsonUnexpectedFinishAndNonWhitelistedAliasAreRejected() {
        for (var example : List.of(
                new OutputCase("safety", ErrorCode.GENERATION_PROVIDER_REFUSED),
                new OutputCase("prompt-block", ErrorCode.GENERATION_PROVIDER_REFUSED),
                new OutputCase("no-candidate", ErrorCode.GENERATION_PROVIDER_PROTOCOL_ERROR),
                new OutputCase("truncated", ErrorCode.GENERATION_PROVIDER_PROTOCOL_ERROR),
                new OutputCase("malformed", ErrorCode.GENERATION_PROVIDER_PROTOCOL_ERROR),
                new OutputCase("trailing-json", ErrorCode.GENERATION_PROVIDER_PROTOCOL_ERROR),
                new OutputCase("duplicate-key", ErrorCode.GENERATION_PROVIDER_PROTOCOL_ERROR),
                new OutputCase("unknown-alias", ErrorCode.GENERATION_PROVIDER_PROTOCOL_ERROR))) {
            captured.clear();
            generationOutput.set(example.output());
            assertApiError(() -> model().select(context("synthetic"), outfits(), List.of(), Duration.ofSeconds(10)),
                    example.error());
            assertThat(captured).extracting(Captured::path).containsExactly(
                    "/v1beta/models/gemini-3.5-flash-lite:countTokens",
                    "/v1beta/models/gemini-3.5-flash-lite:generateContent");
        }
    }

    @Test
    void providerErrorsAndTimeoutsAreNotRetriedAndNeverExposeBodyOrApiKey() {
        for (int status : List.of(401, 403, 429, 503)) {
            captured.clear();
            generationStatus.set(status);
            assertThatThrownBy(() -> model().select(context("synthetic"), outfits(), List.of(), Duration.ofSeconds(10)))
                    .isInstanceOf(StylistApiException.class)
                    .satisfies(failure -> {
                        assertThat(((StylistApiException) failure).errorCode())
                                .isEqualTo(ErrorCode.GENERATION_PROVIDER_UNAVAILABLE);
                        assertThat(failure.toString()).doesNotContain(FIXTURE_KEY, "SENSITIVE_UPSTREAM_BODY",
                                "synthetic-private-prompt", "upstream-secret-value");
                    });
            assertThat(captured).extracting(Captured::path).containsExactly(
                    "/v1beta/models/gemini-3.5-flash-lite:countTokens",
                    "/v1beta/models/gemini-3.5-flash-lite:generateContent");
        }

        captured.clear();
        generationStatus.set(200);
        generationDelayMillis.set(300);
        assertApiError(() -> model(Duration.ofMillis(80)).select(context("synthetic"), outfits(), List.of(),
                Duration.ofSeconds(10)), ErrorCode.GENERATION_PROVIDER_TIMEOUT);
        assertThat(captured).extracting(Captured::path).containsExactly(
                "/v1beta/models/gemini-3.5-flash-lite:countTokens",
                "/v1beta/models/gemini-3.5-flash-lite:generateContent");
    }

    @Test
    void disabledLiveCallsDoNotSendEvenWhenAKeyIsConfigured() {
        GeminiDeveloperApiClient client = client(Duration.ofSeconds(2), false);
        assertThat(client.isConfigured()).isFalse();
        assertApiError(() -> client.countEmbeddingTokens("synthetic", Duration.ofSeconds(1)),
                ErrorCode.GENERATION_RUNTIME_UNCONFIGURED);
        assertThat(captured).isEmpty();
    }

    @Test
    void embeddingMatchesOfficialDeveloperApiWireContractAndNormalizes1536Dimensions() throws Exception {
        GeminiRuntimeEmbeddingAdapter embeddings = embeddings();
        float[] query = embeddings.embedQuery("synthetic retrieval query");
        float[] document = embeddings.embedDocument("synthetic catalog top");

        assertThat(query).hasSize(1_536);
        assertThat(document).hasSize(1_536);
        assertThat(Math.sqrt(java.util.Arrays.stream(toDouble(query)).map(value -> value * value).sum()))
                .isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.00001));
        assertThat(Math.sqrt(java.util.Arrays.stream(toDouble(document)).map(value -> value * value).sum()))
                .isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.00001));
        List<Captured> embedCalls = captured.stream().filter(value -> value.path().endsWith(":batchEmbedContents")).toList();
        assertThat(embedCalls).hasSize(2);
        // Golden request reflects the synchronous batch wire schema used by the official Java SDK's MLDV converter.
        JsonNode contract = json.readTree(getClass().getResourceAsStream(
                "/contracts/gemini-developer-v1beta-batch-embed-contents.json"));
        String endpoint = contract.path("endpoint").asText();
        ObjectNode expectedQuery = ((ObjectNode) contract.path("body").path("requests").get(0)).deepCopy();
        ((ObjectNode) expectedQuery.with("content").withArray("parts").get(0))
                .put("text", "synthetic retrieval query");
        ObjectNode expectedDocument = ((ObjectNode) contract.path("body").path("requests").get(0)).deepCopy();
        ((ObjectNode) expectedDocument.with("content").withArray("parts").get(0))
                .put("text", "synthetic catalog top");
        expectedDocument.put("taskType", "RETRIEVAL_DOCUMENT");

        assertThat(embedCalls.get(0).path()).isEqualTo(endpoint);
        assertThat(embedCalls.get(0).body().path("requests").get(0)).isEqualTo(expectedQuery);
        assertThat(embedCalls.get(1).path()).isEqualTo(endpoint);
        assertThat(embedCalls.get(1).body().path("requests").get(0)).isEqualTo(expectedDocument);
        for (Captured call : embedCalls) {
            JsonNode requests = call.body().path("requests");
            assertThat(requests.isArray()).isTrue();
            assertThat(requests.size()).isEqualTo(1);
            JsonNode item = requests.get(0);
            assertThat(item.path("outputDimensionality").asInt()).isEqualTo(1_536);
            assertThat(call.body().has("autoTruncate")).isFalse();
            assertThat(item.has("autoTruncate")).isFalse();
            assertThat(fieldNames(call.body())).containsExactly("requests");
            assertThat(fieldNames(item)).containsExactlyInAnyOrder("model", "content", "taskType", "outputDimensionality");
            assertThat(item.path("content").path("parts").get(0).path("text").asText()).isNotBlank();
        }
        assertThat(captured.stream().filter(value -> value.path().endsWith(":countTokens"))
                .map(value -> value.body().path("contents").get(0).path("role").asText()).toList())
                .containsExactly("user", "user");
    }

    @Test
    void embeddingInputLimitAndInvalidVectorsFailWithoutTruncationOrRetries() {
        embeddingTokens.set(GeminiDeveloperApiClient.EMBEDDING_MAX_COUNT_TOKENS + 1);
        assertApiError(() -> embeddings().embedQuery("synthetic oversized input"), ErrorCode.GENERATION_INPUT_TOO_LARGE);
        assertThat(captured).extracting(Captured::path)
                .containsExactly("/v1beta/models/gemini-embedding-001:countTokens");

        for (String mode : List.of("wrong-dimension", "default-dimension", "nan", "zero")) {
            captured.clear();
            embeddingTokens.set(10);
            vectorOutput.set(mode);
            assertApiError(() -> embeddings().embedQuery("synthetic query"),
                    ErrorCode.GENERATION_PROVIDER_PROTOCOL_ERROR);
            assertThat(captured).extracting(Captured::path).containsExactly(
                    "/v1beta/models/gemini-embedding-001:countTokens",
                    "/v1beta/models/gemini-embedding-001:batchEmbedContents");
        }
    }

    @Test
    void embeddingProviderErrorsAreSafeAndNotRetried() {
        embeddingTokens.set(10);
        for (int status : List.of(401, 403, 429, 503)) {
            captured.clear();
            embeddingStatus.set(status);
            assertThatThrownBy(() -> embeddings().embedDocument("synthetic top"))
                    .isInstanceOf(StylistApiException.class)
                    .satisfies(failure -> {
                        assertThat(((StylistApiException) failure).errorCode())
                                .isEqualTo(ErrorCode.GENERATION_PROVIDER_UNAVAILABLE);
                        assertThat(failure.toString()).doesNotContain(FIXTURE_KEY, "SENSITIVE_UPSTREAM_BODY");
                    });
            assertThat(captured).extracting(Captured::path).containsExactly(
                    "/v1beta/models/gemini-embedding-001:countTokens",
                    "/v1beta/models/gemini-embedding-001:batchEmbedContents");
        }
    }

    private GeminiStructuredRecommendationModel model() { return model(Duration.ofSeconds(2)); }

    private GeminiStructuredRecommendationModel model(Duration timeout) {
        return new GeminiStructuredRecommendationModel(client(timeout), json);
    }

    private GeminiStructuredRecommendationModel model(String generationModel) {
        return new GeminiStructuredRecommendationModel(client(Duration.ofSeconds(10), true, generationModel), json);
    }

    private GeminiRuntimeEmbeddingAdapter embeddings() {
        return new GeminiRuntimeEmbeddingAdapter(client(Duration.ofSeconds(2)), json);
    }

    private GeminiDeveloperApiClient client(Duration timeout) {
        return client(timeout, true);
    }

    private GeminiDeveloperApiClient client(Duration timeout, boolean externalCallsEnabled) {
        return client(timeout, externalCallsEnabled, GeminiDeveloperApiClient.GENERATION_MODEL);
    }

    private GeminiDeveloperApiClient client(Duration timeout, boolean externalCallsEnabled, String generationModel) {
        return new GeminiDeveloperApiClient(baseUrl(), FIXTURE_KEY, json, Duration.ofSeconds(1), timeout,
                externalCallsEnabled, null, generationModel);
    }

    private String baseUrl() { return "http://127.0.0.1:" + server.getAddress().getPort(); }

    private void respond(HttpExchange exchange) throws IOException {
        JsonNode request = json.readTree(exchange.getRequestBody().readAllBytes());
        String path = exchange.getRequestURI().getPath();
        captured.add(new Captured(path, request, exchange.getRequestHeaders().getFirst("x-goog-api-key") != null,
                exchange.getRequestHeaders().getFirst("Authorization") != null));
        int status = path.endsWith("gemini-embedding-001:countTokens") ? embeddingCountStatus.get()
                : path.endsWith("gemini-embedding-001:batchEmbedContents") ? embeddingStatus.get()
                : path.endsWith("gemini-3.5-flash-lite:countTokens")
                    ? generationCountStatus.get()
                : path.endsWith("gemini-3.5-flash-lite:generateContent") ? generationStatus.get() : 404;
        if (path.endsWith(":generateContent") && generationDelayMillis.get() > 0) {
            try { Thread.sleep(generationDelayMillis.get()); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
        }
        String body;
        if (status != 200) body = "{\"error\":{\"code\":400,\"status\":\"INVALID_ARGUMENT\","
                + "\"message\":\"synthetic-private-prompt upstream-secret-value " + FIXTURE_KEY + "\","
                + "\"details\":[{\"fieldViolations\":[{\"field\":\"generateContentRequest.generationConfig.responseFormat\","
                + "\"description\":\"SENSITIVE_UPSTREAM_BODY\"}]}]}}";
        else if (path.endsWith("gemini-embedding-001:countTokens")) {
            body = "{\"totalTokens\":" + embeddingTokens.get() + "}";
        } else if (path.endsWith("gemini-3.5-flash-lite:countTokens")) {
            body = "{\"totalTokens\":" + generationTokens.get() + "}";
        } else if (path.endsWith("gemini-embedding-001:batchEmbedContents")) {
            body = embeddingResponse();
        } else {
            body = generationResponse();
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        try {
            exchange.sendResponseHeaders(status, bytes.length);
            if (path.endsWith(":generateContent") && generationBodyDelayMillis.get() > 0) {
                Thread.sleep(generationBodyDelayMillis.get());
            }
            try (var response = exchange.getResponseBody()) { response.write(bytes); }
        } catch (IOException ignored) {
            // Expected when a timeout test closes its local fixture connection.
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private String embeddingResponse() {
        ObjectNode root = json.createObjectNode();
        ArrayNode values = root.putArray("embeddings").addObject().putArray("values");
        int dimensions = switch (vectorOutput.get()) {
            case "wrong-dimension" -> 1_535;
            case "default-dimension" -> 3_072;
            default -> 1_536;
        };
        for (int index = 0; index < dimensions; index++) {
            if ("zero".equals(vectorOutput.get())) values.add(0.0);
            else if ("nan".equals(vectorOutput.get())) values.add(Double.NaN);
            else values.add(index == 0 ? 2.0 : 0.5);
        }
        return root.toString();
    }

    private String generationResponse() {
        if ("prompt-block".equals(generationOutput.get())) {
            return "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"},\"candidates\":[]}";
        }
        if ("no-candidate".equals(generationOutput.get())) return "{\"candidates\":[]}";
        if ("safety".equals(generationOutput.get())) {
            return "{\"candidates\":[{\"finishReason\":\"SAFETY\",\"content\":{\"parts\":[]}}]}";
        }
        String finish = "truncated".equals(generationOutput.get()) ? "MAX_TOKENS" : "STOP";
        String output = switch (generationOutput.get()) {
            case "malformed" -> "{";
            case "trailing-json" -> validOutput("O1", "O1_TOP", "O1_BOTTOM") + " {}";
            case "duplicate-key" -> "{\"recommendations\":[],\"recommendations\":[]}";
            case "unknown-alias" -> validOutput("O9", "O1_TOP", "O1_BOTTOM");
            default -> validOutput("O1", "O1_TOP", "O1_BOTTOM");
        };
        ObjectNode root = json.createObjectNode();
        root.putArray("candidates").addObject().put("finishReason", finish).putObject("content")
                .putArray("parts").addObject().put("text", output);
        root.putObject("usageMetadata").put("promptTokenCount", generationTokens.get())
                .put("candidatesTokenCount", 80).put("thoughtsTokenCount", 0);
        return root.toString();
    }

    private String validOutput(String outfitAlias, String topAlias, String bottomAlias) {
        ObjectNode root = json.createObjectNode();
        ObjectNode recommendation = root.putArray("recommendations").addObject();
        recommendation.put("outfit_alias", outfitAlias).put("explanation", "The listed pieces fit the request.")
                .put("styling_tips", "Keep the colors balanced.");
        ArrayNode reasons = recommendation.putArray("item_reasons");
        reasons.addObject().put("item_alias", topAlias).put("reason", "The top matches the request.");
        reasons.addObject().put("item_alias", bottomAlias).put("reason", "The bottom completes the outfit.");
        return root.toString();
    }

    private static void assertApiError(Runnable call, ErrorCode errorCode) {
        assertThatThrownBy(call::run).isInstanceOf(StylistApiException.class)
                .satisfies(failure -> assertThat(((StylistApiException) failure).errorCode()).isEqualTo(errorCode));
    }

    private static double[] toDouble(float[] vector) {
        double[] values = new double[vector.length];
        for (int index = 0; index < vector.length; index++) values[index] = vector[index];
        return values;
    }

    private static List<String> fieldNames(JsonNode object) {
        List<String> names = new ArrayList<>();
        Iterator<String> fields = object.fieldNames();
        fields.forEachRemaining(names::add);
        return names;
    }

    private static RequestContext context(String message) {
        return new RequestContext(UUID.randomUUID(), UUID.randomUUID(), "PROCESSING", message,
                "work", null, null, null, null, false, false, List.of());
    }

    private static List<FrozenOutfit> outfits() {
        UUID topVariant = UUID.randomUUID();
        UUID bottomVariant = UUID.randomUUID();
        return List.of(new FrozenOutfit("O1", null, List.of(
                new ItemAlias("O1_TOP", UUID.randomUUID(), topVariant, UUID.randomUUID(), "TOP",
                        "Synthetic blouse", "Top", "Blue", null, null),
                new ItemAlias("O1_BOTTOM", UUID.randomUUID(), bottomVariant, UUID.randomUUID(), "BOTTOM",
                        "Synthetic skirt", "Bottom", "Black", null, null))));
    }

    private record Captured(String path, JsonNode body, boolean apiKeyPresent, boolean authorizationHeaderPresent) { }
    private record OutputCase(String output, ErrorCode error) { }
}
