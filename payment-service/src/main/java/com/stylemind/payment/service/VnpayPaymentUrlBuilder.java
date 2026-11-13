package com.stylemind.payment.service;

import com.stylemind.payment.config.VnpayPaymentProperties;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class VnpayPaymentUrlBuilder {
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(VIETNAM);
    private final VnpayPaymentProperties properties;

    public VnpayPaymentUrlBuilder(VnpayPaymentProperties properties) { this.properties = properties; }

    public String build(String reference, long amountVnd, Instant createdAt, Instant expiresAt) {
        if (!properties.configured()) throw new com.stylemind.payment.errors.PaymentApiException(HttpStatus.SERVICE_UNAVAILABLE,
                "VNPAY_NOT_CONFIGURED", "VNPAY sandbox credentials and return URL are not configured");
        if (amountVnd < 5_000 || amountVnd > 9_999_999_999L)
            throw new IllegalArgumentException("VNPAY sandbox amount is outside supported limits");
        long amount = Math.multiplyExact(amountVnd, 100L);
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", properties.tmnCode());
        params.put("vnp_Amount", Long.toString(amount));
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", reference);
        params.put("vnp_OrderInfo", "Thanh toan StyleMind " + reference);
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", properties.returnUrl());
        params.put("vnp_IpAddr", "127.0.0.1");
        params.put("vnp_CreateDate", DATE.format(createdAt));
        params.put("vnp_ExpireDate", DATE.format(expiresAt));

        String signedData = query(params);
        params.put("vnp_SecureHash", hmac(signedData));
        return properties.payUrl() + "?" + query(params);
    }

    public boolean verify(Map<String, String> values) {
        String supplied = values.get("vnp_SecureHash");
        if (!StringUtils.hasText(supplied) || properties.hashSecret().isBlank()) return false;
        Map<String, String> signed = new TreeMap<>();
        values.forEach((key, value) -> {
            if (key.startsWith("vnp_") && !key.equals("vnp_SecureHash") && !key.equals("vnp_SecureHashType")) signed.put(key, value);
        });
        byte[] expected = hmac(query(signed)).getBytes(StandardCharsets.US_ASCII);
        byte[] actual = supplied.toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.US_ASCII);
        return java.security.MessageDigest.isEqual(expected, actual);
    }

    private String query(Map<String, String> values) {
        return values.entrySet().stream().map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(java.util.stream.Collectors.joining("&"));
    }

    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }

    private String hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(properties.hashSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException failure) {
            throw new IllegalStateException("Unable to create VNPAY signature");
        }
    }
}
