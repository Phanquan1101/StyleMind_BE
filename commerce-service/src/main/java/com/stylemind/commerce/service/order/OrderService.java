package com.stylemind.commerce.service.order;

import com.stylemind.commerce.dto.order.CancellationRequestResponse;
import com.stylemind.commerce.dto.order.CheckoutPriceResponse;
import com.stylemind.commerce.dto.order.OrderListResponse;
import com.stylemind.commerce.dto.order.OrderResponse;
import com.stylemind.commerce.dto.order.CheckoutSelection;
import com.stylemind.commerce.dto.order.QrCheckoutListResponse;
import com.stylemind.commerce.validation.order.OrderRequestValidator;
import java.util.UUID;

public interface OrderService {

    CheckoutPriceResponse previewGuest(String guestSessionKey);
    OrderResponse recoverGuestCheckout(String guestSessionKey, String checkoutKey);
    OrderResponse recoverCheckout(UUID customerId, String checkoutKey);
    CheckoutPriceResponse previewGuestSelection(String guestSessionKey, CheckoutSelection selection);
    OrderResponse checkoutGuestSelection(String guestSessionKey, com.stylemind.commerce.validation.order.GuestCheckoutValidator.CheckoutCommand command, CheckoutSelection selection);
    OrderResponse checkoutGuest(String guestSessionKey,
            com.stylemind.commerce.validation.order.GuestCheckoutValidator.CheckoutCommand command);
    OrderResponse getGuest(String guestSessionKey, UUID orderId);

    CheckoutPriceResponse preview(UUID customerId);
    CheckoutPriceResponse previewSelection(UUID customerId, CheckoutSelection selection);
    OrderResponse checkoutSelection(UUID customerId, String authorizationHeader, OrderRequestValidator.CheckoutCommand command, CheckoutSelection selection);

    UUID prepareQrCheckout(UUID customerId, String authorizationHeader,
            OrderRequestValidator.CheckoutCommand command, CheckoutSelection selection);
    UUID prepareGuestQrCheckout(String guestSessionKey,
            com.stylemind.commerce.validation.order.GuestCheckoutValidator.CheckoutCommand command, CheckoutSelection selection);

    OrderResponse checkout(UUID customerId, String authorizationHeader,
            OrderRequestValidator.CheckoutCommand command);

    OrderListResponse list(UUID customerId, boolean admin, OrderRequestValidator.ListCriteria criteria);

    QrCheckoutListResponse listPendingQrCheckouts(UUID customerId, OrderRequestValidator.ListCriteria criteria);

    OrderResponse get(UUID customerId, boolean admin, UUID orderId);

    OrderResponse updateStatus(UUID actorId, UUID orderId, OrderRequestValidator.StatusCommand command);

    CancellationRequestResponse requestCancellation(UUID customerId, UUID orderId, String reason);

    OrderResponse confirmOrder(UUID actorId, UUID orderId);

    OrderResponse cancelOrder(UUID customerId, UUID orderId, String reason);

    CancellationRequestResponse reviewCancellation(UUID reviewerId, UUID requestId,
            OrderRequestValidator.CancellationReviewCommand command);
}
