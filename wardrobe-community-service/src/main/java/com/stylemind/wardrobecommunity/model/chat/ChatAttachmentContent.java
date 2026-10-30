package com.stylemind.wardrobecommunity.model.chat;

public record ChatAttachmentContent(byte[] bytes, String contentType) {
    public ChatAttachmentContent { bytes = bytes.clone(); }
    @Override public byte[] bytes() { return bytes.clone(); }
    @Override public String toString() { return "ChatAttachmentContent[bytes=[REDACTED]]"; }
}
