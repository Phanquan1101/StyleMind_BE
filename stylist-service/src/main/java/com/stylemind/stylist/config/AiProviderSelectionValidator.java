package com.stylemind.stylist.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AiProviderSelectionValidator {
    public AiProviderSelectionValidator(@Value("${stylemind.ai.provider:openai}") String provider) {
        if (!"openai".equals(provider) && !"gemini".equals(provider)) {
            throw new IllegalStateException("stylemind.ai.provider must be explicitly set to openai or gemini");
        }
    }
}
