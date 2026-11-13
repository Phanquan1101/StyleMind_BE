package com.stylemind.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VnpayIpnResponse(@JsonProperty("RspCode") String responseCode,
        @JsonProperty("Message") String message) { }
