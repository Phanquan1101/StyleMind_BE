package com.stylemind.commerce.controller.order;

import com.stylemind.commerce.dto.order.InternalPaymentSnapshot;
import com.stylemind.commerce.service.order.InternalPaymentSnapshotService;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InternalPaymentSnapshotController {
    private final InternalPaymentSnapshotService service;
    public InternalPaymentSnapshotController(InternalPaymentSnapshotService service) { this.service = service; }

    @GetMapping("/internal/v1/orders/{orderId}/payment-snapshot")
    public ResponseEntity<InternalPaymentSnapshot> get(@PathVariable UUID orderId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.get(orderId));
    }
}
