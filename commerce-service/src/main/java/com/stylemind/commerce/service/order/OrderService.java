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
import com.stylemind.commerce.dto.cart.CartItem;
import com.stylemind.commerce.dto.cart.CartOwner;
import com.stylemind.commerce.dto.cart.VariantPurchaseInfo;
import com.stylemind.commerce.dto.order.AddressSnapshot;
import com.stylemind.commerce.dto.order.CancellationRequest;
import com.stylemind.commerce.enums.CancellationStatus;
import com.stylemind.commerce.dto.order.OrderDetail;
import com.stylemind.commerce.dto.order.OrderItem;
import com.stylemind.commerce.enums.OrderStatus;
import com.stylemind.commerce.dto.order.OrderStatusHistoryEntry;
import com.stylemind.commerce.dto.order.OrderSummary;
import com.stylemind.commerce.repository.cart.CartRepository;
import com.stylemind.commerce.repository.cart.CartItemRepository;
import com.stylemind.commerce.repository.order.CancellationRepository;
import com.stylemind.commerce.repository.order.OrderAddressRepository;
import com.stylemind.commerce.repository.order.OrderHistoryRepository;
import com.stylemind.commerce.repository.order.OrderItemRepository;
import com.stylemind.commerce.repository.order.OrderRepository;
import com.stylemind.commerce.validation.order.OrderRequestValidator;
import java.time.OffsetDateTime;
import java.util.Collection;
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
    private final CartItemRepository cartItemRepository;
    private final OrderRepository orderRepository;
    private final OrderAddressRepository addressRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderHistoryRepository historyRepository;
    private final CancellationRepository cancellationRepository;
    private final IdentityAddressClient identityAddressClient;

    @Transactional(readOnly = true)
    public CheckoutPriceResponse preview(UUID customerId) {
        UUID cartId = findActiveCartId(CartOwner.customer(customerId)).orElseThrow(this::emptyCart);
        List<CartItem> refreshed = refreshedItems(cartId);
        return new CheckoutPriceResponse(refreshed.stream().map(this::cartItemResponse).toList(),
                refreshed.stream().mapToLong(CartItem::lineTotalVnd).sum());
    }

    @Transactional
    public OrderResponse checkout(UUID customerId, String authorizationHeader,
            OrderRequestValidator.CheckoutCommand command) {
        findByCheckoutKey(customerId, command.checkoutKey()).ifPresent(existing -> {
            throw new ApiOperationException(HttpStatus.CONFLICT, "DUPLICATE_CHECKOUT",
                    "Checkout key was already used for an order");
        });
        UUID cartId = findActiveCartId(CartOwner.customer(customerId)).orElseThrow(this::emptyCart);
        lockCart(cartId);
        List<CartItem> items = refreshedItems(cartId);
        if (items.isEmpty()) {
            throw emptyCart();
        }
        AddressSnapshot address = identityAddressClient.getAddress(command.shippingAddressId(), authorizationHeader);
        long total = items.stream().mapToLong(CartItem::lineTotalVnd).sum();
        UUID orderId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        try {
            insertOrder(orderId, orderNumber(orderId), customerId, command.checkoutKey(),
                    command.paymentMethod(), total, now);
            insertAddress(orderId, address);
            insertItems(orderId, items);
            markCartCheckedOut(cartId, now);
        } catch (DuplicateKeyException exception) {
            throw new ApiOperationException(HttpStatus.CONFLICT, "DUPLICATE_CHECKOUT",
                    "Checkout key was already used for an order");
        }
        return response(detail(findSummary(orderId).orElseThrow()));
    }

    @Transactional(readOnly = true)
    public OrderListResponse list(UUID customerId, boolean admin, OrderRequestValidator.ListCriteria criteria) {
        UUID ownerFilter = admin ? null : customerId;
        long total = count(ownerFilter, criteria.status());
        List<OrderResponse> items = list(ownerFilter, criteria.status(), criteria.size(), criteria.offset())
                .stream().map(summary -> response(detail(summary))).toList();
        long totalPages = total == 0 ? 0 : (total + criteria.size() - 1) / criteria.size();
        return new OrderListResponse(items, criteria.page(), criteria.size(), total, totalPages);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(UUID customerId, boolean admin, UUID orderId) {
        OrderSummary summary = findSummary(orderId).orElseThrow(this::notFound);
        if (!admin && !summary.customerId().equals(customerId)) {
            throw notFound();
        }
        return response(detail(summary));
    }

    @Transactional
    public OrderResponse updateStatus(UUID actorId, UUID orderId, OrderRequestValidator.StatusCommand command) {
        OrderSummary current = findSummary(orderId).orElseThrow(this::notFound);
        if (!ALLOWED_TRANSITIONS.getOrDefault(current.orderStatus(), List.of()).contains(command.orderStatus())) {
            throw new ApiOperationException(HttpStatus.CONFLICT, "INVALID_ORDER_STATUS_TRANSITION",
                    "Order status transition is not allowed");
        }
        if (updateStatus(orderId, current.orderStatus(), command.orderStatus(), actorId,
                command.reason(), OffsetDateTime.now()) != 1) {
            throw new ApiOperationException(HttpStatus.CONFLICT, "ORDER_STATUS_CHANGED",
                    "Order status changed concurrently");
        }
        return response(detail(findSummary(orderId).orElseThrow()));
    }

    @Transactional
    public CancellationRequestResponse requestCancellation(UUID customerId, UUID orderId, String reason) {
        OrderSummary order = findSummary(orderId).orElseThrow(this::notFound);
        if (!order.customerId().equals(customerId)) {
            throw notFound();
        }
        if (!List.of(OrderStatus.PLACED, OrderStatus.PENDING_PAYMENT).contains(order.orderStatus())) {
            throw new ApiOperationException(HttpStatus.CONFLICT, "CANCELLATION_NOT_ALLOWED",
                    "Cancellation is not allowed for this order state");
        }
        try {
            return cancellationResponse(insertCancellation(UUID.randomUUID(), orderId, customerId,
                    reason, OffsetDateTime.now()));
        } catch (DuplicateKeyException exception) {
            throw new ApiOperationException(HttpStatus.CONFLICT, "CANCELLATION_ALREADY_REQUESTED",
                    "A cancellation request already exists for this order");
        }
    }

    @Transactional
    public CancellationRequestResponse reviewCancellation(UUID reviewerId, UUID requestId,
            OrderRequestValidator.CancellationReviewCommand command) {
        CancellationRequest request = cancellationById(requestId).orElseThrow(this::notFound);
        OrderSummary order = findSummary(request.orderId()).orElseThrow(this::notFound);
        if (command.requestStatus() == CancellationStatus.APPROVED) {
            if (!List.of(OrderStatus.PLACED, OrderStatus.PENDING_PAYMENT).contains(order.orderStatus())) {
                throw new ApiOperationException(HttpStatus.CONFLICT, "CANCELLATION_NOT_ALLOWED",
                        "Cancellation is not allowed for this order state");
            }
            updateStatus(order.orderId(), order.orderStatus(), OrderStatus.CANCELLED, reviewerId,
                    "Cancellation approved", OffsetDateTime.now());
        }
        if (reviewCancellation(requestId, command.requestStatus(), reviewerId,
                command.decisionReason(), OffsetDateTime.now()) != 1) {
            throw new ApiOperationException(HttpStatus.CONFLICT, "CANCELLATION_ALREADY_REVIEWED",
                    "Cancellation request was already reviewed");
        }
        return cancellationResponse(cancellationById(requestId).orElseThrow());
    }

    private List<CartItem> refreshedItems(UUID cartId) {
        List<CartItem> items = cartItems(cartId);
        Map<UUID, VariantPurchaseInfo> active = activeVariantInfo(
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

    private java.util.Optional<UUID> findActiveCartId(CartOwner owner) {
        return (owner.customer() ? cartRepository.findByCustomerIdAndCartStatus(owner.customerId(), "ACTIVE")
                : cartRepository.findByGuestSessionKeyAndCartStatus(owner.guestSessionKey(), "ACTIVE"))
                .map(com.stylemind.commerce.entity.Cart::getCartId);
    }

    private List<CartItem> cartItems(UUID cartId) {
        return cartItemRepository.findItemRows(cartId).stream().map(row -> new CartItem(
                (UUID) row[0], (UUID) row[1], (UUID) row[2], (String) row[3], (String) row[4],
                (String) row[5], (String) row[6], (String) row[7], (String) row[8],
                ((Number) row[9]).intValue(), ((Number) row[10]).longValue(), ((Number) row[11]).longValue())).toList();
    }

    private List<VariantPurchaseInfo> activeVariantInfo(Collection<UUID> variantIds) {
        return variantIds.isEmpty() ? List.of() : cartRepository.findActiveVariantRows(variantIds).stream()
                .map(row -> new VariantPurchaseInfo((UUID) row[0], (String) row[1], (String) row[2],
                        (String) row[3], (String) row[4], (String) row[5], (String) row[6],
                        ((Number) row[7]).longValue(), (String) row[8])).toList();
    }

    private void lockCart(UUID cartId) {
        cartRepository.findLockedByCartId(cartId);
    }

    private void insertOrder(UUID orderId, String orderNumber, UUID customerId, String checkoutKey,
            com.stylemind.commerce.enums.PaymentMethod paymentMethod, long total, OffsetDateTime now) {
        OrderStatus status = paymentMethod == com.stylemind.commerce.enums.PaymentMethod.COD
                ? OrderStatus.PLACED : OrderStatus.PENDING_PAYMENT;
        com.stylemind.commerce.entity.Order entity = new com.stylemind.commerce.entity.Order();
        entity.setOrderId(orderId);
        entity.setOrderNumber(orderNumber);
        entity.setCustomerId(customerId);
        entity.setCheckoutKey(checkoutKey);
        entity.setOrderStatus(status.name());
        entity.setPaymentMethod(paymentMethod.name());
        entity.setAcceptedTotalVnd(total);
        entity.setPlacedAt(now);
        try {
            orderRepository.saveAndFlush(entity);
        } catch (org.springframework.dao.DataIntegrityViolationException exception) {
            throw new DuplicateKeyException("Checkout key was already used", exception);
        }
        insertHistory(orderId, null, status.name(), customerId, now, "Order created");
    }

    private void insertAddress(UUID orderId, AddressSnapshot address) {
        com.stylemind.commerce.entity.OrderAddressSnapshot entity = new com.stylemind.commerce.entity.OrderAddressSnapshot();
        entity.setOrderId(orderId);
        entity.setSourceAddressId(address.sourceAddressId());
        entity.setRecipientName(address.recipientName());
        entity.setRecipientPhone(address.recipientPhone());
        entity.setAddressLine(address.addressLine());
        addressRepository.saveAndFlush(entity);
    }

    private void insertItems(UUID orderId, List<CartItem> items) {
        for (CartItem item : items) {
            com.stylemind.commerce.entity.OrderItem entity = new com.stylemind.commerce.entity.OrderItem();
            entity.setOrderItemId(UUID.randomUUID());
            entity.setOrderId(orderId);
            entity.setVariantId(item.variantId());
            entity.setSkuSnapshot(item.sku());
            entity.setProductNameSnapshot(item.productName());
            entity.setBrandNameSnapshot(item.brandName());
            entity.setColorSnapshot(item.color());
            entity.setSizeSnapshot(item.size());
            entity.setMaterialSnapshot(item.material());
            entity.setQuantity(item.quantity());
            entity.setUnitPriceVnd(item.unitPriceVnd());
            orderItemRepository.save(entity);
        }
    }

    private void markCartCheckedOut(UUID cartId, OffsetDateTime now) {
        cartRepository.findById(cartId).ifPresent(cart -> {
            cart.setCartStatus("CHECKED_OUT");
            cart.setUpdatedAt(now);
            cartRepository.saveAndFlush(cart);
        });
    }

    private java.util.Optional<OrderSummary> findByCheckoutKey(UUID customerId, String checkoutKey) {
        return orderRepository.findByCustomerIdAndCheckoutKey(customerId, checkoutKey).map(this::summary);
    }

    private java.util.Optional<OrderSummary> findSummary(UUID orderId) {
        return orderRepository.findById(orderId).map(this::summary);
    }

    private long count(UUID customerId, OrderStatus status) {
        if (customerId == null) {
            return status == null ? orderRepository.count() : orderRepository.countByOrderStatus(status.name());
        }
        return status == null ? orderRepository.countByCustomerId(customerId)
                : orderRepository.countByCustomerIdAndOrderStatus(customerId, status.name());
    }

    private List<OrderSummary> list(UUID customerId, OrderStatus status, int size, long offset) {
        org.springframework.data.domain.PageRequest page = org.springframework.data.domain.PageRequest.of(
                (int) (offset / size), size,
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Order.desc("placedAt"),
                        org.springframework.data.domain.Sort.Order.desc("orderId")));
        List<com.stylemind.commerce.entity.Order> result = customerId == null
                ? (status == null ? orderRepository.findAllByOrderByPlacedAtDescOrderIdDesc(page)
                        : orderRepository.findByOrderStatusOrderByPlacedAtDescOrderIdDesc(status.name(), page))
                : (status == null ? orderRepository.findByCustomerIdOrderByPlacedAtDescOrderIdDesc(customerId, page)
                        : orderRepository.findByCustomerIdAndOrderStatusOrderByPlacedAtDescOrderIdDesc(customerId,
                                status.name(), page));
        return result.stream().map(this::summary).toList();
    }

    private OrderDetail detail(OrderSummary summary) {
        return new OrderDetail(summary, address(summary.orderId()).orElseThrow(), items(summary.orderId()),
                history(summary.orderId()), cancellation(summary.orderId()).orElse(null));
    }

    private java.util.Optional<AddressSnapshot> address(UUID orderId) {
        return addressRepository.findById(orderId).map(entity -> new AddressSnapshot(entity.getSourceAddressId(),
                entity.getRecipientName(), entity.getRecipientPhone(), entity.getAddressLine()));
    }

    private List<com.stylemind.commerce.dto.order.OrderItem> items(UUID orderId) {
        return orderItemRepository.findByOrderIdOrderByOrderItemIdAsc(orderId).stream().map(entity ->
                new com.stylemind.commerce.dto.order.OrderItem(entity.getOrderItemId(), entity.getVariantId(),
                        entity.getSkuSnapshot(), entity.getProductNameSnapshot(), entity.getBrandNameSnapshot(),
                        entity.getColorSnapshot(), entity.getSizeSnapshot(), entity.getMaterialSnapshot(),
                        entity.getImageUrlSnapshot(), entity.getQuantity(), entity.getUnitPriceVnd(),
                        (long) entity.getQuantity() * entity.getUnitPriceVnd())).toList();
    }

    private List<OrderStatusHistoryEntry> history(UUID orderId) {
        return historyRepository.findByOrderIdOrderByChangedAtAscHistoryIdAsc(orderId).stream().map(entity ->
                new OrderStatusHistoryEntry(entity.getHistoryId(), entity.getFromStatus(), entity.getToStatus(),
                        entity.getActorUserId(), entity.getChangedAt(), entity.getReason())).toList();
    }

    private java.util.Optional<CancellationRequest> cancellation(UUID orderId) {
        return cancellationRepository.findByOrderId(orderId).map(this::cancellation);
    }

    private int updateStatus(UUID orderId, OrderStatus from, OrderStatus to, UUID actorId, String reason,
            OffsetDateTime now) {
        java.util.Optional<com.stylemind.commerce.entity.Order> found = orderRepository.findLockedByOrderId(orderId);
        int rows = found.filter(entity -> entity.getOrderStatus().equals(from.name())).map(entity -> {
            entity.setOrderStatus(to.name());
            if (to == OrderStatus.COMPLETED) {
                entity.setCompletedAt(now);
            }
            orderRepository.saveAndFlush(entity);
            return 1;
        }).orElse(0);
        if (rows == 1) {
            insertHistory(orderId, from.name(), to.name(), actorId, now, reason);
        }
        return rows;
    }

    private CancellationRequest insertCancellation(UUID requestId, UUID orderId, UUID requestedBy, String reason,
            OffsetDateTime now) {
        com.stylemind.commerce.entity.CancellationRequest entity = new com.stylemind.commerce.entity.CancellationRequest();
        entity.setCancellationRequestId(requestId);
        entity.setOrderId(orderId);
        entity.setRequestedByUserId(requestedBy);
        entity.setReason(reason);
        entity.setRequestStatus(CancellationStatus.PENDING.name());
        entity.setRequestedAt(now);
        try {
            cancellationRepository.saveAndFlush(entity);
        } catch (org.springframework.dao.DataIntegrityViolationException exception) {
            throw new DuplicateKeyException("Cancellation already exists", exception);
        }
        return cancellation(orderId).orElseThrow();
    }

    private int reviewCancellation(UUID requestId, CancellationStatus status, UUID reviewerId, String decisionReason,
            OffsetDateTime now) {
        return cancellationRepository.findById(requestId)
                .filter(entity -> CancellationStatus.PENDING.name().equals(entity.getRequestStatus()))
                .map(entity -> {
                    entity.setRequestStatus(status.name());
                    entity.setReviewedByUserId(reviewerId);
                    entity.setReviewedAt(now);
                    entity.setDecisionReason(decisionReason);
                    cancellationRepository.saveAndFlush(entity);
                    return 1;
                }).orElse(0);
    }

    private java.util.Optional<CancellationRequest> cancellationById(UUID requestId) {
        return cancellationRepository.findById(requestId).map(this::cancellation);
    }

    private void insertHistory(UUID orderId, String from, String to, UUID actor, OffsetDateTime now, String reason) {
        com.stylemind.commerce.entity.OrderStatusHistory entity = new com.stylemind.commerce.entity.OrderStatusHistory();
        entity.setHistoryId(UUID.randomUUID());
        entity.setOrderId(orderId);
        entity.setFromStatus(from);
        entity.setToStatus(to);
        entity.setActorUserId(actor);
        entity.setChangedAt(now);
        entity.setReason(reason);
        historyRepository.saveAndFlush(entity);
    }

    private OrderSummary summary(com.stylemind.commerce.entity.Order entity) {
        return new OrderSummary(entity.getOrderId(), entity.getOrderNumber(), entity.getCustomerId(),
                entity.getCheckoutKey(), OrderStatus.valueOf(entity.getOrderStatus()),
                com.stylemind.commerce.enums.PaymentMethod.valueOf(entity.getPaymentMethod()),
                entity.getAcceptedTotalVnd(), entity.getPlacedAt(), entity.getCompletedAt());
    }

    private CancellationRequest cancellation(com.stylemind.commerce.entity.CancellationRequest entity) {
        return new CancellationRequest(entity.getCancellationRequestId(), entity.getOrderId(),
                entity.getRequestedByUserId(), entity.getReason(),
                CancellationStatus.valueOf(entity.getRequestStatus()), entity.getRequestedAt(),
                entity.getReviewedByUserId(), entity.getReviewedAt(), entity.getDecisionReason());
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
