package com.stylemind.stylist.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylemind.stylist.service.generation.GeminiDeveloperApiClient;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "stylemind.ai.provider", havingValue = "gemini")
public class GeminiProviderConfiguration {
    @Bean
    @Profile("!offline-gemini-mock")
    @ConditionalOnProperty(name = "stylemind.gemini.mock.enabled", havingValue = "false", matchIfMissing = true)
    GeminiDeveloperApiClient geminiDeveloperApiClient(
            @Value("${stylemind.gemini.api-key:}") String apiKey,
            @Value("${stylemind.gemini.live-calls-enabled:false}") boolean liveCallsEnabled,
            @Value("${stylemind.gemini.generation-model:gemini-3.5-flash-lite}") String generationModel,
            @Value("${stylemind.gemini.meter.proxy-host:}") String meterProxyHost,
            @Value("${stylemind.gemini.meter.proxy-port:0}") int meterProxyPort,
            ObjectMapper objectMapper) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Gemini is selected but STYLEMIND_GEMINI_API_KEY is missing");
        }
        ProxySelector proxy = null;
        if (liveCallsEnabled) {
            if (!"127.0.0.1".equals(meterProxyHost) || meterProxyPort < 1 || meterProxyPort > 65_535) {
                throw new IllegalStateException("Gemini live calls require the local bounded meter proxy");
            }
            proxy = ProxySelector.of(new InetSocketAddress(meterProxyHost, meterProxyPort));
        }
        return new GeminiDeveloperApiClient(GeminiDeveloperApiClient.API_BASE_URL, apiKey, objectMapper,
                java.time.Duration.ofSeconds(2), java.time.Duration.ofSeconds(30), liveCallsEnabled, proxy,
                generationModel);
    }

    @Bean
    @Profile("offline-gemini-mock")
    @ConditionalOnProperty(name = "stylemind.gemini.mock.enabled", havingValue = "true")
    GeminiDeveloperApiClient offlineGeminiMockClient(
            @Value("${stylemind.gemini.mock.base-url:}") String baseUrl,
            @Value("${stylemind.gemini.live-calls-enabled:false}") boolean liveCallsEnabled,
            @Value("${stylemind.gemini.generation-model:gemini-3.5-flash-lite}") String generationModel,
            ObjectMapper objectMapper) {
        if (liveCallsEnabled) {
            throw new IllegalStateException("Offline Gemini mock cannot be combined with live calls");
        }
        URI endpoint;
        try {
            endpoint = URI.create(baseUrl == null ? "" : baseUrl.trim());
        } catch (IllegalArgumentException exception) {
            throw invalidMockEndpoint();
        }
        if (!"http".equalsIgnoreCase(endpoint.getScheme())
                || !"127.0.0.1".equals(endpoint.getHost())
                || endpoint.getPort() < 1 || endpoint.getPort() > 65_535
                || endpoint.getRawUserInfo() != null || endpoint.getRawQuery() != null
                || endpoint.getRawFragment() != null
                || (endpoint.getRawPath() != null && !endpoint.getRawPath().isEmpty())) {
            throw invalidMockEndpoint();
        }
        return new GeminiDeveloperApiClient(endpoint.toString(), "offline-gemini-http-fixture", objectMapper,
                java.time.Duration.ofSeconds(2), java.time.Duration.ofSeconds(30), true, null, generationModel);
    }

    private static IllegalStateException invalidMockEndpoint() {
        return new IllegalStateException("Offline Gemini mock requires an explicit 127.0.0.1 HTTP origin and port");
    }
}
