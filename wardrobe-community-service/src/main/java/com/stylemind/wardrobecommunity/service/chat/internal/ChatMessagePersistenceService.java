package com.stylemind.wardrobecommunity.service.chat.internal;

import com.stylemind.wardrobecommunity.dto.chat.ChatAttachmentMetadataResponse;
import com.stylemind.wardrobecommunity.dto.chat.ChatHistoryCriteria;
import com.stylemind.wardrobecommunity.dto.chat.ChatHistoryResponse;
import com.stylemind.wardrobecommunity.dto.chat.ChatMessageResponse;
import com.stylemind.wardrobecommunity.entity.ChatMessage;
import com.stylemind.wardrobecommunity.entity.ChatAttachment;
import com.stylemind.wardrobecommunity.model.chat.ChatImageCommand;
import com.stylemind.wardrobecommunity.model.chat.ChatStoredImage;
import com.stylemind.wardrobecommunity.model.chat.ChatImageMessageSnapshot;
import com.stylemind.wardrobecommunity.storage.chat.ChatImageKeys;
import com.stylemind.wardrobecommunity.errors.ApiOperationException;
import com.stylemind.wardrobecommunity.model.chat.ChatMessageSendResult;
import com.stylemind.wardrobecommunity.model.chat.ChatTextCommand;
import com.stylemind.wardrobecommunity.model.friendship.FriendshipPair;
import com.stylemind.wardrobecommunity.repository.chat.ChatAttachmentRepository;
import com.stylemind.wardrobecommunity.repository.chat.ChatMessageRepository;
import com.stylemind.wardrobecommunity.repository.chat.ChatSessionRepository;
import com.stylemind.wardrobecommunity.repository.friendship.FriendshipRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatMessagePersistenceService {
    private final FriendshipRepository friendships;
    private final ChatSessionRepository sessions;
    private final ChatMessageRepository messages;
    private final ChatAttachmentRepository attachments;
    private final Clock clock;

    public ChatMessagePersistenceService(FriendshipRepository friendships, ChatSessionRepository sessions,
            ChatMessageRepository messages, ChatAttachmentRepository attachments, @Qualifier("chatClock") Clock clock) {
        this.friendships = friendships;
        this.sessions = sessions;
        this.messages = messages;
        this.attachments = attachments;
        this.clock = clock;
    }

    @Transactional
    public ChatMessageSendResult send(UUID actor, UUID sessionId, ChatTextCommand command) {
        return write(actor, sessionId, command.clientMessageId(), command.content(), List.of(), null);
    }

    @Transactional
    public ChatMessageSendResult sendImages(UUID actor, UUID sessionId, ChatImageCommand command, List<ChatStoredImage> uploaded) {
        if (command.images().isEmpty() || command.images().size() > 5 || uploaded.size() != command.images().size()) {
            throw new IllegalArgumentException("Validated image aggregate is required");
        }
        return write(actor, sessionId, command.clientMessageId(), command.content(), List.copyOf(uploaded), command);
    }

    private ChatMessageSendResult write(UUID actor, UUID sessionId, UUID clientKey, String content,
            List<ChatStoredImage> uploaded, ChatImageCommand imageCommand) {
        var visible = sessions.findVisibleForAccount(actor, sessionId).orElseThrow(ChatMessagePersistenceService::notFound);
        var canonical = new FriendshipPair(actor, visible.friendAccountId());
        var friendship = friendships.findLockedByAccountLowIdAndAccountHighId(canonical.accountLowId(), canonical.accountHighId())
                .filter(pair -> pair.getUnfriendedAt() == null).orElseThrow(ChatMessagePersistenceService::notFound);
        var session = sessions.findLockedByChatSessionId(sessionId)
                .filter(row -> row.getFriendshipId().equals(friendship.getFriendshipId())).orElseThrow(ChatMessagePersistenceService::notFound);
        sessions.findVisibleForAccount(actor, sessionId).orElseThrow(ChatMessagePersistenceService::notFound);
        var existing = messages.findByChatSessionIdAndSenderAccountIdAndClientMessageId(sessionId, actor, clientKey);
        if (existing.isPresent()) {
            ChatMessageResponse old = response(existing.get());
            if (imageCommand == null ? !Objects.equals(content, old.content()) || !old.attachments().isEmpty()
                    : !sameImages(imageSnapshot(existing.get()), imageCommand)) { throw conflict(); }
            return new ChatMessageSendResult(old, false);
        }
        if (session.getLastSequenceNumber() == Long.MAX_VALUE) {
            throw new ApiOperationException(HttpStatus.CONFLICT, "CHAT_SEQUENCE_EXHAUSTED", "Conversation sequence cannot advance");
        }
        long sequence = session.getLastSequenceNumber() + 1;
        OffsetDateTime now = OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
        ChatMessage row = new ChatMessage();
        row.setChatMessageId(UUID.randomUUID());
        row.setChatSessionId(sessionId);
        row.setSenderAccountId(actor);
        row.setClientMessageId(clientKey);
        row.setSequenceNumber(sequence);
        row.setContent(content);
        row.setCreatedAt(now);
        messages.saveAndFlush(row);
        List<ChatAttachment> imageRows = new ArrayList<>();
        for (int index = 0; index < uploaded.size(); index++) {
            ChatStoredImage stored = uploaded.get(index);
            ChatAttachment image = new ChatAttachment();
            image.setChatAttachmentId(UUID.randomUUID());
            image.setChatMessageId(row.getChatMessageId());
            image.setDisplayOrder((short) (index + 1));
            image.setStorageObjectKey(stored.key());
            image.setContentType(stored.contentType());
            image.setSizeBytes(stored.sizeBytes());
            image.setCreatedAt(now);
            imageRows.add(image);
        }
        if (!imageRows.isEmpty()) { attachments.saveAllAndFlush(imageRows); }
        session.setLastSequenceNumber(sequence);
        session.setUpdatedAt(now.isBefore(session.getUpdatedAt()) ? session.getUpdatedAt() : now);
        sessions.saveAndFlush(session);
        return new ChatMessageSendResult(response(row), true);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Optional<ChatMessageResponse> imageReplay(UUID actor, UUID sessionId, ChatImageCommand command) {
        sessions.findVisibleForAccount(actor, sessionId).orElseThrow(ChatMessagePersistenceService::notFound);
        return messages.findByChatSessionIdAndSenderAccountIdAndClientMessageId(sessionId, actor, command.clientMessageId())
                .map(row -> {
                    var snapshot = imageSnapshot(row);
                    if (!sameImages(snapshot, command)) { throw conflict(); }
                    return snapshot.message();
                });
    }

    @Transactional(readOnly = true)
    public ChatStoredImage attachment(UUID actor, UUID sessionId, UUID attachmentId) {
        sessions.findVisibleForAccount(actor, sessionId).orElseThrow(ChatMessagePersistenceService::notFound);
        var image = attachments.findById(attachmentId).orElseThrow(ChatMessagePersistenceService::notFound);
        messages.findById(image.getChatMessageId()).filter(row -> row.getChatSessionId().equals(sessionId))
                .orElseThrow(ChatMessagePersistenceService::notFound);
        return new ChatStoredImage(image.getStorageObjectKey(), image.getContentType(), image.getSizeBytes());
    }

    private ChatImageMessageSnapshot imageSnapshot(ChatMessage row) {
        return new ChatImageMessageSnapshot(response(row), attachments.findByChatMessageIdOrderByDisplayOrderAsc(row.getChatMessageId())
                .stream().map(image -> new ChatStoredImage(image.getStorageObjectKey(), image.getContentType(), image.getSizeBytes())).toList());
    }

    private boolean sameImages(ChatImageMessageSnapshot stored, ChatImageCommand command) {
        if (!Objects.equals(stored.message().content(), command.content()) || stored.images().size() != command.images().size()) { return false; }
        for (int index = 0; index < stored.images().size(); index++) {
            var old = stored.images().get(index);
            var image = command.images().get(index);
            if (!image.sha256().equals(ChatImageKeys.sha256(old.key())) || image.sizeBytes() != old.sizeBytes()
                    || !image.contentType().equals(old.contentType())) { return false; }
        }
        return true;
    }

    private static ApiOperationException conflict() {
        return new ApiOperationException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "Client message key was already used with different content");
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ChatHistoryResponse history(UUID actor, UUID sessionId, ChatHistoryCriteria criteria) {
        sessions.findVisibleForAccount(actor, sessionId).orElseThrow(ChatMessagePersistenceService::notFound);
        var page = PageRequest.of(0, criteria.limit() + 1);
        List<ChatMessage> descending = criteria.beforeSequence() == null
                ? messages.findByChatSessionIdOrderBySequenceNumberDesc(sessionId, page)
                : messages.findByChatSessionIdAndSequenceNumberLessThanOrderBySequenceNumberDesc(sessionId, criteria.beforeSequence(), page);
        boolean more = descending.size() > criteria.limit();
        List<ChatMessage> selected = new ArrayList<>(descending.subList(0, Math.min(descending.size(), criteria.limit())));
        Long next = more ? selected.getLast().getSequenceNumber() : null;
        Collections.reverse(selected);
        return new ChatHistoryResponse(selected.stream().map(this::response).toList(), more, next);
    }

    private ChatMessageResponse response(ChatMessage row) {
        var images = attachments.findByChatMessageIdOrderByDisplayOrderAsc(row.getChatMessageId()).stream()
                .map(image -> new ChatAttachmentMetadataResponse(image.getChatAttachmentId(), image.getDisplayOrder(),
                        image.getContentType(), image.getSizeBytes())).toList();
        return new ChatMessageResponse(row.getChatMessageId(), row.getChatSessionId(), row.getSenderAccountId(),
                row.getClientMessageId(), row.getSequenceNumber(), row.getContent(), row.getCreatedAt(), images);
    }

    private static ApiOperationException notFound() {
        return new ApiOperationException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource was not found");
    }
}

