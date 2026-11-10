package com.stylemind.stylist.service.generation;

import com.stylemind.stylist.dto.generation.GenerateRequest;
import com.stylemind.stylist.errors.ErrorCode;
import com.stylemind.stylist.errors.StylistApiException;
import com.stylemind.stylist.security.SessionRequestPolicyFilter;
import java.io.IOException;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.stereotype.Component;

@Component
public class GenerateRequestHttpMessageConverter extends AbstractHttpMessageConverter<GenerateRequest> {
    private final GenerateRequestParser parser;

    public GenerateRequestHttpMessageConverter(GenerateRequestParser parser) {
        super(MediaType.APPLICATION_JSON);
        this.parser = parser;
    }

    @Override protected boolean supports(Class<?> clazz) { return clazz == GenerateRequest.class; }

    @Override
    protected GenerateRequest readInternal(Class<? extends GenerateRequest> clazz, HttpInputMessage inputMessage)
            throws IOException {
        byte[] body = inputMessage.getBody().readNBytes(SessionRequestPolicyFilter.MAX_BODY_BYTES + 1);
        if (body.length > SessionRequestPolicyFilter.MAX_BODY_BYTES) {
            throw new StylistApiException(ErrorCode.PAYLOAD_TOO_LARGE);
        }
        return parser.parse(body);
    }

    @Override protected void writeInternal(GenerateRequest request, HttpOutputMessage outputMessage) {
        throw new UnsupportedOperationException("Generate request is input-only");
    }
}
