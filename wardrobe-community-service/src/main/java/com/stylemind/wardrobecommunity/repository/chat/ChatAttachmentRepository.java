package com.stylemind.wardrobecommunity.repository.chat;

import com.stylemind.wardrobecommunity.entity.ChatAttachment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Metadata lookup only; it grants neither conversation access nor access to a stored file. */
public interface ChatAttachmentRepository extends JpaRepository<ChatAttachment, UUID> {
    List<ChatAttachment> findByChatMessageIdOrderByDisplayOrderAsc(UUID chatMessageId);
}

