package com.stylemind.commerce.service.order;

import com.stylemind.commerce.dto.order.CancellationRequestResponse;
import com.stylemind.commerce.dto.order.CheckoutPriceResponse;
import com.stylemind.commerce.dto.order.OrderListResponse;
import com.stylemind.commerce.dto.order.OrderResponse;
import com.stylemind.commerce.validation.order.OrderRequestValidator;
import java.util.UUID;

public interface OrderService {

    CheckoutPriceResponse preview(UUID customerId);

    OrderResponse checkout(UUID customerId, String authorizationHeader,
            OrderRequestValidator.CheckoutCommand command);

    OrderListResponse list(UUID customerId, boolean admin, OrderRequestValidator.ListCriteria criteria);

    OrderResponse get(UUID customerId, boolean admin, UUID orderId);

    OrderResponse updateStatus(UUID actorId, UUID orderId, OrderRequestValidator.StatusCommand command);

    CancellationRequestResponse requestCancellation(UUID customerId, UUID orderId, String reason);

    CancellationRequestResponse reviewCancellation(UUID reviewerId, UUID requestId,
            OrderRequestValidator.CancellationReviewCommand command);
}
