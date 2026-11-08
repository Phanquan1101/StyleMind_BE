package com.stylemind.stylist.service.request;

import com.stylemind.stylist.dto.request.CreateStylingRequestRequest;
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
public class CreateStylingRequestHttpMessageConverter
        extends AbstractHttpMessageConverter<CreateStylingRequestRequest> {

    private final StylingRequestParser parser;

    public CreateStylingRequestHttpMessageConverter(StylingRequestParser parser) {
        super(MediaType.APPLICATION_JSON);
        this.parser = parser;
    }

    @Override
    protected boolean supports(Class<?> clazz) {
        return clazz == CreateStylingRequestRequest.class;
    }

    @Override
    protected CreateStylingRequestRequest readInternal(
            Class<? extends CreateStylingRequestRequest> clazz, HttpInputMessage inputMessage) throws IOException {
        byte[] body = inputMessage.getBody().readNBytes(SessionRequestPolicyFilter.MAX_BODY_BYTES + 1);
        if (body.length > SessionRequestPolicyFilter.MAX_BODY_BYTES) {
            throw new StylistApiException(ErrorCode.PAYLOAD_TOO_LARGE);
        }
        return parser.parse(body);
    }

    @Override
    protected void writeInternal(CreateStylingRequestRequest request, HttpOutputMessage outputMessage) {
        throw new UnsupportedOperationException("Create styling request DTO is input-only");
    }
}
