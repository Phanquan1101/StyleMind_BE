package com.stylemind.wardrobecommunity.storage.chat;

import com.stylemind.wardrobecommunity.model.chat.ChatAttachmentContent;
import com.stylemind.wardrobecommunity.model.chat.ChatImage;

public interface ChatImageStorage {
    void requireAvailable();
    void put(String key, ChatImage image);
    ChatAttachmentContent get(String key);
    void delete(String key);
}
