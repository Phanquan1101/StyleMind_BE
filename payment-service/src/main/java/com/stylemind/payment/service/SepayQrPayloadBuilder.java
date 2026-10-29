package com.stylemind.payment.service;

import com.stylemind.payment.config.SepayPaymentProperties;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Service;

@Service
public class SepayQrPayloadBuilder {

    private static final String VIETQR_IMAGE_ENDPOINT = "https://vietqr.app/img";

    private final SepayPaymentProperties properties;

    public SepayQrPayloadBuilder(SepayPaymentProperties properties) {
        this.properties = properties;
    }

    public String buildQrImageUrl(long amountVnd, String transferContent) {
        if (properties.qrAccountNumber().isBlank() || properties.qrBankName().isBlank()) {
            throw new IllegalStateException("SePay QR bank account configuration is missing");
        }
        return VIETQR_IMAGE_ENDPOINT
                + "?acc=" + encode(properties.qrAccountNumber())
                + "&bank=" + encode(properties.qrBankName())
                + "&amount=" + amountVnd
                + "&des=" + encode(transferContent);
    }

    public String buildTransferContent(String paymentReference) {
        return paymentReference;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
