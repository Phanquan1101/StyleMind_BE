package com.stylemind.commerce.service.returns.impl;

import com.stylemind.commerce.dto.returns.ReturnCommand;
import com.stylemind.commerce.dto.returns.ReturnCommand.Image;
import com.stylemind.commerce.dto.returns.ReturnResponse;
import com.stylemind.commerce.dto.returns.ReturnResponse.AdminPage;
import com.stylemind.commerce.dto.returns.ReturnResponse.Item;
import com.stylemind.commerce.dto.returns.ReturnResponse.OrderReturns;
import com.stylemind.commerce.dto.returns.ReturnResponse.Status;
import com.stylemind.commerce.errors.ApiOperationException;
import com.stylemind.commerce.repository.returns.ReturnRepository;
import com.stylemind.commerce.repository.returns.ReturnRepository.OwnedOrder;
import com.stylemind.commerce.service.returns.ReturnService;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReturnServiceImpl implements ReturnService {

    private static final Duration RETURN_WINDOW = Duration.ofDays(7);

    private final ReturnRepository repository;

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public OrderReturns forOrder(UUID owner, UUID orderId) {
        OwnedOrder order = repository.ownedOrder(orderId, owner, false).orElseThrow(this::notFound);
        List<Item> items = canOpenReturn(order, Instant.now()) && repository.remainingAmount(orderId) > 0
                ? repository.availableItems(orderId) : List.of();
        boolean eligible = items.stream().anyMatch(item -> item.unitPriceVnd() > 0);
        return new OrderReturns(eligible, eligible ? items : List.of(), repository.findForOrder(orderId));
    }

    @Override
    @Transactional
    public ReturnResponse create(UUID owner, UUID orderId, ReturnCommand command, List<Image> images) {
        String fingerprint = fingerprint(command, images);
        // Every creator locks the same order before reading quantities reserved by earlier requests.
        OwnedOrder order = repository.ownedOrder(orderId, owner, true).orElseThrow(this::notFound);
        var previous = repository.existingRequest(orderId, command.requestKey());
        if (previous.isPresent()) {
            if (!fingerprint.equals(previous.get().fingerprint())) {
                throw conflict("RETURN_REQUEST_KEY_CONFLICT", "This return request key is already in use");
            }
            return response(previous.get().id());
        }

        Instant now = Instant.now();
        if (!canOpenReturn(order, now)) {
            throw conflict("RETURN_NOT_ELIGIBLE", "The order is outside its return window or has no settled payment");
        }
        List<Item> selected = selectItems(command.quantities(), repository.availableItems(orderId));
        long amount = calculateAmount(selected);
        if (amount < 1 || amount > repository.remainingAmount(orderId)) {
            throw conflict("RETURN_AMOUNT_UNAVAILABLE", "Selected items exceed the available refund amount");
        }
        UUID id = UUID.randomUUID();
        OffsetDateTime recordedAt = now.atOffset(ZoneOffset.UTC);
        repository.insert(id, orderId, order.paymentEventId(), command.requestKey(), fingerprint, command.reason(),
                amount, recordedAt);
        repository.addItems(id, selected);
        repository.addEvidence(id, images, recordedAt);
        repository.appendHistory(id, Status.REQUESTED, "CREATE", owner, command.reason(), recordedAt);
        return response(id);
    }

    @Override
    @Transactional
    public ReturnResponse addEvidence(UUID owner, UUID id, List<Image> images) {
        requireStatus(id, owner, false, Status.MORE_IMAGES_REQUESTED);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        repository.addEvidence(id, images, now);
        repository.changeStatus(id, Status.REQUESTED, null);
        repository.appendHistory(id, Status.REQUESTED, "ADD_EVIDENCE", owner, null, now);
        return response(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Image evidence(UUID actor, boolean admin, UUID id, UUID imageId) {
        return repository.evidence(id, imageId, actor, admin).orElseThrow(this::notFound);
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AdminPage adminPage(int page, int size, long offset) {
        long total = repository.count();
        long pages = Math.ceilDiv(total, size);
        return new AdminPage(repository.adminPage(size, offset), page, size, total, pages);
    }

    @Override
    @Transactional
    public ReturnResponse decide(UUID admin, UUID id, ReturnCommand.Decision decision) {
        var action = decision.action();
        requireStatus(id, admin, true, action.from());
        repository.changeStatus(id, action.to(), decision.reason());
        repository.appendHistory(id, action.to(), action.name(), admin, decision.reason(),
                OffsetDateTime.now(ZoneOffset.UTC));
        return response(id);
    }

    private void requireStatus(UUID id, UUID actor, boolean admin, Status required) {
        if (repository.lockStatus(id, actor, admin).orElseThrow(this::notFound) != required) {
            throw conflict("RETURN_STATE_CONFLICT", "The return is in a different state");
        }
    }

    private ReturnResponse response(UUID id) {
        return repository.find(id).orElseThrow(this::notFound);
    }

    private boolean canOpenReturn(OwnedOrder order, Instant now) {
        if (!"COMPLETED".equals(order.status()) || order.completedAt() == null || order.paymentEventId() == null) {
            return false;
        }
        Instant completed = order.completedAt().toInstant();
        return !now.isBefore(completed) && !now.isAfter(completed.plus(RETURN_WINDOW));
    }

    private List<Item> selectItems(Map<UUID, Integer> quantities, List<Item> availableItems) {
        Map<UUID, Item> available = new HashMap<>();
        availableItems.forEach(item -> available.put(item.orderItemId(), item));
        List<Item> selected = new ArrayList<>();
        for (var line : quantities.entrySet()) {
            Item item = available.get(line.getKey());
            int quantity = line.getValue();
            if (item == null || quantity > item.quantity()) {
                throw conflict("RETURN_QUANTITY_UNAVAILABLE", "One or more selected item quantities are unavailable");
            }
            selected.add(new Item(item.orderItemId(), item.productName(), item.skuSnapshot(), item.colorSnapshot(),
                    item.sizeSnapshot(), quantity, item.unitPriceVnd()));
        }
        return selected;
    }

    private long calculateAmount(List<Item> items) {
        long amount = 0;
        try {
            for (Item item : items) {
                amount = Math.addExact(amount, Math.multiplyExact(item.unitPriceVnd(), item.quantity()));
            }
        } catch (ArithmeticException exception) {
            throw conflict("RETURN_AMOUNT_UNAVAILABLE", "Selected items exceed the available refund amount");
        }
        return amount;
    }

    private String fingerprint(ReturnCommand command, List<Image> images) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var out = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
                out.writeUTF(command.reason());
                out.writeInt(command.quantities().size());
                for (var item : command.quantities().entrySet()) {
                    out.writeUTF(item.getKey().toString());
                    out.writeInt(item.getValue());
                }
                out.writeInt(images.size());
                for (Image image : images) {
                    out.writeUTF(image.contentType());
                    out.writeInt(image.bytes().length);
                    out.write(image.bytes());
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException | IOException exception) {
            throw new IllegalStateException("Return request fingerprint could not be generated", exception);
        }
    }

    private ApiOperationException notFound() {
        return new ApiOperationException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource was not found");
    }

    private ApiOperationException conflict(String code, String detail) {
        return new ApiOperationException(HttpStatus.CONFLICT, code, detail);
    }
}
