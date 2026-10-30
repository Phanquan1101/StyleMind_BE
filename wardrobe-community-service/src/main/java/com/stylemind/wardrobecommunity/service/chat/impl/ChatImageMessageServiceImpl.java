package com.stylemind.wardrobecommunity.service.chat.impl;

import com.stylemind.wardrobecommunity.errors.ApiOperationException;
import com.stylemind.wardrobecommunity.model.chat.ChatAttachmentContent;
import com.stylemind.wardrobecommunity.model.chat.ChatImageCommand;
import com.stylemind.wardrobecommunity.model.chat.ChatMessageSendResult;
import com.stylemind.wardrobecommunity.model.chat.ChatStoredImage;
import com.stylemind.wardrobecommunity.service.chat.ChatImageMessageService;
import com.stylemind.wardrobecommunity.service.chat.internal.ChatMessagePersistenceService;
import com.stylemind.wardrobecommunity.storage.chat.ChatImageKeys;
import com.stylemind.wardrobecommunity.storage.chat.ChatImageStorage;
import com.stylemind.wardrobecommunity.validation.chat.ChatImageRequestValidator;
import com.stylemind.wardrobecommunity.websocket.ChatRealtimePublisher;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatImageMessageServiceImpl implements ChatImageMessageService {
    private static final Logger log = LoggerFactory.getLogger(ChatImageMessageServiceImpl.class);
    private final ChatMessagePersistenceService persistence;
    private final ChatImageStorage storage;
    private final ChatRealtimePublisher realtime;

    @Override public ChatMessageSendResult send(UUID actorId, UUID chatId, ChatImageCommand command) {
        var replay = persistence.imageReplay(actorId, chatId, command);
        if (replay.isPresent()) { return new ChatMessageSendResult(replay.get(), false); }
        storage.requireAvailable();
        UUID attempt = UUID.randomUUID();
        List<ChatStoredImage> uploaded = new ArrayList<>();
        try {
            for (int index = 0; index < command.images().size(); index++) {
                var image = command.images().get(index);
                var pending = new ChatStoredImage(ChatImageKeys.create(attempt, index + 1, image), image.contentType(), image.sizeBytes());
                // Track even an uncertain Put: the DB writer has not yet run and this attempt owns the key.
                uploaded.add(pending);
                storage.put(pending.key(), image);
            }
        } catch (RuntimeException uploadFailure) {
            cleanup(uploaded);
            throw uploadFailure;
        }
        ChatMessageSendResult result;
        try {
            result = persistence.sendImages(actorId, chatId, command, uploaded);
        } catch (ApiOperationException definitiveBusinessRollback) {
            cleanup(uploaded);
            throw definitiveBusinessRollback;
        } catch (RuntimeException outcomeUnknown) {
            // A commit transport failure may have committed. Never delete potentially referenced objects.
            log.warn("Chat image DB outcome needs reconciliation attemptId={}", attempt);
            throw outcomeUnknown;
        }
        if (!result.created()) { cleanup(uploaded); }
        else { realtime.publish(result.message()); }
        return result;
    }

    @Override public ChatAttachmentContent read(UUID actorId, UUID chatId, UUID attachmentId) {
        ChatStoredImage stored = persistence.attachment(actorId, chatId, attachmentId);
        ChatAttachmentContent data = storage.get(stored.key());
        // A network read may outlive unfriend; recheck before returning any bytes.
        persistence.attachment(actorId, chatId, attachmentId);
        String digest = ChatImageKeys.sha256(stored.key());
        if (data.bytes().length != stored.sizeBytes() || !stored.contentType().equalsIgnoreCase(data.contentType())
                || digest == null || !digest.equals(ChatImageRequestValidator.sha256(data.bytes()))) {
            throw new ApiOperationException(HttpStatus.SERVICE_UNAVAILABLE, "CHAT_IMAGE_STORAGE_UNAVAILABLE",
                    "Private chat image storage is temporarily unavailable");
        }
        return data;
    }

    private void cleanup(List<ChatStoredImage> objects) {
        for (var object : objects) {
            boolean removed = false;
            for (int attempt = 0; attempt < 3 && !removed; attempt++) {
                try { storage.delete(object.key()); removed = true; }
                catch (RuntimeException cleanupFailure) { /* Leave private for controlled reconciliation. */ }
            }
            if (!removed) { log.warn("Chat image cleanup needs reconciliation objectKey={}", object.key()); }
        }
    }
}
