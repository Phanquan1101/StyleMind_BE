package com.stylemind.stylist.service.session;

import com.stylemind.stylist.dto.session.CreateStylistSessionRequest;
import com.stylemind.stylist.errors.ErrorCode;
import com.stylemind.stylist.errors.StylistApiException;
import com.stylemind.stylist.security.SessionRequestPolicyFilter;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.stereotype.Component;

/** Parses and validates only the P03-A session-create DTO before it reaches a controller. */
@Component
public class CreateStylistSessionRequestHttpMessageConverter
        extends AbstractHttpMessageConverter<CreateStylistSessionRequest> {

    private final StylistSessionRequestParser parser;

    public CreateStylistSessionRequestHttpMessageConverter(StylistSessionRequestParser parser) {
        super(MediaType.APPLICATION_JSON);
        this.parser = parser;
    }

    @Override
    protected boolean supports(Class<?> clazz) {
        return clazz == CreateStylistSessionRequest.class;
    }

    @Override
    protected CreateStylistSessionRequest readInternal(
            Class<? extends CreateStylistSessionRequest> clazz, HttpInputMessage inputMessage) throws IOException {
        InputStream body = inputMessage.getBody();
        byte[] bytes = body.readNBytes(SessionRequestPolicyFilter.MAX_BODY_BYTES + 1);
        if (bytes.length > SessionRequestPolicyFilter.MAX_BODY_BYTES) {
            throw new StylistApiException(ErrorCode.PAYLOAD_TOO_LARGE);
        }
        return parser.parse(bytes);
    }

    @Override
    protected void writeInternal(CreateStylistSessionRequest request, HttpOutputMessage outputMessage) {
        throw new UnsupportedOperationException("Create session request DTO is input-only");
    }
}
