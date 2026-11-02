package com.stylemind.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SepayWebhookRequest(
        @JsonProperty("id") Long id,
        @JsonProperty("gateway") String gateway,
        @JsonProperty("transactionDate") String transactionDate,
        @JsonProperty("accountNumber") String accountNumber,
        @JsonProperty("subAccount") String subAccount,
        @JsonProperty("code") String code,
        @JsonProperty("content") String content,
        @JsonProperty("transferType") String transferType,
        @JsonProperty("description") String description,
        @JsonProperty("transferAmount") Long transferAmount,
        @JsonProperty("accumulated") Long accumulated,
        @JsonProperty("referenceCode") String referenceCode) {
}
