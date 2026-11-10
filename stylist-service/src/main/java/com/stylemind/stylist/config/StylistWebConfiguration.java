package com.stylemind.stylist.config;

import com.stylemind.stylist.service.session.CreateStylistSessionRequestHttpMessageConverter;
import com.stylemind.stylist.service.generation.GenerateRequestHttpMessageConverter;
import com.stylemind.stylist.service.request.CreateStylingRequestHttpMessageConverter;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class StylistWebConfiguration implements WebMvcConfigurer {

    private final CreateStylistSessionRequestHttpMessageConverter sessionRequestConverter;
    private final CreateStylingRequestHttpMessageConverter stylingRequestConverter;
    private final GenerateRequestHttpMessageConverter generateRequestConverter;

    public StylistWebConfiguration(CreateStylistSessionRequestHttpMessageConverter sessionRequestConverter,
                                   CreateStylingRequestHttpMessageConverter stylingRequestConverter,
                                   GenerateRequestHttpMessageConverter generateRequestConverter) {
        this.sessionRequestConverter = sessionRequestConverter;
        this.stylingRequestConverter = stylingRequestConverter;
        this.generateRequestConverter = generateRequestConverter;
    }

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.add(0, generateRequestConverter);
        converters.add(0, stylingRequestConverter);
        converters.add(0, sessionRequestConverter);
    }
}
