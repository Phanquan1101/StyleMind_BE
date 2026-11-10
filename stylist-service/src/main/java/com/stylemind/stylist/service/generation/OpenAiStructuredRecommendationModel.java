package com.stylemind.stylist.service.generation;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.stylemind.stylist.errors.ErrorCode;
import com.stylemind.stylist.errors.StylistApiException;
import com.stylemind.stylist.model.generation.GenerationModel.FrozenOutfit;
import com.stylemind.stylist.model.generation.GenerationModel.ModelOutput;
import com.stylemind.stylist.model.generation.GenerationModel.RequestContext;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** One bounded Spring AI adapter using OpenAI-native JSON Schema strict response format and no tools. */
@Component
@ConditionalOnProperty(name = "stylemind.ai.provider", havingValue = "openai", matchIfMissing = true)
public class OpenAiStructuredRecommendationModel implements StructuredRecommendationModel {
    public static final String MODEL = "gpt-5.4-mini-2026-03-17";
    public static final int MAX_INPUT_TOKENS = 6_000;
    public static final int MAX_OUTPUT_TOKENS = 2_000;
    private static final String FORMAT_NAME = "stylemind_outfit_selection";
    private static final String SYSTEM = StructuredRecommendationContract.SYSTEM;

    private final ObjectProvider<OpenAiResponsesClient> responsesProvider;
    private final ObjectMapper json;
    private final boolean enabled;

    public OpenAiStructuredRecommendationModel(ObjectProvider<OpenAiResponsesClient> responsesProvider,
            @Value("${stylemind.openai.enabled:false}") boolean enabled) {
        this.responsesProvider = responsesProvider;
        this.enabled = enabled;
        this.json = JsonMapper.builder(JsonFactory.builder()
                        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    }

    @Override
    public ModelOutput select(RequestContext context, List<FrozenOutfit> whitelist,
                              List<String> knowledgeFacts, Duration remaining) {
        if (!isConfigured()) throw new StylistApiException(ErrorCode.GENERATION_RUNTIME_UNCONFIGURED);
        if (whitelist == null || whitelist.isEmpty() || whitelist.size() > 3) {
            throw new StylistApiException(ErrorCode.GENERATION_PROVIDER_PROTOCOL_ERROR);
        }
        if (remaining == null || remaining.compareTo(Duration.ofSeconds(8)) < 0) {
            throw new StylistApiException(ErrorCode.GENERATION_PROVIDER_TIMEOUT);
        }
        try {
            String userData = userData(context, whitelist, knowledgeFacts);
            String schema = schema(whitelist);
            long deadline = System.nanoTime() + remaining.toNanos();
            ObjectNode countPayload = requestPayload(userData, schema);
            OpenAiResponsesClient client = responsesProvider.getObject();
            JsonNode tokenCount = client.countInputTokens(countPayload, remaining(deadline));
            int exactInputTokens = tokenCount.path("input_tokens").asInt(-1);
            if (!"response.input_tokens".equals(tokenCount.path("object").asText()) || exactInputTokens < 0) {
                throw new StylistApiException(ErrorCode.GENERATION_PROVIDER_PROTOCOL_ERROR);
            }
            if (exactInputTokens > MAX_INPUT_TOKENS) {
                throw new StylistApiException(ErrorCode.GENERATION_INPUT_TOO_LARGE);
            }
            ObjectNode generationPayload = countPayload.deepCopy();
            generationPayload.put("store", false).put("max_output_tokens", MAX_OUTPUT_TOKENS);
            JsonNode response = client.createResponse(generationPayload, remaining(deadline));
            return parseResponse(response, whitelist);
        } catch (StylistApiException exception) {
            throw exception;
        } catch (JsonProcessingException exception) {
            throw new StylistApiException(ErrorCode.GENERATION_PROVIDER_PROTOCOL_ERROR);
        } catch (RuntimeException exception) {
            throw OpenAiFailureMapper.map(exception);
        }
    }

    @Override
    public boolean isConfigured() { return enabled && responsesProvider.getIfAvailable() != null; }

    private ObjectNode requestPayload(String userData, String schema) throws JsonProcessingException {
        ObjectNode payload = json.createObjectNode().put("model", MODEL).put("instructions", SYSTEM)
                .put("input", userData);
        ObjectNode format = payload.putObject("text").putObject("format");
        format.put("type", "json_schema").put("name", FORMAT_NAME).put("strict", true);
        format.set("schema", json.readTree(schema));
        return payload;
    }

    private String userData(RequestContext context, List<FrozenOutfit> outfits, List<String> facts)
            throws JsonProcessingException {
        return StructuredRecommendationContract.userData(json, context, outfits, facts);
    }

    private String schema(List<FrozenOutfit> outfits) {
        return StructuredRecommendationContract.schema(json, outfits, true).toString();
    }

    private void addStringEnum(ObjectNode node, List<String> values) {
        node.put("type", "string");
        node.set("enum", json.valueToTree(values));
    }

    private ModelOutput parseResponse(JsonNode response, List<FrozenOutfit> whitelist) {
        if (response == null || !response.isObject()) throw invalidOutput();
        String status = response.path("status").asText();
        if ("incomplete".equals(status)) throw invalidOutput();
        if (!"completed".equals(status)) throw invalidOutput();
        JsonNode outputItems = response.path("output");
        if (!outputItems.isArray()) throw invalidOutput();
        String content = null;
        for (JsonNode outputItem : outputItems) {
            if (!"message".equals(outputItem.path("type").asText())) continue;
            JsonNode contentItems = outputItem.path("content");
            if (!contentItems.isArray()) throw invalidOutput();
            for (JsonNode contentItem : contentItems) {
                String type = contentItem.path("type").asText();
                if ("refusal".equals(type)) {
                    throw new StylistApiException(ErrorCode.GENERATION_PROVIDER_REFUSED);
                }
                if ("output_text".equals(type)) {
                    if (content != null || !contentItem.path("text").isTextual()) throw invalidOutput();
                    content = contentItem.path("text").textValue();
                }
            }
        }
        if (content == null || content.isBlank()) throw invalidOutput();
        return StructuredRecommendationContract.parse(content, whitelist);
    }

    private static StylistApiException invalidOutput() {
        return new StylistApiException(ErrorCode.GENERATION_PROVIDER_PROTOCOL_ERROR);
    }

    private static Duration remaining(long deadline) {
        long nanos = deadline - System.nanoTime();
        if (nanos <= 0) throw new StylistApiException(ErrorCode.GENERATION_PROVIDER_TIMEOUT);
        return Duration.ofNanos(nanos);
    }
}
