package com.stylemind.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record PaymentExceptionListResponse(
        @JsonProperty("items") List<PaymentExceptionResponse> items,
        @JsonProperty("limit") int limit,
        @JsonProperty("offset") int offset) {
}
