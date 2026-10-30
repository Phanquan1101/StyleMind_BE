package com.stylemind.wardrobecommunity.service.chat;

import com.stylemind.wardrobecommunity.model.chat.ChatAttachmentContent;
import com.stylemind.wardrobecommunity.model.chat.ChatImageCommand;
import com.stylemind.wardrobecommunity.model.chat.ChatMessageSendResult;
import java.util.UUID;

public interface ChatImageMessageService {
    ChatMessageSendResult send(UUID actorId, UUID chatId, ChatImageCommand command);
    ChatAttachmentContent read(UUID actorId, UUID chatId, UUID attachmentId);
}
