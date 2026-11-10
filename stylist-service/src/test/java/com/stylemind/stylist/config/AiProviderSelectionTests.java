package com.stylemind.stylist.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylemind.stylist.service.generation.GeminiRuntimeEmbeddingAdapter;
import com.stylemind.stylist.service.generation.GeminiDeveloperApiClient;
import com.stylemind.stylist.service.generation.GeminiStructuredRecommendationModel;
import com.stylemind.stylist.service.generation.OpenAiRuntimeEmbeddingAdapter;
import com.stylemind.stylist.service.generation.OpenAiStructuredRecommendationModel;
import com.stylemind.stylist.service.generation.StructuredRecommendationModel;
import com.stylemind.stylist.service.retrieval.RuntimeEmbeddingPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class AiProviderSelectionTests {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ProviderConfiguration.class);

    @Test
    void geminiMustBeSelectedExplicitlyAndCreatesOnlyGeminiRuntimeAdapters() {
        contextRunner.withPropertyValues(
                "stylemind.ai.provider=gemini",
                "stylemind.gemini.api-key=local-fixture-key")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(GeminiRuntimeEmbeddingAdapter.class);
                    assertThat(context).hasSingleBean(GeminiStructuredRecommendationModel.class);
                    assertThat(context).doesNotHaveBean(OpenAiRuntimeEmbeddingAdapter.class);
                    assertThat(context).doesNotHaveBean(OpenAiStructuredRecommendationModel.class);
                    assertThat(context).hasSingleBean(RuntimeEmbeddingPort.class);
                    assertThat(context).hasSingleBean(StructuredRecommendationModel.class);
                    assertThat(context.getBean(GeminiRuntimeEmbeddingAdapter.class).isConfigured()).isFalse();
                    assertThat(context.getBean(GeminiStructuredRecommendationModel.class).isConfigured()).isFalse();
                });
    }

    @Test
    void geminiLiveCallsRequireASeparateExplicitOptIn() {
        contextRunner.withPropertyValues(
                "stylemind.ai.provider=gemini",
                "stylemind.gemini.api-key=local-fixture-key",
                "stylemind.gemini.live-calls-enabled=true",
                "stylemind.gemini.meter.proxy-host=127.0.0.1",
                "stylemind.gemini.meter.proxy-port=45123")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(GeminiRuntimeEmbeddingAdapter.class).isConfigured()).isTrue();
                    assertThat(context.getBean(GeminiStructuredRecommendationModel.class).isConfigured()).isTrue();
                });
    }

    @Test
    void offlineGeminiMockUsesLoopbackWithoutReadingOrRequiringAnApiKey() {
        contextRunner.withInitializer(context ->
                        context.getEnvironment().setActiveProfiles("offline-gemini-mock"))
                .withPropertyValues(
                        "stylemind.ai.provider=gemini",
                        "stylemind.gemini.mock.enabled=true",
                        "stylemind.gemini.mock.base-url=http://127.0.0.1:45123",
                        "stylemind.gemini.live-calls-enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(GeminiDeveloperApiClient.class);
                    assertThat(context.getBean(GeminiDeveloperApiClient.class)
                            .isConfigured()).isTrue();
                    assertThat(context.getBean(GeminiRuntimeEmbeddingAdapter.class).isConfigured()).isTrue();
                    assertThat(context.getBean(GeminiStructuredRecommendationModel.class).isConfigured()).isTrue();
                });
    }

    @Test
    void offlineGeminiMockRejectsNonLoopbackEndpoints() {
        contextRunner.withInitializer(context ->
                        context.getEnvironment().setActiveProfiles("offline-gemini-mock"))
                .withPropertyValues(
                        "stylemind.ai.provider=gemini",
                        "stylemind.gemini.mock.enabled=true",
                        "stylemind.gemini.mock.base-url=http://example.com:45123")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure()
                        .hasRootCauseMessage("Offline Gemini mock requires an explicit 127.0.0.1 HTTP origin and port"));
    }

    @Test
    void offlineGeminiMockCannotBeCombinedWithLiveCalls() {
        contextRunner.withInitializer(context ->
                        context.getEnvironment().setActiveProfiles("offline-gemini-mock"))
                .withPropertyValues(
                        "stylemind.ai.provider=gemini",
                        "stylemind.gemini.mock.enabled=true",
                        "stylemind.gemini.mock.base-url=http://127.0.0.1:45123",
                        "stylemind.gemini.live-calls-enabled=true")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure()
                        .hasRootCauseMessage("Offline Gemini mock cannot be combined with live calls"));
    }

    @Test
    void selectedGeminiWithoutKeyFailsClearly() {
        contextRunner.withPropertyValues("stylemind.ai.provider=gemini")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure()
                        .hasRootCauseMessage("Gemini is selected but STYLEMIND_GEMINI_API_KEY is missing"));
    }

    @Test
    void merelyHavingGeminiKeyDoesNotSelectGemini() {
        contextRunner.withPropertyValues(
                "stylemind.ai.provider=openai",
                "stylemind.gemini.api-key=local-fixture-key")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(GeminiRuntimeEmbeddingAdapter.class);
                    assertThat(context).doesNotHaveBean(GeminiStructuredRecommendationModel.class);
                    assertThat(context).hasSingleBean(OpenAiRuntimeEmbeddingAdapter.class);
                    assertThat(context).hasSingleBean(OpenAiStructuredRecommendationModel.class);
                    assertThat(context.getBean(OpenAiRuntimeEmbeddingAdapter.class).isConfigured()).isFalse();
                    assertThat(context.getBean(OpenAiStructuredRecommendationModel.class).isConfigured()).isFalse();
                });
    }

    @Test
    void unknownProviderFailsInsteadOfFallingBack() {
        contextRunner.withPropertyValues("stylemind.ai.provider=unexpected")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure()
                        .hasRootCauseMessage("stylemind.ai.provider must be explicitly set to openai or gemini"));
    }

    @Configuration(proxyBeanMethods = false)
    @Import({
            GeminiProviderConfiguration.class,
            GeminiRuntimeEmbeddingAdapter.class,
            GeminiStructuredRecommendationModel.class,
            OpenAiModelConfiguration.class,
            OpenAiRuntimeEmbeddingAdapter.class,
            OpenAiStructuredRecommendationModel.class,
            AiProviderSelectionValidator.class
    })
    static class ProviderConfiguration {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
