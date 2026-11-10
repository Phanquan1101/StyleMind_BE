package com.stylemind.stylist.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylemind.stylist.service.generation.GeminiDeveloperApiClient;
import org.junit.jupiter.api.Test;

class GeminiProviderConfigurationTests {
    @Test
    void liveProviderConfigurationCannotStartWithoutLocalMeterProxy() {
        GeminiProviderConfiguration configuration = new GeminiProviderConfiguration();
        assertThatThrownBy(() -> configuration.geminiDeveloperApiClient(
                "synthetic-only", true, GeminiDeveloperApiClient.GENERATION_MODEL, "", 0, new ObjectMapper()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Gemini live calls require the local bounded meter proxy");
    }

    @Test
    void defaultNonLiveConfigurationDoesNotRequireMeterProxy() {
        GeminiProviderConfiguration configuration = new GeminiProviderConfiguration();
        var client = configuration.geminiDeveloperApiClient(
                "synthetic-only", false, GeminiDeveloperApiClient.GENERATION_MODEL, "", 0, new ObjectMapper());
        assertThat(client.isConfigured()).isFalse();
        assertThat(client.generationModel()).isEqualTo(GeminiDeveloperApiClient.GENERATION_MODEL);
    }

    @Test
    void selectedFlashLiteIsAvailableOnlyAsAnExplicitLocalProviderSetting() {
        GeminiProviderConfiguration configuration = new GeminiProviderConfiguration();
        var client = configuration.geminiDeveloperApiClient(
                "synthetic-only", false, GeminiDeveloperApiClient.GEMINI_3_5_FLASH_LITE, "", 0, new ObjectMapper());
        assertThat(client.generationModel()).isEqualTo(GeminiDeveloperApiClient.GEMINI_3_5_FLASH_LITE);
    }
}
