package com.stylemind.payment.service;

import com.stylemind.payment.dto.RefundRequests.Completion;
import com.stylemind.payment.dto.RefundRequests.Destination;
import com.stylemind.payment.dto.RefundRequests.Failure;
import com.stylemind.payment.dto.RefundResponse;
import com.stylemind.payment.dto.RefundSourceType;
import com.stylemind.payment.service.PaymentAuthorizationService.RefundActor;
import java.util.UUID;

public interface RefundService {
    RefundResponse saveReceipt(RefundSourceType type, UUID sourceId, UUID admin, org.springframework.web.multipart.MultipartFile image);
    com.stylemind.payment.dto.RefundReceiptImage receipt(RefundSourceType type, UUID sourceId, RefundActor actor);
    RefundResponse prepare(RefundSourceType type, UUID sourceId, RefundActor actor);
    RefundResponse get(RefundSourceType type, UUID sourceId, RefundActor actor);
    RefundResponse saveDestination(RefundSourceType type, UUID sourceId, RefundActor actor, Destination destination);
    RefundResponse start(RefundSourceType type, UUID sourceId, UUID admin);
    Destination getDestination(RefundSourceType type, UUID sourceId, UUID admin);
    RefundResponse complete(RefundSourceType type, UUID sourceId, UUID admin, Completion evidence);
    RefundResponse fail(RefundSourceType type, UUID sourceId, UUID admin, Failure failure);
}
