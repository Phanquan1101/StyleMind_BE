package com.stylemind.stylist.config;

import java.net.http.HttpClient;
import java.time.Duration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylemind.stylist.service.generation.OpenAiResponsesClient;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.document.MetadataMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.retry.support.RetryTemplate;

@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression("'${stylemind.ai.provider:openai}' == 'openai' && '${stylemind.openai.enabled:false}' == 'true'")
public class OpenAiModelConfiguration {
    @Bean
    OpenAiApi stylistOpenAiApi(@Value("${stylemind.openai.api-key:}") String apiKey,
                               @Value("${stylemind.openai.base-url:https://api.openai.com}") String baseUrl) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OpenAI is enabled but its environment key is missing");
        }
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(client);
        requestFactory.setReadTimeout(Duration.ofSeconds(8));
        RestClient.Builder restClient = RestClient.builder().requestFactory(requestFactory);
        return OpenAiApi.builder().baseUrl(baseUrl).apiKey(apiKey).restClientBuilder(restClient)
                .responseErrorHandler(new DefaultResponseErrorHandler()).build();
    }

    @Bean
    OpenAiResponsesClient stylistOpenAiResponsesClient(
            @Value("${stylemind.openai.api-key:}") String apiKey,
            @Value("${stylemind.openai.base-url:https://api.openai.com}") String baseUrl,
            ObjectMapper objectMapper) {
        return new OpenAiResponsesClient(baseUrl, apiKey, objectMapper, Duration.ofSeconds(2), Duration.ofSeconds(8));
    }

    @Bean
    OpenAiEmbeddingModel stylistOpenAiEmbeddingModel(OpenAiApi api) {
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model("text-embedding-3-small").encodingFormat("float").dimensions(1_536).build();
        return new OpenAiEmbeddingModel(api, MetadataMode.EMBED, options,
                RetryTemplate.builder().maxAttempts(1).build());
    }
}
