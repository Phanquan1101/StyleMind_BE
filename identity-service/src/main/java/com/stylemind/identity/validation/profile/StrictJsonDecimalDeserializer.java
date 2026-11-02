package com.stylemind.identity.validation.profile;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.math.BigDecimal;

/** Reads a JSON number directly as BigDecimal and rejects string/boolean coercion. */
public final class StrictJsonDecimalDeserializer extends JsonDeserializer<BigDecimal> {

    @Override
    public BigDecimal deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonToken token = parser.currentToken();
        if (token != JsonToken.VALUE_NUMBER_INT && token != JsonToken.VALUE_NUMBER_FLOAT) {
            return (BigDecimal) context.handleUnexpectedToken(BigDecimal.class, parser);
        }
        return parser.getDecimalValue();
    }
}
