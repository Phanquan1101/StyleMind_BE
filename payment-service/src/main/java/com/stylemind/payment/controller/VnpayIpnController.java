package com.stylemind.payment.controller;

import com.stylemind.payment.dto.VnpayIpnResponse;
import com.stylemind.payment.service.VnpayIpnService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VnpayIpnController {
    private final VnpayIpnService ipn;
    public VnpayIpnController(VnpayIpnService ipn) { this.ipn = ipn; }

    @GetMapping(path = "/api/v1/payments/vnpay/ipn", produces = "application/json")
    public ResponseEntity<VnpayIpnResponse> receive(@RequestParam MultiValueMap<String, String> query) {
        Map<String, String> values = new LinkedHashMap<>();
        for (var entry : query.entrySet()) {
            if (entry.getValue() == null || entry.getValue().size() != 1)
                return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new VnpayIpnResponse("99", "Invalid request"));
            values.put(entry.getKey(), entry.getValue().getFirst());
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ipn.receive(values, Instant.now()));
    }
}
