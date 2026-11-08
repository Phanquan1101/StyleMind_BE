package com.stylemind.stylist.service.generation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stylemind.stylist.errors.ErrorCode;
import com.stylemind.stylist.errors.StylistApiException;
import com.stylemind.stylist.model.generation.GenerationModel.FrozenOutfit;
import com.stylemind.stylist.model.generation.GenerationModel.ModelOutput;
import com.stylemind.stylist.model.generation.GenerationModel.RequestContext;
import java.io.IOException;
import java.time.Duration;
import java.util.Iterator;
import java.util.Set;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "stylemind.ai.provider", havingValue = "gemini")
public class GeminiStructuredRecommendationModel implements StructuredRecommendationModel {
    public static final String MODEL = GeminiDeveloperApiClient.GENERATION_MODEL;
    public static final int MAX_INPUT_TOKENS = 6_000;
    public static final int MAX_OUTPUT_TOKENS = 2_000;
    private static final String FORMAT_NAME = "stylemind_outfit_selection";

    private final GeminiDeveloperApiClient client;
    private final ObjectMapper json;

    public GeminiStructuredRecommendationModel(GeminiDeveloperApiClient client, ObjectMapper json) {
        this.client = client;
        this.json = json;
    }

    @Override
    public ModelOutput select(RequestContext context, List<FrozenOutfit> whitelist,
                              List<String> knowledgeFacts, Duration remaining) {
        if (!isConfigured()) throw new StylistApiException(ErrorCode.GENERATION_RUNTIME_UNCONFIGURED);
        if (whitelist == null || whitelist.isEmpty() || whitelist.size() > 3) throw protocol();
        if (remaining == null || remaining.compareTo(Duration.ofSeconds(8)) < 0) {
            throw new StylistApiException(ErrorCode.GENERATION_PROVIDER_TIMEOUT);
        }
        try {
            String userData = StructuredRecommendationContract.userData(json, context, whitelist, knowledgeFacts);
            ObjectNode request = requestFor(userData, whitelist);
            long deadline = System.nanoTime() + remaining.toNanos();
            JsonNode count = client.countGenerationTokens(request, remaining(deadline));
            int tokenCount = count.path("totalTokens").asInt(-1);
            if (tokenCount < 0) throw protocol();
            if (tokenCount > MAX_INPUT_TOKENS) {
                throw new StylistApiException(ErrorCode.GENERATION_INPUT_TOO_LARGE);
            }
            JsonNode response = client.generateContent(request, remaining(deadline));
            return StructuredRecommendationContract.parse(responseText(response), whitelist);
        } catch (StylistApiException exception) {
            throw exception;
        } catch (IOException | IllegalArgumentException exception) {
            throw protocol();
        }
    }

    @Override public boolean isConfigured() { return client != null && client.isConfigured(); }

    ObjectNode requestFor(String userData, List<FrozenOutfit> whitelist) {
        ObjectNode request = json.createObjectNode();
        ObjectNode instruction = request.putObject("systemInstruction");
        instruction.putArray("parts").addObject().put("text", StructuredRecommendationContract.SYSTEM);
        ArrayNode contents = request.putArray("contents");
        contents.addObject().put("role", "user").putArray("parts").addObject().put("text", userData);
        ObjectNode generation = request.putObject("generationConfig");
        generation.put("candidateCount", 1).put("maxOutputTokens", MAX_OUTPUT_TOKENS);
        if (GeminiDeveloperApiClient.GEMINI_3_5_FLASH_LITE.equals(client.generationModel())) {
            generation.putObject("thinkingConfig").put("thinkingLevel", "MINIMAL");
            ObjectNode textFormat = generation.putObject("responseFormat").putObject("text");
            textFormat.put("mimeType", "APPLICATION_JSON");
            textFormat.set("schema", StructuredRecommendationContract.schema(json, whitelist, false));
        } else {
            generation.putObject("thinkingConfig").put("thinkingBudget", 0);
            generation.put("responseMimeType", "application/json");
            generation.set("responseSchema", geminiResponseSchema(
                    StructuredRecommendationContract.schema(json, whitelist, false)));
        }
        return request;
    }

    private ObjectNode geminiResponseSchema(ObjectNode source) {
        ObjectNode target = json.createObjectNode();
        Iterator<String> fields = source.fieldNames();
        Set<String> supported = Set.of("type", "properties", "items", "enum", "required", "minItems", "maxItems");
        while (fields.hasNext()) {
            String field = fields.next();
            if ("additionalProperties".equals(field)) continue;
            if (!supported.contains(field)) throw protocol();
            JsonNode value = source.get(field);
            if ("type".equals(field)) {
                target.put(field, value.asText().toUpperCase(java.util.Locale.ROOT));
            } else if ("properties".equals(field) && value.isObject()) {
                ObjectNode properties = target.putObject(field);
                value.fields().forEachRemaining(entry -> properties.set(entry.getKey(),
                        geminiResponseSchema((ObjectNode) entry.getValue())));
            } else if ("items".equals(field) && value.isObject()) {
                target.set(field, geminiResponseSchema((ObjectNode) value));
            } else {
                target.set(field, value.deepCopy());
            }
        }
        return target;
    }

    private static String responseText(JsonNode response) {
        if (response == null || !response.isObject()) throw protocol();
        JsonNode blockReason = response.path("promptFeedback").path("blockReason");
        if (blockReason.isTextual() && !blockReason.asText().isBlank()
                && !"BLOCK_REASON_UNSPECIFIED".equals(blockReason.asText())) throw refused();
        JsonNode candidates = response.path("candidates");
        if (!candidates.isArray() || candidates.size() != 1) throw protocol();
        JsonNode candidate = candidates.get(0);
        String finishReason = candidate.path("finishReason").asText();
        if ("SAFETY".equals(finishReason) || "RECITATION".equals(finishReason)
                || "BLOCKLIST".equals(finishReason) || "PROHIBITED_CONTENT".equals(finishReason)
                || "SPII".equals(finishReason)) throw refused();
        if (!"STOP".equals(finishReason)) throw protocol();
        JsonNode parts = candidate.path("content").path("parts");
        if (!parts.isArray() || parts.isEmpty()) throw protocol();
        StringBuilder content = new StringBuilder();
        for (JsonNode part : parts) {
            JsonNode text = part.get("text");
            if (text == null || !text.isTextual() || part.path("thought").asBoolean(false)) throw protocol();
            content.append(text.textValue());
        }
        if (content.isEmpty()) throw protocol();
        return content.toString();
    }

    private static Duration remaining(long deadline) {
        long nanos = deadline - System.nanoTime();
        if (nanos <= 0) throw new StylistApiException(ErrorCode.GENERATION_PROVIDER_TIMEOUT);
        return Duration.ofNanos(nanos);
    }

    private static StylistApiException refused() {
        return new StylistApiException(ErrorCode.GENERATION_PROVIDER_REFUSED);
    }

    private static StylistApiException protocol() {
        return new StylistApiException(ErrorCode.GENERATION_PROVIDER_PROTOCOL_ERROR);
    }
}
