package com.stylemind.commerce.service.order;

import com.stylemind.commerce.dto.cart.CartItemResponse;
import com.stylemind.commerce.dto.order.CancellationRequestResponse;
import com.stylemind.commerce.dto.order.CheckoutPriceResponse;
import com.stylemind.commerce.dto.order.OrderAddressResponse;
import com.stylemind.commerce.dto.order.OrderHistoryResponse;
import com.stylemind.commerce.dto.order.OrderItemResponse;
import com.stylemind.commerce.dto.order.OrderListResponse;
import com.stylemind.commerce.dto.order.OrderResponse;
import com.stylemind.commerce.errors.ApiOperationException;
import com.stylemind.commerce.model.cart.CartItem;
import com.stylemind.commerce.model.cart.CartOwner;
import com.stylemind.commerce.model.cart.VariantPurchaseInfo;
import com.stylemind.commerce.model.order.AddressSnapshot;
import com.stylemind.commerce.model.order.CancellationRequest;
import com.stylemind.commerce.model.order.CancellationStatus;
import com.stylemind.commerce.model.order.OrderDetail;
import com.stylemind.commerce.model.order.OrderItem;
import com.stylemind.commerce.model.order.OrderStatus;
import com.stylemind.commerce.model.order.OrderStatusHistoryEntry;
import com.stylemind.commerce.model.order.OrderSummary;
import com.stylemind.commerce.repository.cart.CartRepository;
import com.stylemind.commerce.repository.order.OrderRepository;
import com.stylemind.commerce.validation.order.OrderRequestValidator;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final Map<OrderStatus, List<OrderStatus>> ALLOWED_TRANSITIONS = transitions();

    private final CartRepository cartRepository;
    private final OrderRepository orderRepository;
    private final IdentityAddressClient identityAddressClient;

    @Transactional(readOnly = true)
    public CheckoutPriceResponse preview(UUID customerId) {
        UUID cartId = cartRepository.findActiveCartId(CartOwner.customer(customerId)).orElseThrow(this::emptyCart);
        List<CartItem> refreshed = refreshedItems(cartId);
        return new CheckoutPriceResponse(refreshed.stream().map(this::cartItemResponse).toList(),
                refreshed.stream().mapToLong(CartItem::lineTotalVnd).sum());
    }

    @Transactional
    public OrderResponse checkout(UUID customerId, String authorizationHeader,
            OrderRequestValidator.CheckoutCommand command) {
        orderRepository.findByCheckoutKey(customerId, command.checkoutKey()).ifPresent(existing -> {
            throw new ApiOperationException(HttpStatus.CONFLICT, "DUPLICATE_CHECKOUT",
                    "Checkout key was already used for an order");
        });
        UUID cartId = cartRepository.findActiveCartId(CartOwner.customer(customerId)).orElseThrow(this::emptyCart);
        orderRepository.lockCart(cartId);
        List<CartItem> items = refreshedItems(cartId);
        if (items.isEmpty()) {
            throw emptyCart();
        }
        AddressSnapshot address = identityAddressClient.getAddress(command.shippingAddressId(), authorizationHeader);
        long total = items.stream().mapToLong(CartItem::lineTotalVnd).sum();
        UUID orderId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        try {
            orderRepository.insertOrder(orderId, orderNumber(orderId), customerId, command.checkoutKey(),
                    command.paymentMethod(), total, now);
            orderRepository.insertAddress(orderId, address);
            orderRepository.insertItems(orderId, items);
            orderRepository.markCartCheckedOut(cartId, now);
        } catch (DuplicateKeyException exception) {
            throw new ApiOperationException(HttpStatus.CONFLICT, "DUPLICATE_CHECKOUT",
                    "Checkout key was already used for an order");
        }
        return response(orderRepository.detail(orderRepository.findSummary(orderId).orElseThrow()));
    }

    @Transactional(readOnly = true)
    public OrderListResponse list(UUID customerId, boolean admin, OrderRequestValidator.ListCriteria criteria) {
        UUID ownerFilter = admin ? null : customerId;
        long total = orderRepository.count(ownerFilter, criteria.status());
        List<OrderResponse> items = orderRepository.list(ownerFilter, criteria.status(), criteria.size(), criteria.offset())
                .stream().map(summary -> response(orderRepository.detail(summary))).toList();
        long totalPages = total == 0 ? 0 : (total + criteria.size() - 1) / criteria.size();
        return new OrderListResponse(items, criteria.page(), criteria.size(), total, totalPages);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(UUID customerId, boolean admin, UUID orderId) {
        OrderSummary summary = orderRepository.findSummary(orderId).orElseThrow(this::notFound);
        if (!admin && !summary.customerId().equals(customerId)) {
            throw notFound();
        }
        return response(orderRepository.detail(summary));
    }

    @Transactional
    public OrderResponse updateStatus(UUID actorId, UUID orderId, OrderRequestValidator.StatusCommand command) {
        OrderSummary current = orderRepository.findSummary(orderId).orElseThrow(this::notFound);
        if (!ALLOWED_TRANSITIONS.getOrDefault(current.orderStatus(), List.of()).contains(command.orderStatus())) {
            throw new ApiOperationException(HttpStatus.CONFLICT, "INVALID_ORDER_STATUS_TRANSITION",
                    "Order status transition is not allowed");
        }
        if (orderRepository.updateStatus(orderId, current.orderStatus(), command.orderStatus(), actorId,
                command.reason(), OffsetDateTime.now()) != 1) {
            throw new ApiOperationException(HttpStatus.CONFLICT, "ORDER_STATUS_CHANGED",
                    "Order status changed concurrently");
        }
        return response(orderRepository.detail(orderRepository.findSummary(orderId).orElseThrow()));
    }

    @Transactional
    public CancellationRequestResponse requestCancellation(UUID customerId, UUID orderId, String reason) {
        OrderSummary order = orderRepository.findSummary(orderId).orElseThrow(this::notFound);
        if (!order.customerId().equals(customerId)) {
            throw notFound();
        }
        if (!List.of(OrderStatus.PLACED, OrderStatus.PENDING_PAYMENT).contains(order.orderStatus())) {
            throw new ApiOperationException(HttpStatus.CONFLICT, "CANCELLATION_NOT_ALLOWED",
                    "Cancellation is not allowed for this order state");
        }
        try {
            return cancellationResponse(orderRepository.insertCancellation(UUID.randomUUID(), orderId, customerId,
                    reason, OffsetDateTime.now()));
        } catch (DuplicateKeyException exception) {
            throw new ApiOperationException(HttpStatus.CONFLICT, "CANCELLATION_ALREADY_REQUESTED",
                    "A cancellation request already exists for this order");
        }
    }

    @Transactional
    public CancellationRequestResponse reviewCancellation(UUID reviewerId, UUID requestId,
            OrderRequestValidator.CancellationReviewCommand command) {
        CancellationRequest request = orderRepository.cancellationById(requestId).orElseThrow(this::notFound);
        OrderSummary order = orderRepository.findSummary(request.orderId()).orElseThrow(this::notFound);
        if (command.requestStatus() == CancellationStatus.APPROVED) {
            if (!List.of(OrderStatus.PLACED, OrderStatus.PENDING_PAYMENT).contains(order.orderStatus())) {
                throw new ApiOperationException(HttpStatus.CONFLICT, "CANCELLATION_NOT_ALLOWED",
                        "Cancellation is not allowed for this order state");
            }
            orderRepository.updateStatus(order.orderId(), order.orderStatus(), OrderStatus.CANCELLED, reviewerId,
                    "Cancellation approved", OffsetDateTime.now());
        }
        if (orderRepository.reviewCancellation(requestId, command.requestStatus(), reviewerId,
                command.decisionReason(), OffsetDateTime.now()) != 1) {
            throw new ApiOperationException(HttpStatus.CONFLICT, "CANCELLATION_ALREADY_REVIEWED",
                    "Cancellation request was already reviewed");
        }
        return cancellationResponse(orderRepository.cancellationById(requestId).orElseThrow());
    }

    private List<CartItem> refreshedItems(UUID cartId) {
        List<CartItem> items = cartRepository.items(cartId);
        Map<UUID, VariantPurchaseInfo> active = cartRepository.activeVariantInfo(
                items.stream().map(CartItem::variantId).toList()).stream()
                .collect(Collectors.toMap(VariantPurchaseInfo::variantId, item -> item));
        if (active.size() != items.size()) {
            throw new ApiOperationException(HttpStatus.CONFLICT, "INVALID_CART_ITEM",
                    "Cart contains an inactive or unavailable variant");
        }
        return items.stream().map(item -> {
            VariantPurchaseInfo v = active.get(item.variantId());
            return new CartItem(item.cartItemId(), item.cartId(), item.variantId(), v.sku(), v.productName(),
                    v.brandName(), v.color(), v.size(), v.material(), item.quantity(), v.unitPriceVnd(),
                    item.quantity() * v.unitPriceVnd());
        }).toList();
    }

    private OrderResponse response(OrderDetail detail) {
        OrderSummary s = detail.summary();
        return new OrderResponse(s.orderId(), s.orderNumber(), s.customerId(), s.checkoutKey(),
                s.orderStatus().name(), s.paymentMethod().name(), s.acceptedTotalVnd(), s.placedAt(),
                s.completedAt(), addressResponse(detail.address()),
                detail.items().stream().map(this::orderItemResponse).toList(),
                detail.history().stream().map(this::historyResponse).toList(),
                detail.cancellationRequest() == null ? null : cancellationResponse(detail.cancellationRequest()));
    }

    private CartItemResponse cartItemResponse(CartItem item) {
        return new CartItemResponse(item.cartItemId(), item.variantId(), item.sku(), item.productName(),
                item.brandName(), item.color(), item.size(), item.material(), item.quantity(),
                item.unitPriceVnd(), item.lineTotalVnd());
    }

    private OrderAddressResponse addressResponse(AddressSnapshot address) {
        return new OrderAddressResponse(address.sourceAddressId(), address.recipientName(), address.recipientPhone(),
                address.addressLine());
    }

    private OrderItemResponse orderItemResponse(OrderItem item) {
        return new OrderItemResponse(item.orderItemId(), item.variantId(), item.skuSnapshot(),
                item.productNameSnapshot(), item.brandNameSnapshot(), item.colorSnapshot(), item.sizeSnapshot(),
                item.materialSnapshot(), item.imageUrlSnapshot(), item.quantity(), item.unitPriceVnd(),
                item.lineTotalVnd());
    }

    private OrderHistoryResponse historyResponse(OrderStatusHistoryEntry history) {
        return new OrderHistoryResponse(history.historyId(), history.fromStatus(), history.toStatus(),
                history.actorUserId(), history.changedAt(), history.reason());
    }

    private CancellationRequestResponse cancellationResponse(CancellationRequest request) {
        return new CancellationRequestResponse(request.cancellationRequestId(), request.orderId(),
                request.requestedByUserId(), request.reason(), request.requestStatus().name(), request.requestedAt(),
                request.reviewedByUserId(), request.reviewedAt(), request.decisionReason());
    }

    private String orderNumber(UUID orderId) {
        return "SM-" + orderId.toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT);
    }

    private ApiOperationException notFound() {
        return new ApiOperationException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource was not found");
    }

    private ApiOperationException emptyCart() {
        return new ApiOperationException(HttpStatus.CONFLICT, "EMPTY_CART", "Cart has no checkout items");
    }

    private static Map<OrderStatus, List<OrderStatus>> transitions() {
        Map<OrderStatus, List<OrderStatus>> map = new EnumMap<>(OrderStatus.class);
        map.put(OrderStatus.PENDING_PAYMENT, List.of(OrderStatus.PAID, OrderStatus.CANCELLED));
        map.put(OrderStatus.PLACED, List.of(OrderStatus.PROCESSING, OrderStatus.CANCELLED));
        map.put(OrderStatus.PAID, List.of(OrderStatus.PROCESSING));
        map.put(OrderStatus.PROCESSING, List.of(OrderStatus.SHIPPED));
        map.put(OrderStatus.SHIPPED, List.of(OrderStatus.COMPLETED));
        map.put(OrderStatus.COMPLETED, List.of());
        map.put(OrderStatus.CANCELLED, List.of());
        return map;
    }
}
