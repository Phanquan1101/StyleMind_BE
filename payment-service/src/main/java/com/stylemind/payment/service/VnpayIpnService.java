package com.stylemind.payment.service;

import com.stylemind.payment.dto.VnpayIpnResponse;
import java.time.Instant;
import java.util.Map;

public interface VnpayIpnService {
    VnpayIpnResponse receive(Map<String, String> parameters, Instant receivedAt);
}
